"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { cn } from "cn";
import { useReducedMotion } from "./use-reduced-motion";
import "./landing.css";

/**
 * Sri Lanka's coast as (longitude, latitude) points, clockwise from the Jaffna peninsula, simplified
 * to read well at this size. Projected to the 300 × 440 view box and smoothed into curves.
 */
const COAST: [number, number][] = [
  [79.9, 9.72], [79.95, 9.8], [80.05, 9.82], [80.23, 9.83], [80.45, 9.62], [80.82, 9.27], [81.0, 8.98],
  [81.23, 8.57], [81.43, 8.25], [81.7, 7.72], [81.82, 7.41], [81.84, 6.88], [81.72, 6.52], [81.45, 6.3],
  [81.12, 6.12], [80.8, 6.02], [80.59, 5.92], [80.22, 6.03], [79.99, 6.42], [79.86, 6.93], [79.84, 7.21],
  [79.79, 7.58], [79.8, 7.95], [79.74, 8.25], [79.88, 8.5], [79.93, 8.8], [79.9, 9.0], [79.88, 9.06],
  [80.0, 9.07], [80.2, 9.47], [80.28, 9.56], [80.14, 9.63], [79.98, 9.68],
];

const project = ([lon, lat]: [number, number]): [number, number] => [38 + (lon - 79.6) * 100, 20 + (9.9 - lat) * 100];

/** A closed Catmull-Rom curve through the points, as SVG cubic Béziers. */
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

const ISLAND = smoothClosedPath(COAST.map(project));

/** Hill country around Nuwara Eliya: three wobbly contour rings. */
const HIGHLANDS = [54, 36, 20].map((radius, ring) =>
  smoothClosedPath(
    Array.from({ length: 9 }, (_, index): [number, number] => {
      const angle = (index / 9) * 2 * Math.PI;
      const seed = 3 - ring;
      const r = radius * (1 + 0.18 * Math.sin(3 * angle + seed) + 0.08 * Math.cos(5 * angle + seed * 2));
      return [160 + r * Math.cos(angle) * 0.8, 318 + r * Math.sin(angle)];
    }),
  ),
);

/** The sample parcel's stops, in travel order. */
const STOPS = [
  { name: "Colombo", x: 68, y: 316, label: "left" },
  { name: "Kandy", x: 142, y: 281, label: "right" },
  { name: "Anuradhapura", x: 118, y: 179, label: "right" },
  { name: "Jaffna", x: 84, y: 40, label: "right" },
] as const;

/** Roughly the A1, A9 and the road through Elephant Pass. */
const LEGS = [
  "M68 316Q102 290 142 281",
  "M142 281Q146 226 118 179",
  "M118 179C120 124 128 74 114 56S96 42 84 40",
];

/** Other towns on the network, drawn faintly for depth. */
const TOWNS = [
  { name: "Galle", x: 100, y: 404 },
  { name: "Trincomalee", x: 198, y: 155 },
  { name: "Batticaloa", x: 243, y: 240 },
  { name: "Ella", x: 183, y: 323 },
];

const NETWORK = [
  "M68 316Q76 366 100 404",
  "M142 281Q166 296 183 323",
  "M183 323Q218 296 243 240",
  "M142 281Q182 222 198 155",
  "M118 179Q158 158 198 155",
];

const TRAVEL_MS = 1900;
const DWELL_MS = 1100;
const FINAL_MS = 2800;

/**
 * The timeline alternates waiting at a stop and travelling a leg:
 * wait at Colombo, leg 1, wait at Kandy, leg 2, wait at Anuradhapura, leg 3, wait at Jaffna.
 */
const SEGMENTS = LEGS.flatMap(() => [DWELL_MS, TRAVEL_MS]).concat(FINAL_MS);
const CYCLE_MS = SEGMENTS.reduce((sum, length) => sum + length, 0);

const easeInOut = (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2);

/**
 * Hero illustration: a sample parcel travels across the island, drawing its route behind it and
 * lighting up each stop. Driven by requestAnimationFrame with direct DOM updates, so React
 * re-renders only when the parcel reaches a new segment.
 */
