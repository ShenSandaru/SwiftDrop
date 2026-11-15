"use client";

import { useEffect, useMemo, useRef, useState, type MouseEvent } from "react";
import {
  Compass,
  LocateFixed,
  MapPin,
  Minus,
  Plus,
  RotateCcw,
  Star,
  Warehouse,
} from "lucide-react";
import { cn } from "cn";
import { Button } from "@/components/ui/button";
import {
  DISTRICT_COORDINATES,
  resolveDistrictCoordinates,
  type District,
} from "@/lib/districts";
import type { Hub } from "@/lib/hubs";
import type { ZoneAdmin } from "@/lib/pricing";

/**
 * Sri Lanka coastline coordinates (longitude, latitude), clockwise from Jaffna.
 * Matches the projection in components/landing/island-map.tsx (300 x 440 viewBox).
 */
const COAST: [number, number][] = [
  [79.9, 9.72], [79.95, 9.8], [80.05, 9.82], [80.23, 9.83], [80.45, 9.62], [80.82, 9.27], [81.0, 8.98],
  [81.23, 8.57], [81.43, 8.25], [81.7, 7.72], [81.82, 7.41], [81.84, 6.88], [81.72, 6.52], [81.45, 6.3],
  [81.12, 6.12], [80.8, 6.02], [80.59, 5.92], [80.22, 6.03], [79.99, 6.42], [79.86, 6.93], [79.84, 7.21],
  [79.79, 7.58], [79.8, 7.95], [79.74, 8.25], [79.88, 8.5], [79.93, 8.8], [79.9, 9.0], [79.88, 9.06],
  [80.0, 9.07], [80.2, 9.47], [80.28, 9.56], [80.14, 9.63], [79.98, 9.68],
];

const VIEWBOX_W = 300;
const VIEWBOX_H = 440;
const MIN_ZOOM = 1;
const MAX_ZOOM = 4.5;

const CITY_NAME_ALIASES: Record<string, string> = {
  kagalle: "KEG",
  kegalle: "KEG",
  colombo: "CMB",
  gampaha: "GAM",
  kalutara: "KAL",
  kandy: "KDY",
  matale: "MTL",
  "nuwara eliya": "NUE",
  galle: "GAL",
  matara: "MAT",
  hambantota: "HAM",
  jaffna: "JAF",
  kilinochchi: "KIL",
  mannar: "MAN",
  vavuniya: "VAV",
  mullaitivu: "MUL",
  batticaloa: "BAT",
  ampara: "AMP",
  trincomalee: "TRI",
  kurunegala: "KUR",
  puttalam: "PUT",
  anuradhapura: "ANU",
  polonnaruwa: "POL",
  badulla: "BAD",
  monaragala: "MON",
  ratnapura: "RAT",
};

const project = ([lon, lat]: [number, number]): [number, number] => [
  38 + (lon - 79.6) * 100,
  20 + (9.9 - lat) * 100,
];

function smoothClosedPath(points: [number, number][]) {
  const at = (index: number) => points[(index + points.length) % points.length];
  const round = (value: number) => Math.round(value * 10) / 10;
  let d = `M${round(points[0][0])} ${round(points[0][1])}`;
  points.forEach((_, index) => {
    const [p0, p1, p2, p3] = [at(index - 1), at(index), at(index + 1), at(index + 2)];
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    d += `C${round(c1[0])} ${round(c1[1])} ${round(c2[0])} ${round(c2[1])} ${round(p2[0])} ${round(p2[1])}`;
  });
  return `${d}Z`;
}

const ISLAND_PATH = smoothClosedPath(COAST.map(project));

const HIGHLAND_PATHS = [54, 36, 20].map((radius, ring) =>
  smoothClosedPath(
    Array.from({ length: 9 }, (_, index): [number, number] => {
      const angle = (index / 9) * 2 * Math.PI;
      const seed = 3 - ring;
      const r =
        radius *
        (1 + 0.18 * Math.sin(3 * angle + seed) + 0.08 * Math.cos(5 * angle + seed * 2));
      return [160 + r * Math.cos(angle) * 0.8, 318 + r * Math.sin(angle)];
    })
  )
);