export function IslandMap({ className }: { className?: string }) {
  const reducedMotion = useReducedMotion();
  const [segment, setSegment] = useState(SEGMENTS.length - 1);
  const hostRef = useRef<HTMLDivElement>(null);
  const pinRef = useRef<SVGGElement>(null);
  const legRefs = useRef<(SVGPathElement | null)[]>([]);
  const trailRefs = useRef<(SVGPathElement | null)[]>([]);
  const legRefCallbacks = useMemo(
    () => LEGS.map((_, index) => (node: SVGPathElement | null) => {
      legRefs.current[index] = node;
    }),
    [],
  );
  const trailRefCallbacks = useMemo(
    () => LEGS.map((_, index) => (node: SVGPathElement | null) => {
      trailRefs.current[index] = node;
    }),
    [],
  );

  useEffect(() => {
    let isMounted = true;
    const legs = legRefs.current.filter((leg): leg is SVGPathElement => Boolean(leg));
    const trails = trailRefs.current.filter((trail): trail is SVGPathElement => Boolean(trail));
    const pin = pinRef.current;
    const host = hostRef.current;

    // Ensure all required SVG paths, trails, and elements are mounted before initializing
    if (!pin || !host || legs.length !== LEGS.length || trails.length !== LEGS.length) {
      return;
    }

    const lengths = legs.map((leg) => {
      try {
        return leg.getTotalLength();
      } catch {
        return 0;
      }
    });

    trails.forEach((trail, index) => {
      if (trail) {
        trail.setAttribute("stroke-dasharray", `${lengths[index]}`);
      }
    });

    const place = (legIndex: number, progress: number) => {
      if (!isMounted || !pin) return;
      const leg = legs[legIndex];
      if (!leg || typeof leg.getPointAtLength !== "function") return;
      try {
        const length = lengths[legIndex] ?? 0;
        const point = leg.getPointAtLength(length * progress);
        pin.setAttribute("transform", `translate(${point.x} ${point.y})`);
      } catch {
        // Element might be detached during navigation
      }
    };

    const drawTrails = (legIndex: number, progress: number) => {
      if (!isMounted) return;
      trails.forEach((trail, index) => {
        if (!trail) return;
        const length = lengths[index] ?? 0;
        const done = index < legIndex ? 1 : index === legIndex ? progress : 0;
        trail.setAttribute("stroke-dashoffset", `${length * (1 - done)}`);
      });
    };

    if (reducedMotion) {
      // Still picture: the finished journey
      place(LEGS.length - 1, 1);
      drawTrails(LEGS.length - 1, 1);
      return;
    }

    let frame = 0;
    let visible = true;
    let elapsed = 0;
    let last = performance.now();
    let shownSegment = -1;

    const observer = new IntersectionObserver(([entry]) => {
      if (!isMounted) return;
      visible = entry ? entry.isIntersecting : true;
    });
    observer.observe(host);

    const tick = (now: number) => {
      if (!isMounted) return;
      const delta = Math.min(now - last, 100);
      last = now;
      if (visible) {
        elapsed = (elapsed + delta) % CYCLE_MS;
        let time = elapsed;
        let index = 0;
        while (index < SEGMENTS.length && time >= SEGMENTS[index]) {
          time -= SEGMENTS[index];
          index += 1;
        }
        if (index >= SEGMENTS.length) {
          index = SEGMENTS.length - 1;
        }
        const travelling = index % 2 === 1;
        const legIndex = travelling ? Math.floor((index - 1) / 2) : Math.floor(index / 2) - 1;
        const segmentDuration = SEGMENTS[index] || 1;
        const progress = travelling ? easeInOut(Math.min(Math.max(time / segmentDuration, 0), 1)) : 1;
        if (legIndex < 0) {
          place(0, 0);
          drawTrails(-1, 0);
        } else if (legIndex < LEGS.length) {
          place(legIndex, progress);
          drawTrails(legIndex, progress);
        }
        if (index !== shownSegment && isMounted) {
          shownSegment = index;
          setSegment(index);
        }
      }
      if (isMounted) {
        frame = requestAnimationFrame(tick);
      }
    };
    frame = requestAnimationFrame(tick);

    return () => {
      isMounted = false;
      cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, [reducedMotion]);

  const shown = reducedMotion ? SEGMENTS.length - 1 : segment;
  const reached = Math.floor(shown / 2);
  const travelling = shown % 2 === 1;

  return (
    <div
      ref={hostRef}
      role="img"
      aria-label="Illustration: a sample parcel travelling across Sri Lanka from Colombo to Jaffna"
      className={cn("relative mx-auto aspect-[300/440] w-full max-w-[340px]", className)}
    >
      <svg viewBox="0 0 300 440" className="absolute inset-0 size-full overflow-visible" aria-hidden="true">
        <defs>
          <linearGradient id="island-land" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--card)" />
            <stop offset="100%" stopColor="var(--accent)" />
          </linearGradient>
        </defs>

        {/* Shallow water, then wave marks drifting along the coast */}
        <path d={ISLAND} className="fill-none stroke-primary/[0.06]" strokeWidth="44" strokeLinejoin="round" />
        <path d={ISLAND} className={cn("fill-none stroke-primary/30", "landing-ripple")} strokeWidth="16" strokeLinejoin="round" />

        {/* Land and the hill country */}
        <path d={ISLAND} fill="url(#island-land)" className="stroke-primary/50" strokeWidth="1.4" strokeLinejoin="round" />
        {HIGHLANDS.map((d) => (
          <path key={d} d={d} className="fill-primary/[0.04] stroke-primary/20" strokeDasharray="2 4" />
        ))}

        {/* Network */}
        {NETWORK.map((d) => (
          <path key={d} d={d} className={cn("fill-none stroke-primary/30", "landing-network")} strokeWidth="1.2" />
        ))}
        {TOWNS.map((town) => (
          <g key={town.name}>
            <circle cx={town.x} cy={town.y} r="2.5" className="fill-primary/40" />
            <text x={town.x + 6} y={town.y + 3} className="fill-muted-foreground text-[8px]">{town.name}</text>
          </g>
        ))}

        {/* The sample parcel's route: road ahead, then the part already travelled */}
        {LEGS.map((d, index) => (
          <path key={`road-${d}`} ref={legRefCallbacks[index]} d={d}
            className="fill-none stroke-border" strokeWidth="3" strokeDasharray="1 6" strokeLinecap="round" />
        ))}
        {LEGS.map((d, index) => (
          <path key={`trail-${d}`} ref={trailRefCallbacks[index]} d={d}
            className="fill-none stroke-primary" strokeWidth="3.5" strokeLinecap="round" />
        ))}

        {STOPS.map((stop, index) => {
          const isReached = index <= reached;
          const isHere = index === reached && !travelling;
          return (
            <g key={stop.name}>
              {isHere && <circle cx={stop.x} cy={stop.y} r="6" className={cn("fill-primary", "landing-pulse")} />}
              <circle cx={stop.x} cy={stop.y} r="6" strokeWidth="2.5"
                className={cn("transition-colors duration-500",
                  isReached ? "fill-primary stroke-card" : "fill-card stroke-primary/50")} />
              <text
                x={stop.label === "left" ? stop.x - 12 : stop.x + 12}
                y={stop.y + 4}
                textAnchor={stop.label === "left" ? "end" : "start"}
                className={cn("text-[11px] font-bold transition-colors duration-500",
                  isReached ? "fill-foreground" : "fill-muted-foreground")}
              >
                {stop.name}
              </text>
            </g>
          );
        })}

        {/* Parcel pin */}
        <g ref={pinRef} transform={`translate(${STOPS[0].x} ${STOPS[0].y})`}>
          <ellipse cx="0" cy="1" rx="6" ry="2" className="fill-foreground/20" />
          <g className={travelling ? undefined : "landing-bob"}>
            <path d="M0 0C-3 -6 -12 -11 -12 -20A12 12 0 0 1 12 -20C12 -11 3 -6 0 0Z" className="fill-primary" />
            <rect x="-5.5" y="-25.5" width="11" height="11" rx="2" className="fill-primary-foreground" />
            <path d="M-5.5 -21.5H5.5M0 -25.5V-21.5" className="stroke-primary" strokeWidth="1.4" />
          </g>
        </g>

        {/* Compass */}
        <g transform="translate(262 46)" className="text-muted-foreground">
          <circle r="15" className="fill-card stroke-border" />
          <path d="M0 -10L4 2H-4Z" className="fill-primary" />
          <path d="M0 10L4 -2H-4Z" className="fill-muted-foreground/40" />
          <text y="-19" textAnchor="middle" className="fill-muted-foreground text-[8px] font-bold">N</text>
        </g>
      </svg>
    </div>
  );
}