function resolveHubLatLng(
  hub: Hub,
  districts: District[],
  zones: ZoneAdmin[]
): { lat: number; lng: number } {
  const cityNorm = hub.city.trim().toLowerCase();

  // 1. Exact district name match (prevents "Kegalle" matching "Galle")
  const exactDistrict = districts.find((d) => d.name.toLowerCase() === cityNorm);
  if (exactDistrict) {
    if (exactDistrict.latitude != null && exactDistrict.longitude != null) {
      return { lat: exactDistrict.latitude, lng: exactDistrict.longitude };
    }
    if (DISTRICT_COORDINATES[exactDistrict.code]) {
      return DISTRICT_COORDINATES[exactDistrict.code];
    }
  }

  // 2. Direct city / spelling alias lookup (handles e.g. "Kagalle" -> KEG)
  if (CITY_NAME_ALIASES[cityNorm]) {
    return DISTRICT_COORDINATES[CITY_NAME_ALIASES[cityNorm]];
  }

  // 3. Hub code prefix (e.g. "KEG-MAIN" -> "KEG")
  const byCode = resolveDistrictCoordinates(hub.code);
  if (byCode) return byCode;

  // 4. Whole-word district name match inside hub.city
  const wordDistrict = districts.find((d) => {
    const escaped = d.name.toLowerCase().replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    return new RegExp(`\\b${escaped}\\b`).test(cityNorm);
  });
  if (wordDistrict) {
    if (wordDistrict.latitude != null && wordDistrict.longitude != null) {
      return { lat: wordDistrict.latitude, lng: wordDistrict.longitude };
    }
    if (DISTRICT_COORDINATES[wordDistrict.code]) {
      return DISTRICT_COORDINATES[wordDistrict.code];
    }
  }

  // 5. Direct city/code helper
  const byCity = resolveDistrictCoordinates(hub.city);
  if (byCity) return byCity;

  // 6. Primary serving zone
  if (hub.zoneId) {
    const primaryZone = zones.find((z) => z.id === hub.zoneId);
    if (primaryZone) {
      const byZone =
        resolveDistrictCoordinates(primaryZone.code) ??
        resolveDistrictCoordinates(primaryZone.name);
      if (byZone) return byZone;
    }
  }

  return DISTRICT_COORDINATES.CMB;
}

function buildCurvedRoute(x1: number, y1: number, x2: number, y2: number): string {
  const mx = (x1 + x2) / 2;
  const my = (y1 + y2) / 2;
  const dx = x2 - x1;
  const dy = y2 - y1;
  const len = Math.hypot(dx, dy) || 1;
  const bend = Math.min(22, len * 0.16);
  const cx = mx - (dy / len) * bend;
  const cy = my + (dx / len) * bend;
  return `M${x1.toFixed(1)} ${y1.toFixed(1)} Q${cx.toFixed(1)} ${cy.toFixed(1)} ${x2.toFixed(1)} ${y2.toFixed(1)}`;
}

interface PlottedHub {
  hub: Hub;
  x: number;
  y: number;
  assignedZones: ZoneAdmin[];
}

export interface IslandCoverageMapProps {
  hubs: Hub[];
  zones: ZoneAdmin[];
  districts: District[];
  coveragePercent: number;
  loading?: boolean;
}

export function IslandCoverageMap({
  hubs,
  zones,
  districts,
  coveragePercent,
  loading = false,
}: IslandCoverageMapProps) {
  const mapViewportRef = useRef<HTMLDivElement | null>(null);
  const svgRef = useRef<SVGSVGElement | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);
  const dragOrigin = useRef<{ x: number; y: number; panX: number; panY: number } | null>(null);
  const [selectedHubId, setSelectedHubId] = useState<string | null>(null);
  const [hoveredHubId, setHoveredHubId] = useState<string | null>(null);

  const activeHubsCount = useMemo(() => hubs.filter((h) => h.active).length, [hubs]);

  const plottedHubs: PlottedHub[] = useMemo(() => {
    const coordCount = new Map<string, number>();

    return hubs.map((hub) => {
      const { lat, lng } = resolveHubLatLng(hub, districts, zones);
      const [baseX, baseY] = project([lng, lat]);
      const key = `${Math.round(baseX)}_${Math.round(baseY)}`;
      const seen = coordCount.get(key) ?? 0;
      coordCount.set(key, seen + 1);

      const angle = seen * ((2 * Math.PI) / 5);
      const offsetR = seen === 0 ? 0 : 12;
      const x = Math.max(24, Math.min(VIEWBOX_W - 24, baseX + Math.cos(angle) * offsetR));
      const y = Math.max(24, Math.min(VIEWBOX_H - 24, baseY + Math.sin(angle) * offsetR));

      const assignedZones = zones.filter((z) => z.hubId === hub.id || z.id === hub.zoneId);

      return { hub, x, y, assignedZones };
    });
  }, [hubs, districts, zones]);

  const centralPlotted = useMemo(
    () =>
      plottedHubs.find((p) => p.hub.central && p.hub.active) ??
      plottedHubs.find((p) => p.hub.central) ??
      plottedHubs.find((p) => p.hub.active) ??
      plottedHubs[0] ??
      null,
    [plottedHubs]
  );

  const totalAssignedZones = useMemo(
    () => plottedHubs.reduce((sum, item) => sum + item.assignedZones.length, 0),
    [plottedHubs]
  );

  // Space-aware progressive detail thresholds:
  // - When zoomed out, hide extra hub names & zone nodes if space is tight so the map stays clean.
  // - When zoomed in (or sparse), reveal hub names and delivery zones; hide again on zoom out.
  const isSparseNetwork = plottedHubs.length <= 3 && totalAssignedZones <= 6;
  const labelZoomThreshold = isSparseNetwork ? 1 : 1.35;
  const zoneZoomThreshold = isSparseNetwork ? 1 : 2.0;
  const showAllHubLabels = zoom >= labelZoomThreshold;
  const showZones = zoom >= zoneZoomThreshold;

  // Approximate pixels per SVG unit (520px tall viewport / 440 viewBox height)
  const pxPerSvgUnit = (520 / VIEWBOX_H) * zoom;

  const activeSelection = useMemo(() => {
    if (selectedHubId) {
      const match = plottedHubs.find((p) => p.hub.id === selectedHubId);
      if (match) return match;
    }
    return centralPlotted;
  }, [selectedHubId, plottedHubs, centralPlotted]);

  const viewW = VIEWBOX_W / zoom;
  const viewH = VIEWBOX_H / zoom;
  const maxPanX = (VIEWBOX_W - viewW) / 2;
  const maxPanY = (VIEWBOX_H - viewH) / 2;
  const clampedPanX = Math.max(-maxPanX, Math.min(maxPanX, pan.x));
  const clampedPanY = Math.max(-maxPanY, Math.min(maxPanY, pan.y));
  const minX = (VIEWBOX_W - viewW) / 2 + clampedPanX;
  const minY = (VIEWBOX_H - viewH) / 2 + clampedPanY;

  const invScale = 1 / Math.pow(zoom, 0.65);

  function updateZoom(nextZoom: number, centerPan?: { x: number; y: number }) {
    const clamped = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Number(nextZoom.toFixed(2))));
    setZoom(clamped);
    if (clamped === 1) {
      setPan({ x: 0, y: 0 });
    } else if (centerPan) {
      setPan(centerPan);
    }
  }

  // Non-passive native wheel listener so scrolling inside the map zooms ONLY the map
  // and never scrolls the browser page.
  useEffect(() => {
    const container = mapViewportRef.current;
    if (!container) return;

    function onNativeWheel(event: WheelEvent) {
      event.preventDefault();
      event.stopPropagation();
      const delta = event.deltaY < 0 ? 0.25 : -0.25;
      setZoom((prev) => {
        const next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Number((prev + delta).toFixed(2))));
        if (next === 1) {
          setPan({ x: 0, y: 0 });
        }
        return next;
      });
    }

    container.addEventListener("wheel", onNativeWheel, { passive: false });
    return () => container.removeEventListener("wheel", onNativeWheel);
  }, []);

  function focusOnHub(item: PlottedHub) {
    setSelectedHubId(item.hub.id);
    const targetZoom = Math.max(zoom, 2.0);
    const targetPanX = item.x - VIEWBOX_W / 2;
    const targetPanY = item.y - VIEWBOX_H / 2;
    updateZoom(targetZoom, { x: targetPanX, y: targetPanY });
  }

  function resetView() {
    setZoom(1);
    setPan({ x: 0, y: 0 });
  }

  function handleMouseDown(event: MouseEvent<SVGSVGElement>) {
    if (zoom <= 1) return;
    setDragging(true);
    dragOrigin.current = {
      x: event.clientX,
      y: event.clientY,
      panX: clampedPanX,
      panY: clampedPanY,
    };
  }

  function handleMouseMove(event: MouseEvent<SVGSVGElement>) {
    if (!dragging || !dragOrigin.current || !svgRef.current) return;
    const rect = svgRef.current.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    const scaleX = viewW / rect.width;
    const scaleY = viewH / rect.height;
    const dx = (event.clientX - dragOrigin.current.x) * scaleX;
    const dy = (event.clientY - dragOrigin.current.y) * scaleY;
    setPan({
      x: dragOrigin.current.panX - dx,
      y: dragOrigin.current.panY - dy,
    });
  }

  function handleMouseUp() {
    setDragging(false);
    dragOrigin.current = null;
  }

  const highlightedId = hoveredHubId ?? activeSelection?.hub.id ?? null;

  return (
    <div className="grid gap-6 lg:grid-cols-12">
      {/* Left: Interactive Sri Lanka Map Card */}
      <div className="lg:col-span-8 rounded-2xl border border-border bg-card shadow-2xs overflow-hidden flex flex-col">
        {/* Static Top Toolbar — fixed layout so zoom buttons never shift */}
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/80 bg-muted/30 px-4 py-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <span className="inline-flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary border border-primary/20">
              <Compass className="size-4" />
            </span>
            <div className="min-w-0">
              <h2 className="text-sm font-bold text-foreground truncate">
                Sri Lanka Hub &amp; Zone Network Map
              </h2>
              <p className="text-[11px] text-muted-foreground truncate">
                {activeHubsCount}/{plottedHubs.length} active hubs ·{" "}
                {zones.length} delivery {zones.length === 1 ? "zone" : "zones"} ·{" "}
                {coveragePercent}% island coverage
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            {/* Fixed-width status pill so zoom controls stay completely static */}
            <span
              className={cn(
                "inline-flex w-44 items-center justify-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-medium transition-colors",
                showZones
                  ? "border-primary/30 bg-primary/10 text-primary"
                  : "border-border bg-background text-muted-foreground"
              )}
            >
              <MapPin className="size-3 shrink-0" />
              <span className="truncate">
                {showZones
                  ? `Zones visible (${totalAssignedZones})`
                  : `Zoom ${zoneZoomThreshold.toFixed(1)}× for zones`}
              </span>
            </span>

            {/* Zoom Controls */}
            <div className="inline-flex items-center rounded-lg border border-border bg-background p-0.5 shadow-2xs shrink-0">
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                onClick={() => updateZoom(zoom - 0.35)}
                disabled={zoom <= MIN_ZOOM}
                title="Zoom out"
                aria-label="Zoom out"
              >
                <Minus className="size-3.5" />
              </Button>
              <span className="w-11 text-center font-mono text-[11px] font-semibold text-foreground">
                {zoom.toFixed(1)}×
              </span>
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                onClick={() => updateZoom(zoom + 0.35)}
                disabled={zoom >= MAX_ZOOM}
                title="Zoom in"
                aria-label="Zoom in"
              >
                <Plus className="size-3.5" />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                onClick={resetView}
                disabled={zoom === 1 && clampedPanX === 0 && clampedPanY === 0}
                title="Reset map view"
                aria-label="Reset map view"
              >
                <RotateCcw className="size-3" />
              </Button>
            </div>
          </div>
        </div>

        {/* SVG Map Canvas (wheel-isolated so page never scrolls while inside) */}
        <div
          ref={mapViewportRef}
          className="relative flex-1 min-h-[470px] overflow-hidden bg-gradient-to-b from-accent/30 via-background to-accent/20 flex items-center justify-center p-4 pb-12 select-none overscroll-contain"
        >
          {loading ? (
            <div className="text-xs text-muted-foreground">Loading network map…</div>
          ) : (
            <svg
              ref={svgRef}
              viewBox={`${minX.toFixed(2)} ${minY.toFixed(2)} ${viewW.toFixed(2)} ${viewH.toFixed(2)}`}
              onMouseDown={handleMouseDown}
              onMouseMove={handleMouseMove}
              onMouseUp={handleMouseUp}
              onMouseLeave={handleMouseUp}
              role="img"
              aria-label="Interactive Sri Lanka delivery hub and zone coverage map"
              className={cn(
                "w-full max-h-[520px] h-full transition-all duration-150",
                zoom > 1 ? (dragging ? "cursor-grabbing" : "cursor-grab") : "cursor-default"
              )}
            >
              <defs>
                <style>{`
                  @keyframes swiftdrop-route-flow {
                    to {
                      stroke-dashoffset: -20;
                    }
                  }
                  @keyframes swiftdrop-hub-pulse {
                    0%, 100% {
                      transform: scale(1);
                      opacity: 0.45;
                    }
                    50% {
                      transform: scale(1.65);
                      opacity: 0;
                    }
                  }
                  .sd-route-dash {
                    animation: swiftdrop-route-flow 2.4s linear infinite;
                  }
                  .sd-central-pulse {
                    transform-box: fill-box;
                    transform-origin: center;
                    animation: swiftdrop-hub-pulse 2.4s ease-out infinite;
                  }
                  @media (prefers-reduced-motion: reduce) {
                    .sd-route-dash,
                    .sd-central-pulse {
                      animation: none !important;
                    }
                  }
                `}</style>
                <radialGradient id="sd-island-fill" cx="46%" cy="55%" r="65%">
                  <stop offset="0%" stopColor="var(--card)" />
                  <stop offset="100%" stopColor="var(--muted)" />
                </radialGradient>
                <radialGradient id="sd-highland-grad-0" cx="50%" cy="50%" r="55%">
                  <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.10" />
                  <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.03" />
                </radialGradient>
                <radialGradient id="sd-highland-grad-1" cx="50%" cy="50%" r="55%">
                  <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.14" />
                  <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.05" />
                </radialGradient>
                <radialGradient id="sd-highland-grad-2" cx="50%" cy="50%" r="55%">
                  <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.20" />
                  <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.08" />
                </radialGradient>
                <filter id="sd-soft-shadow" x="-15%" y="-15%" width="130%" height="130%">
                  <feDropShadow dx="0" dy="3" stdDeviation="4" floodColor="#0f766e" floodOpacity="0.12" />
                </filter>
              </defs>

              {/* Subtle coordinate grid */}
              <g opacity="0.32" stroke="var(--border)" strokeWidth={0.4 * invScale} strokeDasharray="2 3">
                {[75, 150, 225].map((gx) => (
                  <line key={`gx-${gx}`} x1={gx} y1={10} x2={gx} y2={430} />
                ))}
                {[90, 180, 270, 360].map((gy) => (
                  <line key={`gy-${gy}`} x1={15} y1={gy} x2={285} y2={gy} />
                ))}
              </g>

              {/* Layered Sea / Coastal Bathymetry Depth Around Sri Lanka */}
              <path
                d={ISLAND_PATH}
                fill="none"
                stroke="var(--primary)"
                strokeOpacity={0.045}
                strokeWidth={28 * invScale}
                strokeLinejoin="round"
              />
              <path
                d={ISLAND_PATH}
                fill="none"
                stroke="var(--primary)"
                strokeOpacity={0.075}
                strokeWidth={16 * invScale}
                strokeLinejoin="round"
              />
              <path
                d={ISLAND_PATH}
                fill="none"
                stroke="var(--primary)"
                strokeOpacity={0.12}
                strokeWidth={7.5 * invScale}
                strokeLinejoin="round"
              />
              <path
                d={ISLAND_PATH}
                fill="none"
                stroke="var(--primary)"
                strokeOpacity={0.17}
                strokeWidth={3 * invScale}
                strokeLinejoin="round"
              />

              {/* Sri Lanka Island Silhouette */}
              <path
                d={ISLAND_PATH}
                fill="url(#sd-island-fill)"
                stroke="var(--primary)"
                strokeOpacity={0.38}
                strokeWidth={1.3 * invScale}
                filter="url(#sd-soft-shadow)"
              />

              {/* Central Highlands Topographic Contours with Layered Depth Gradients */}
              <g>
                {HIGHLAND_PATHS.map((d, i) => (
                  <path
                    key={`hl-${i}`}
                    d={d}
                    fill={`url(#sd-highland-grad-${i})`}
                    stroke="var(--primary)"
                    strokeOpacity={0.16 + i * 0.05}
                    strokeWidth={0.75 * invScale}
                  />
                ))}
              </g>

              {/* Animated Dashed Hub-to-Hub Routes (Main Hub -> Branch Hubs; amber & static when inactive) */}
              {centralPlotted && (
                <g fill="none">
                  {plottedHubs
                    .filter((item) => item.hub.id !== centralPlotted.hub.id)
                    .map((item) => {
                      const d = buildCurvedRoute(
                        centralPlotted.x,
                        centralPlotted.y,
                        item.x,
                        item.y
                      );
                      const isRouteActive = centralPlotted.hub.active && item.hub.active;
                      const routeColor = isRouteActive ? "var(--primary)" : "#d97706";
                      const isHighlighted =
                        highlightedId === item.hub.id ||
                        highlightedId === centralPlotted.hub.id;
                      return (
                        <g key={`route-${item.hub.id}`}>
                          <path
                            d={d}
                            stroke={routeColor}
                            strokeOpacity={isHighlighted ? 0.08 : 0.035}
                            strokeWidth={(isHighlighted ? 1.2 : 0.8) * invScale}
                            strokeLinecap="round"
                          />
                          <path
                            d={d}
                            className={isRouteActive ? "sd-route-dash" : undefined}
                            stroke={routeColor}
                            strokeOpacity={
                              isRouteActive
                                ? isHighlighted
                                  ? 0.38
                                  : 0.18
                                : isHighlighted
                                  ? 0.32
                                  : 0.16
                            }
                            strokeWidth={(isHighlighted ? 1.1 : 0.75) * invScale}
                            strokeDasharray={`${3 * invScale} ${7 * invScale}`}
                            strokeLinecap="round"
                          />
                        </g>
                      );
                    })}
                </g>
              )}

              {/* Related Delivery Zones (Only shown when zoomed in & hub has enough clearance) */}
              {plottedHubs.map((item) => {
                const count = item.assignedZones.length;
                if (count === 0) return null;

                // Check screen-space distance to nearest other hub so nearby hubs don't collide
                const nearestSvgDist = plottedHubs.reduce((minD, other) => {
                  if (other.hub.id === item.hub.id) return minD;
                  return Math.min(minD, Math.hypot(other.x - item.x, other.y - item.y));
                }, Infinity);
                const nearestPxDist = nearestSvgDist * pxPerSvgUnit;
                const isFocusedHub =
                  activeSelection?.hub.id === item.hub.id || hoveredHubId === item.hub.id;
                const hasSpaceForZones =
                  showZones && (isSparseNetwork || isFocusedHub || nearestPxDist >= 120);

                // Keep enough orbit radius so zone nodes clear the hub label below the pin
                const ringRadius = Math.max(22, 18 + count * 1.4) * invScale;

                return (
                  <g
                    key={`zones-${item.hub.id}`}
                    style={{
                      opacity: hasSpaceForZones ? 1 : 0,
                      pointerEvents: hasSpaceForZones ? "auto" : "none",
                      transition: "opacity 220ms ease",
                    }}
                  >
                    {item.assignedZones.map((zone, idx) => {
                      // Start at top-right/top and distribute evenly; nudge away from straight-down (hub label)
                      const rawAngle = (idx / count) * 2 * Math.PI - Math.PI / 2;
                      const zx = item.x + Math.cos(rawAngle) * ringRadius;
                      const zy = item.y + Math.sin(rawAngle) * ringRadius;

                      const cosA = Math.cos(rawAngle);
                      const sinA = Math.sin(rawAngle);
                      const labelOffset = 5.5 * invScale;
                      const lx = zx + cosA * labelOffset;
                      const ly = zy + sinA * labelOffset + 1.8 * invScale;
                      const anchor =
                        Math.abs(cosA) < 0.28 ? "middle" : cosA > 0 ? "start" : "end";

                      const isZoneActive = item.hub.active && zone.active;
                      const zoneColor = isZoneActive ? "var(--primary)" : "#d97706";

                      return (
                        <g key={`zone-node-${zone.id}`}>
                          <line
                            x1={item.x}
                            y1={item.y}
                            x2={zx}
                            y2={zy}
                            stroke={zoneColor}
                            strokeOpacity={isZoneActive ? 0.45 : 0.55}
                            strokeWidth={0.85 * invScale}
                            strokeDasharray={`${2 * invScale} ${2 * invScale}`}
                          />
                          <circle
                            cx={zx}
                            cy={zy}
                            r={2.5 * invScale}
                            fill="var(--background)"
                            stroke={zoneColor}
                            strokeWidth={1.15 * invScale}
                          />
                          <text
                            x={lx}
                            y={ly}
                            textAnchor={anchor}
                            fontSize={4.8 * invScale}
                            fontWeight={600}
                            fill={isZoneActive ? "var(--foreground)" : "#b45309"}
                            paintOrder="stroke"
                            stroke="var(--background)"
                            strokeWidth={2 * invScale}
                            strokeLinejoin="round"
                          >
                            {zone.code}
                          </text>
                        </g>
                      );
                    })}
                  </g>
                );
              })}

              {/* Hub Markers (Active in teal; Inactive in amber) */}
              {plottedHubs.map((item) => {
                const isCentral = item.hub.central;
                const isActive = item.hub.active;
                const hubColor = isActive ? "var(--primary)" : "#d97706";
                const isSelected = activeSelection?.hub.id === item.hub.id;
                const isHovered = hoveredHubId === item.hub.id;
                const pinRadius = (isCentral ? 6.5 : 4.5) * invScale;
                const showHubLabel = isCentral || isHovered || showAllHubLabels;

                return (
                  <g
                    key={`hub-${item.hub.id}`}
                    className="cursor-pointer"
                    onClick={(e) => {
                      e.stopPropagation();
                      focusOnHub(item);
                    }}
                    onMouseEnter={() => setHoveredHubId(item.hub.id)}
                    onMouseLeave={() => setHoveredHubId(null)}
                  >
                    {/* Animated pulse ring on Active Main Central Hub */}
                    {isCentral && isActive && (
                      <circle
                        cx={item.x}
                        cy={item.y}
                        r={10 * invScale}
                        fill="var(--primary)"
                        className="sd-central-pulse"
                      />
                    )}

                    {/* Selection Halo */}
                    {(isSelected || isHovered) && (
                      <circle
                        cx={item.x}
                        cy={item.y}
                        r={pinRadius + 3.2 * invScale}
                        fill="none"
                        stroke={hubColor}
                        strokeOpacity={0.45}
                        strokeWidth={1.3 * invScale}
                      />
                    )}

                    {/* Hub Pin Circle */}
                    <circle
                      cx={item.x}
                      cy={item.y}
                      r={pinRadius}
                      fill={isCentral ? hubColor : "var(--card)"}
                      stroke={hubColor}
                      strokeWidth={(isCentral ? 2 : 1.8) * invScale}
                    />

                    {/* Inner Dot */}
                    <circle
                      cx={item.x}
                      cy={item.y}
                      r={(isCentral ? 2.3 : 1.7) * invScale}
                      fill={isCentral ? "var(--primary-foreground)" : hubColor}
                    />

                    {/* Hub Label (only Main Hub + hovered hub when zoomed out; all hubs when zoomed in) */}
                    {showHubLabel && (
                      <text
                        x={item.x}
                        y={item.y + pinRadius + 8.5 * invScale}
                        textAnchor="middle"
                        fontSize={(isCentral ? 7.2 : 6.2) * invScale}
                        fontWeight={isCentral ? 700 : 600}
                        fill={isActive ? "var(--foreground)" : "#b45309"}
                        paintOrder="stroke"
                        stroke="var(--background)"
                        strokeWidth={2.5 * invScale}
                        strokeLinejoin="round"
                      >
                        {item.hub.city}
                        {isCentral ? " ★" : ""}
                        {!isActive ? " (Inactive)" : ""}
                      </text>
                    )}
                  </g>
                );
              })}
            </svg>
          )}

          {/* Bottom-Center Compact Legend */}
          <div className="pointer-events-none absolute bottom-3 left-1/2 -translate-x-1/2 flex w-max max-w-[calc(100%-1.5rem)] flex-wrap items-center justify-center gap-3 rounded-lg border border-border/80 bg-background/90 px-3 py-1.5 text-[11px] text-muted-foreground backdrop-blur-xs shadow-2xs">
            <span className="inline-flex items-center gap-1.5 font-medium text-foreground">
              <span className="size-2.5 rounded-full bg-primary ring-2 ring-primary/25" />
              Main Hub
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="size-2.5 rounded-full border-2 border-primary bg-card" />
              Active Hub
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="size-2 rounded-full border border-primary bg-background" />
              Delivery Zone
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="size-2.5 rounded-full border-2 border-amber-600 bg-card" />
              Inactive
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="w-4 border-t-2 border-dashed border-primary" />
              Transit Route
            </span>
          </div>
        </div>
      </div>

      {/* Right: Inspector & Active Hubs List */}
      <div className="lg:col-span-4 space-y-4">
        {/* Selected / Main Hub Inspector */}
        <div className="rounded-2xl border border-border bg-card p-5 shadow-2xs space-y-4">
          {activeSelection ? (
            <>
              <div className="flex items-start justify-between gap-2">
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="rounded-md bg-muted px-2 py-0.5 font-mono text-[11px] font-semibold text-foreground">
                      {activeSelection.hub.code}
                    </span>
                    {activeSelection.hub.central && (
                      <span className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
                        <Star className="size-3 fill-primary" /> Main Hub
                      </span>
                    )}
                    {!activeSelection.hub.active && (
                      <span className="inline-flex items-center rounded-full bg-amber-500/15 px-2 py-0.5 text-[11px] font-semibold text-amber-700 dark:text-amber-400">
                        Inactive
                      </span>
                    )}
                  </div>
                  <h3 className="text-base font-bold text-foreground">
                    {activeSelection.hub.name}
                  </h3>
                  <p className="text-xs text-muted-foreground">
                    {activeSelection.hub.city} · {activeSelection.assignedZones.length} assigned{" "}
                    {activeSelection.assignedZones.length === 1 ? "zone" : "zones"}
                  </p>
                </div>

                <Button
                  type="button"
                  variant="outline"
                  size="xs"
                  onClick={() => focusOnHub(activeSelection)}
                >
                  <LocateFixed className="size-3 mr-1" /> Focus
                </Button>
              </div>

              <div className="space-y-2 border-t border-border/70 pt-3">
                <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Assigned Delivery Zones
                </p>
                {activeSelection.assignedZones.length === 0 ? (
                  <p className="text-xs text-muted-foreground">
                    No delivery zones assigned to this hub yet.
                  </p>
                ) : (
                  <div className="flex flex-wrap gap-1.5 max-h-40 overflow-y-auto pr-1">
                    {activeSelection.assignedZones.map((zone) => {
                      const isZoneActive = activeSelection.hub.active && zone.active;
                      return (
                        <span
                          key={zone.id}
                          className={cn(
                            "inline-flex items-center gap-1 rounded-md border px-2 py-1 text-[11px] font-medium",
                            isZoneActive
                              ? "border-border bg-muted/40 text-foreground"
                              : "border-amber-500/40 bg-amber-500/10 text-amber-800 dark:text-amber-300"
                          )}
                        >
                          <MapPin
                            className={cn(
                              "size-3",
                              isZoneActive ? "text-primary" : "text-amber-600"
                            )}
                          />
                          <span>{zone.name}</span>
                          <span className="font-mono text-[10px] text-muted-foreground">
                            ({zone.code})
                          </span>
                        </span>
                      );
                    })}
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="py-6 text-center text-xs text-muted-foreground">
              No active hubs found. Add or activate a hub to visualize it on the Sri Lanka map.
            </div>
          )}
        </div>

        {/* Active Hubs Quick Switcher */}
        <div className="rounded-2xl border border-border bg-card p-5 shadow-2xs space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
              Network Hubs ({plottedHubs.length})
            </h3>
            {zoom > 1 && (
              <button
                type="button"
                onClick={resetView}
                className="text-[11px] font-medium text-primary hover:underline"
              >
                Show full island
              </button>
            )}
          </div>

          <div className="space-y-1.5 max-h-[260px] overflow-y-auto pr-1">
            {plottedHubs.map((item) => {
              const isSelected = activeSelection?.hub.id === item.hub.id;
              return (
                <button
                  key={item.hub.id}
                  type="button"
                  onClick={() => focusOnHub(item)}
                  className={cn(
                    "w-full flex items-center justify-between gap-2 rounded-xl border px-3 py-2 text-left transition-colors",
                    isSelected
                      ? "border-primary/40 bg-accent/60 text-foreground"
                      : "border-border/70 bg-background hover:bg-muted/50 text-foreground"
                  )}
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <span
                      className={cn(
                        "inline-flex size-6 shrink-0 items-center justify-center rounded-md",
                        !item.hub.active
                          ? "bg-amber-500/15 text-amber-600"
                          : item.hub.central
                            ? "bg-primary text-primary-foreground"
                            : "bg-muted text-muted-foreground"
                      )}
                    >
                      {item.hub.central ? (
                        <Star className="size-3.5 fill-current" />
                      ) : (
                        <Warehouse className="size-3.5" />
                      )}
                    </span>
                    <div className="min-w-0">
                      <p className="truncate text-xs font-semibold">
                        {item.hub.city}
                        {item.hub.central ? " (Main)" : ""}
                      </p>
                      <p className="truncate font-mono text-[10px] text-muted-foreground">
                        {item.hub.code}
                      </p>
                    </div>
                  </div>
                  <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-[10px] font-semibold text-muted-foreground">
                    {item.assignedZones.length} {item.assignedZones.length === 1 ? "zone" : "zones"}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
