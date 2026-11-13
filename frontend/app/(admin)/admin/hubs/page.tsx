"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import {
  AlertTriangle,
  Building2,
  CalendarDays,
  Check,
  ChevronDown,
  Edit2,
  Globe,
  Loader2,
  MapPin,
  MapPinned,
  Pencil,
  Plus,
  Power,
  Search,
  Star,
  Trash2,
  Warehouse,
  X,
} from "lucide-react";
import { cn } from "cn";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ThemedSelect } from "@/components/ui/select";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { ConfirmationDialog } from "@/components/ui/confirmation-dialog";
import { IslandCoverageMap } from "@/components/admin/island-coverage-map";
import { formatApiError } from "@/lib/api";
import { createHub, deleteHub, listHubs, updateHub, type Hub } from "@/lib/hubs";
import { listDistricts, type District } from "@/lib/districts";
import { createZone, deleteZone, listAdminZones, updateZone, type ZoneAdmin } from "@/lib/pricing";

const EMPTY_HUB_FORM = { code: "", name: "", city: "", zoneId: "", central: false };
const EMPTY_ZONE_FORM = { code: "", name: "" };

/**
 * Hub & Zone Network: Central and branch hubs hierarchy alongside delivery service areas.
 * Styled to match the Dispatch Control Tower dashboard.
 */
export default function AdminHubsAndZonesPage() {
  const [tab, setTab] = useState<"hubs" | "zones" | "coverage">("hubs");

  // Shared state
  const [hubs, setHubs] = useState<Hub[] | null>(null);
  const [zones, setZones] = useState<ZoneAdmin[] | null>(null);
  const [districts, setDistricts] = useState<District[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Hub form states
  const [hubForm, setHubForm] = useState(EMPTY_HUB_FORM);
  const [selectedDistrict, setSelectedDistrict] = useState<District | null>(null);
  const [savingHub, setSavingHub] = useState(false);
  const [busyHub, setBusyHub] = useState<string | null>(null);
  const [hubToDelete, setHubToDelete] = useState<Hub | null>(null);
  const [deletingHub, setDeletingHub] = useState(false);

  // Zone form & filter states
  const [zoneForm, setZoneForm] = useState(EMPTY_ZONE_FORM);
  const [zoneSearch, setZoneSearch] = useState("");
  const [selectedHubId, setSelectedHubId] = useState("");
  const [selectedSubZoneName, setSelectedSubZoneName] = useState("");
  const [savingZone, setSavingZone] = useState(false);
  const [editingZone, setEditingZone] = useState<ZoneAdmin | null>(null);
  const [zoneToDelete, setZoneToDelete] = useState<ZoneAdmin | null>(null);
  const [deletingZone, setDeletingZone] = useState(false);

  const load = useCallback(() => {
    Promise.all([listHubs(), listAdminZones(), listDistricts()])
      .then(([loadedHubs, loadedZones, loadedDistricts]) => {
        setHubs(loadedHubs);
        setZones(loadedZones);
        setDistricts(loadedDistricts);
        setError(null);
      })
      .catch((failure) => setError(formatApiError(failure)));
  }, []);

  useEffect(() => {
    load();
    const interval = setInterval(() => {
      load();
    }, 10000);

    function onFocus() {
      load();
    }
    window.addEventListener("focus", onFocus);

    return () => {
      clearInterval(interval);
      window.removeEventListener("focus", onFocus);
    };
  }, [load]);

  const zoneList = useMemo(() => zones ?? [], [zones]);
  const zoneName = (zoneId: string | null) => zoneList.find((zone) => zone.id === zoneId)?.name;

  const servedZoneIds = useMemo(() => {
    const set = new Set<string>();
    (hubs ?? []).filter((h) => h.active).forEach((h) => {
      if (h.zoneId) set.add(h.zoneId);
    });
    zoneList.forEach((z) => {
      if (z.hubId && hubs?.some((h) => h.active && h.id === z.hubId)) {
        set.add(z.id);
      }
    });
    return set;
  }, [hubs, zoneList]);

  const activeZonesCount = useMemo(() => zoneList.filter((z) => z.active).length, [zoneList]);
  const coveredDistrictsCount = useMemo(() => {
    const covered = new Set<string>();
    for (const h of hubs ?? []) {
      if (h.active && h.city) covered.add(h.city.toLowerCase());
    }
    return covered.size;
  }, [hubs]);
  const coveragePercent = useMemo(
    () => (zoneList.length > 0 ? Math.round((servedZoneIds.size / zoneList.length) * 100) : 0),
    [servedZoneIds, zoneList]
  );

  const today = useMemo(
    () =>
      new Intl.DateTimeFormat("en-US", {
        timeZone: "Asia/Colombo",
        dateStyle: "medium",
      }).format(new Date()),
    [],
  );

  const unserved = zoneList.filter((zone) => !servedZoneIds.has(zone.id));
  const takenZones = new Set((hubs ?? []).map((hub) => hub.zoneId));
  const central = hubs?.find((hub) => hub.central && hub.active);
  const branches = (hubs ?? []).filter((hub) => hub !== central);

  const getAssignedZones = useCallback(
    (hub: Hub) => zoneList.filter((z) => z.hubId === hub.id || z.id === hub.zoneId),
    [zoneList],
  );

  const servedZoneMap = useMemo(() => {
    const map = new Map<string, Hub>();
    (hubs ?? []).forEach((hub) => {
      if (hub.zoneId) map.set(hub.zoneId, hub);
    });
    zoneList.forEach((z) => {
      if (z.hubId) {
        const found = (hubs ?? []).find((h) => h.id === z.hubId);
        if (found) map.set(z.id, found);
      }
    });
    return map;
  }, [hubs, zoneList]);

  const existingDistrictKeys = useMemo(() => {
    const set = new Set<string>();
    for (const h of hubs ?? []) {
      if (h.city) set.add(h.city.trim().toLowerCase());
      if (h.code) set.add(h.code.trim().toLowerCase());
    }
    return set;
  }, [hubs]);

  const selectedHub = useMemo(
    () => (hubs ?? []).find((h) => h.id === selectedHubId),
    [hubs, selectedHubId]
  );

  const currentDistrict = useMemo(() => {
    if (!selectedHub) return undefined;
    const hubCity = selectedHub.city.trim().toLowerCase();
    const hubCode = selectedHub.code.trim().toLowerCase();
    return districts.find(
      (d) => d.name.toLowerCase() === hubCity || d.code.toLowerCase() === hubCode
    );
  }, [selectedHub, districts]);

  const availableSubZones = useMemo(() => {
    if (!currentDistrict) return [];
    return currentDistrict.zones;
  }, [currentDistrict]);

  const existingZoneCodes = useMemo(
    () => new Set(zoneList.map((z) => z.code.toUpperCase())),
    [zoneList]
  );

  const existingZoneNames = useMemo(
    () => new Set(zoneList.map((z) => z.name.toLowerCase())),
    [zoneList]
  );

  /* ------------------- Hub Actions ------------------- */
  function handleDistrictSelect(district: District) {
    if (existingDistrictKeys.has(district.name.toLowerCase()) || existingDistrictKeys.has(district.code.toLowerCase())) {
      return;
    }
    setSelectedDistrict(district);
    const matchingZone = zoneList.find(
      (z) => z.name.toLowerCase() === district.name.toLowerCase() || z.code.toUpperCase() === district.code.toUpperCase()
    );
    setHubForm((prev) => ({
      ...prev,
      city: district.name,
      name: prev.name || district.defaultBranchName,
      code: prev.code || district.code,
      zoneId: matchingZone ? matchingZone.id : prev.zoneId,
    }));
  }

  async function submitHub(event: FormEvent) {
    event.preventDefault();
    setSavingHub(true);
    setError(null);
    setSuccess(null);
    try {
      let targetZoneId = hubForm.zoneId;
      if (!targetZoneId && selectedDistrict && !hubForm.central) {
        const existingZone = zoneList.find(
          (z) => z.code.toUpperCase() === selectedDistrict.code.toUpperCase()
        );
        if (existingZone) {
          targetZoneId = existingZone.id;
        } else {
          try {
            const created = await createZone({
              code: selectedDistrict.code,
              name: selectedDistrict.name,
              active: true,
            });
            targetZoneId = created.id;
          } catch {
            // Proceed without dedicated primary zone if it already exists
          }
        }
      }
      await createHub({ ...hubForm, zoneId: targetZoneId || null });
      setHubForm(EMPTY_HUB_FORM);
      setSelectedDistrict(null);
      setSuccess("Hub created successfully.");
      load();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setSavingHub(false);
    }
  }

  async function saveHub(
    hub: Hub,
    changes: { name: string; city: string; zoneId: string | null; central?: boolean },
  ) {
    setBusyHub(hub.id);
    setError(null);
    setSuccess(null);
    try {
      await updateHub(hub.id, {
        ...hub,
        ...changes,
        central: changes.central ?? hub.central,
      });
      setSuccess(`Hub "${hub.name}" updated successfully.`);
      load();
      return true;
    } catch (failure) {
      setError(formatApiError(failure));
      return false;
    } finally {
      setBusyHub(null);
    }
  }

  async function toggleHubActive(hub: Hub) {
    setBusyHub(hub.id);
    setError(null);
    setSuccess(null);
    try {
      await updateHub(hub.id, { ...hub, active: !hub.active });
      setSuccess(`Hub "${hub.name}" is now ${!hub.active ? "active" : "inactive"}.`);
      load();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setBusyHub(null);
    }
  }

  async function confirmDeleteHub() {
    if (!hubToDelete) return;
    setDeletingHub(true);
    setError(null);
    setSuccess(null);
    try {
      await deleteHub(hubToDelete.id);
      setSuccess(`Hub "${hubToDelete.name}" deleted successfully.`);
      setHubs((prev) => (prev ? prev.filter((h) => h.id !== hubToDelete.id) : prev));
      setHubToDelete(null);
      load();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setDeletingHub(false);
    }
  }

  /* ------------------- Zone Actions ------------------- */
  function handleHubChange(hubId: string) {
    setSelectedHubId(hubId);
    setSelectedSubZoneName("");
    setZoneForm(EMPTY_ZONE_FORM);
  }

  function handleSubZoneChange(subZoneName: string) {
    setSelectedSubZoneName(subZoneName);
    const matched = availableSubZones.find((sz) => sz.name === subZoneName);
    if (matched) {
      setZoneForm({
        name: matched.name,
        code: matched.code,
      });
    } else {
      setZoneForm((prev) => ({
        ...prev,
        name: subZoneName,
      }));
    }
  }

  async function handleCreateZone(e: FormEvent) {
    e.preventDefault();
    if (existingZoneCodes.has(zoneForm.code.trim().toUpperCase())) {
      setError(`Zone code "${zoneForm.code}" already exists.`);
      return;
    }
    if (existingZoneNames.has(zoneForm.name.trim().toLowerCase())) {
      setError(`Zone "${zoneForm.name}" already exists.`);
      return;
    }
    setSavingZone(true);
    setError(null);
    setSuccess(null);
    try {
      await createZone({ ...zoneForm, hubId: selectedHubId || null });
      setZoneForm(EMPTY_ZONE_FORM);
      setSelectedSubZoneName("");
      setSuccess(`Zone ${zoneForm.code} (${zoneForm.name}) added and assigned to hub successfully.`);
      load();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setSavingZone(false);
    }
  }

  async function handleUpdateZone(e: FormEvent) {
    e.preventDefault();
    if (!editingZone) return;
    setSavingZone(true);
    setError(null);
    setSuccess(null);
    try {
      await updateZone(editingZone.id, {
        code: editingZone.code,
        name: editingZone.name,
        active: editingZone.active,
        version: editingZone.version,
      });
      setSuccess(`Zone ${editingZone.code} updated successfully.`);
      setEditingZone(null);
      load();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setSavingZone(false);
    }
  }

  async function toggleZoneActive(zone: ZoneAdmin) {
    setError(null);
    setSuccess(null);
    try {
      await updateZone(zone.id, {
        code: zone.code,
        name: zone.name,
        active: !zone.active,
        version: zone.version,
      });
      setSuccess(`Zone ${zone.code} is now ${!zone.active ? "active" : "inactive"}.`);
      load();
    } catch (failure) {
      setError(formatApiError(failure));
    }
  }

  async function confirmDeleteZone() {
    if (!zoneToDelete) return;
    setDeletingZone(true);
    setError(null);
    setSuccess(null);
    try {
      await deleteZone(zoneToDelete.id);
      setSuccess(`Zone ${zoneToDelete.code} (${zoneToDelete.name}) has been deleted.`);
      setZones((prev) => (prev ? prev.filter((z) => z.id !== zoneToDelete.id) : prev));
      setZoneToDelete(null);
      load();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setDeletingZone(false);
    }
  }

  // Options for ThemedSelect in Zone creation
  const hubOptions = useMemo(() => {
    return (hubs ?? []).map((h) => ({
      value: h.id,
      label: `${h.name} (${h.city})`,
      badge: h.central ? "Central Hub" : h.code,
      badgeVariant: (h.central ? "success" : "default") as "success" | "default",
      description: h.central ? "Island Main Transit Hub" : `Serving ${h.city}`,
    }));
  }, [hubs]);

  const subZoneOptions = useMemo(() => {
    return availableSubZones.map((sz) => {
      const isTaken =
        existingZoneCodes.has(sz.code.toUpperCase()) ||
        existingZoneNames.has(sz.name.toLowerCase());
      return {
        value: sz.name,
        label: sz.name,
        badge: sz.code,
        badgeVariant: (isTaken ? "warning" : "default") as "warning" | "default",
        disabled: isTaken,
        description: isTaken ? "Already added to system" : `Postal Code: ${sz.postalCode || "Standard"}`,
      };
    });
  }, [availableSubZones, existingZoneCodes, existingZoneNames]);

  return (
    <div className="space-y-6">
      {/* Top Operations Header Bar */}
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-card p-4 shadow-xs sm:flex-row sm:items-center sm:justify-between sm:p-5">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1.5 font-bold text-primary">
              <Warehouse className="size-3.5" aria-hidden="true" />
              Network Control Tower
            </span>
            <span aria-hidden="true">&middot;</span>
            <span className="inline-flex items-center gap-1.5">
              <CalendarDays className="size-3.5" aria-hidden="true" />
              <time suppressHydrationWarning>{today}</time>
            </span>
            <span aria-hidden="true">&middot;</span>
            <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
              Island Network Active
            </span>
          </div>
          <p className="text-sm text-muted-foreground">
            Coordinate regional parcel sorting hubs, branch transfers, and geographic delivery coverage.
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs text-muted-foreground shrink-0 self-start sm:self-center">
          <span className="relative flex size-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full size-2 bg-emerald-500"></span>
          </span>
          <span className="font-mono text-[11px] text-muted-foreground">Live synced</span>
        </div>
      </div>

      {/* Island Hubs & Delivery Zones Hero Banner (Matches Dispatch Fleet Control) */}
      <section className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-primary via-primary/95 to-primary/90 px-6 py-7 text-primary-foreground sm:px-8 sm:py-8 shadow-sm">
        <svg
          className="pointer-events-none absolute -right-12 -top-10 h-64 w-96 opacity-20"
          viewBox="0 0 320 220"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="M10 190 C 80 120, 120 200, 180 120 S 260 40, 310 20"
            stroke="currentColor"
            strokeWidth="3.5"
            strokeDasharray="10 10"
            strokeLinecap="round"
          />
          <circle cx="10" cy="190" r="7" fill="currentColor" />
          <circle cx="180" cy="120" r="6" fill="currentColor" />
          <circle cx="310" cy="20" r="8" fill="currentColor" />
        </svg>
        <div className="relative max-w-2xl space-y-3">
          <p className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.15em] opacity-85">
            <MapPinned className="size-4" aria-hidden="true" /> Island-Wide Logistic Command
          </p>
          <h2 className="text-2xl font-extrabold leading-tight sm:text-3xl">
            Hub, Transit &amp; Delivery Zone Control
          </h2>
          <p className="text-sm opacity-90 sm:text-base leading-relaxed">
            Configure regional parcel sorting hubs, connect district branch facilities, and manage geographic delivery coverage across Sri Lanka.
          </p>
          <div className="flex flex-wrap gap-2.5 pt-2">
            <Button
              type="button"
              size="sm"
              onClick={() => {
                setTab("hubs");
                setError(null);
                setSuccess(null);
              }}
              className={cn(
                "font-semibold transition-colors",
                tab === "hubs"
                  ? "bg-primary-foreground text-primary hover:bg-primary-foreground/90 shadow-xs"
                  : "border border-primary-foreground/40 bg-primary-foreground/10 text-primary-foreground hover:bg-primary-foreground/20"
              )}
            >
              <Warehouse className="size-4 mr-1.5" /> Hub Network ({hubs?.length ?? 0})
            </Button>
            <Button
              type="button"
              size="sm"
              onClick={() => {
                setTab("zones");
                setError(null);
                setSuccess(null);
              }}
              className={cn(
                "font-semibold transition-colors",
                tab === "zones"
                  ? "bg-primary-foreground text-primary hover:bg-primary-foreground/90 shadow-xs"
                  : "border border-primary-foreground/40 bg-primary-foreground/10 text-primary-foreground hover:bg-primary-foreground/20"
              )}
            >
              <MapPin className="size-4 mr-1.5" /> Delivery Zones ({zoneList.length})
            </Button>
            <Button
              type="button"
              size="sm"
              onClick={() => {
                setTab("coverage");
                setError(null);
                setSuccess(null);
              }}
              className={cn(
                "font-semibold transition-colors",
                tab === "coverage"
                  ? "bg-primary-foreground text-primary hover:bg-primary-foreground/90 shadow-xs"
                  : "border border-primary-foreground/40 bg-primary-foreground/10 text-primary-foreground hover:bg-primary-foreground/20"
              )}
            >
              <Globe className="size-4 mr-1.5" /> Island Coverage: {coveragePercent}%
            </Button>
          </div>
        </div>
      </section>

      {/* Quick Metrics Cards (5-column matching Dispatch Overview) */}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <div className="flex h-full flex-col justify-between gap-3 rounded-lg border border-border bg-card p-4 shadow-2xs">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-xs font-medium text-muted-foreground">Total sorting hubs</p>
              <p className="mt-1 font-mono text-2xl font-bold text-foreground">
                {hubs ? hubs.length : "—"}
              </p>
            </div>
            <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
              <Warehouse className="size-4" aria-hidden="true" />
            </span>
          </div>
          <p className="text-xs text-muted-foreground">
            {branches.length} branch(es) &middot; {central ? "1 central" : "0 central"}
          </p>
        </div>

        <div className="flex h-full flex-col justify-between gap-3 rounded-lg border border-border bg-card p-4 shadow-2xs">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-xs font-medium text-muted-foreground">Central transit hub</p>
              <p className="mt-1 font-mono text-2xl font-bold text-foreground truncate max-w-[130px]">
                {central ? central.code : "None"}
              </p>
            </div>
            <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-amber-500/20 bg-amber-500/10 text-amber-600 dark:text-amber-400">
              <Star className="size-4 fill-amber-500 text-amber-500" aria-hidden="true" />
            </span>
          </div>
          <p className="text-xs text-muted-foreground truncate">
            {central ? central.name : "Transit unavailable"}
          </p>
        </div>

        <div className="flex h-full flex-col justify-between gap-3 rounded-lg border border-border bg-card p-4 shadow-2xs">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-xs font-medium text-muted-foreground">Active delivery zones</p>
              <p className="mt-1 font-mono text-2xl font-bold text-foreground">
                {activeZonesCount}
              </p>
            </div>
            <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
              <MapPin className="size-4" aria-hidden="true" />
            </span>
          </div>
          <p className="text-xs text-muted-foreground">
            Of {zoneList.length} registered zones
          </p>
        </div>

        <div className="flex h-full flex-col justify-between gap-3 rounded-lg border border-border bg-card p-4 shadow-2xs">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-xs font-medium text-muted-foreground">Assigned zone coverage</p>
              <p className="mt-1 font-mono text-2xl font-bold text-foreground">
                {servedZoneIds.size}
              </p>
            </div>
            <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
              <Globe className="size-4" aria-hidden="true" />
            </span>
          </div>
          <p className="text-xs text-muted-foreground">
            {coveragePercent}% island coverage
          </p>
        </div>

        <div className="flex h-full flex-col justify-between gap-3 rounded-lg border border-border bg-card p-4 shadow-2xs">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-xs font-medium text-muted-foreground">Districts connected</p>
              <p className="mt-1 font-mono text-2xl font-bold text-foreground">
                {coveredDistrictsCount} / 25
              </p>
            </div>
            <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
              <Building2 className="size-4" aria-hidden="true" />
            </span>
          </div>
          <p className="text-xs text-muted-foreground">
            {25 - coveredDistrictsCount} district(s) unserved
          </p>
        </div>
      </div>

      {/* Section Header (Matches Dispatch 'WORKFLOW PIPELINE' Header) */}
      <div className="space-y-1 pt-1">
        <p className="text-xs font-semibold uppercase tracking-wider text-primary">
          Network Topology &amp; Coverage
        </p>
        <h2 className="text-xl font-bold tracking-tight text-foreground">
          Regional Sorting Facilities &amp; Sub-Zones
        </h2>
        <p className="text-xs text-muted-foreground">
          Manage central transit and regional branch hubs, map delivery coverage, and assign cataloged zones.
        </p>
      </div>

      {/* Status Alerts */}
      {error && (
        <Alert variant="destructive" className="pr-10">
          <AlertTriangle aria-hidden="true" />
          <AlertTitle>Action Required</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
          <button
            type="button"
            onClick={() => setError(null)}
            aria-label="Dismiss error"
            className="absolute right-3 top-3 rounded-md p-1 text-destructive/70 hover:bg-destructive/10 hover:text-destructive transition-colors"
          >
            <X className="size-3.5" />
          </button>
        </Alert>
      )}
      {success && (
        <Alert variant="success" role="status" className="pr-10">
          <Check aria-hidden="true" />
          <AlertTitle>Network Updated</AlertTitle>
          <AlertDescription>{success}</AlertDescription>
          <button
            type="button"
            onClick={() => setSuccess(null)}
            aria-label="Dismiss status"
            className="absolute right-3 top-3 rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
          >
            <X className="size-3.5" />
          </button>
        </Alert>
      )}

      {/* Navigation Tabs (Pills Matching Dispatch Tabs) */}
      <div className="flex items-center gap-2 border-b border-border pb-3 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        <button
          type="button"
          onClick={() => {
            setTab("hubs");
            setError(null);
            setSuccess(null);
          }}
          className={cn(
            "flex items-center gap-2 rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-colors",
            tab === "hubs"
              ? "bg-primary text-primary-foreground shadow-xs"
              : "text-muted-foreground hover:bg-muted hover:text-foreground"
          )}
        >
          <Warehouse className="size-3.5" />
          Hub Network
          {hubs && (
            <span
              className={cn(
                "rounded-full px-1.5 py-0.2 text-[10px] font-bold",
                tab === "hubs" ? "bg-primary-foreground/20 text-primary-foreground" : "bg-muted text-muted-foreground"
              )}
            >
              {hubs.length}
            </span>
          )}
        </button>

        <button
          type="button"
          onClick={() => {
            setTab("zones");
            setError(null);
            setSuccess(null);
          }}
          className={cn(
            "flex items-center gap-2 rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-colors",
            tab === "zones"
              ? "bg-primary text-primary-foreground shadow-xs"
              : "text-muted-foreground hover:bg-muted hover:text-foreground"
          )}
        >
          <MapPin className="size-3.5" />
          Delivery Zones
          {zones && (
            <span
              className={cn(
                "rounded-full px-1.5 py-0.2 text-[10px] font-bold",
                tab === "zones" ? "bg-primary-foreground/20 text-primary-foreground" : "bg-muted text-muted-foreground"
              )}
            >
              {zones.length}
            </span>
          )}
        </button>

        <button
          type="button"
          onClick={() => {
            setTab("coverage");
            setError(null);
            setSuccess(null);
          }}
          className={cn(
            "flex items-center gap-2 rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-colors",
            tab === "coverage"
              ? "bg-primary text-primary-foreground shadow-xs"
              : "text-muted-foreground hover:bg-muted hover:text-foreground"
          )}
        >
          <Globe className="size-3.5" />
          Island Coverage
          <span
            className={cn(
              "rounded-full px-1.5 py-0.2 text-[10px] font-bold",
              tab === "coverage" ? "bg-primary-foreground/20 text-primary-foreground" : "bg-muted text-muted-foreground"
            )}
          >
            {coveragePercent}%
          </span>
        </button>
      </div>

      {/* ===================== TAB 1: HUB NETWORK ===================== */}
      {tab === "hubs" && (
        <div className="space-y-6">
          {hubs !== null && unserved.length > 0 && (
            <Alert variant="warning">
              <AlertTriangle aria-hidden="true" />
              <AlertTitle>
                {unserved.length} Delivery {unserved.length === 1 ? "Zone" : "Zones"} Without an Active Branch Hub
              </AlertTitle>
              <AlertDescription className="space-y-2">
                <p>
                  Parcels to or from these zones are paused until each has an active branch hub assigned:
                </p>
                <div className="flex flex-wrap gap-1.5 pt-0.5">
                  {unserved.map((zone) => (
                    <span
                      key={zone.id}
                      className="inline-flex items-center rounded-md border border-amber-500/25 bg-amber-500/10 px-2 py-0.5 text-[11px] font-medium text-amber-900 dark:text-amber-200"
                    >
                      {zone.name}
                    </span>
                  ))}
                </div>
              </AlertDescription>
            </Alert>
          )}

          {hubs !== null && !central && hubs.length > 0 && (
            <Alert variant="warning">
              <AlertTriangle aria-hidden="true" />
              <AlertTitle>No Active Central Hub</AlertTitle>
              <AlertDescription>
                Parcels between two different branches cannot be booked until a central hub is active.
              </AlertDescription>
            </Alert>
          )}

          {/* Central Hub & Branches Visual Network */}
          <section aria-label="Hubs" className="rounded-2xl border border-border bg-card p-5 shadow-xs">
            {hubs === null ? (
              <div className="h-40 animate-pulse rounded-xl bg-muted" aria-hidden="true" />
            ) : hubs.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">No hubs configured yet. Start with the central hub.</p>
            ) : (
              <div className="flex flex-col items-center gap-6">
                {central && (
                  <HubCard
                    hub={central}
                    zone={zoneName(central.zoneId)}
                    assignedZones={getAssignedZones(central)}
                    busy={busyHub === central.id}
                    onToggle={toggleHubActive}
                    onDelete={setHubToDelete}
                    zones={zoneList}
                    takenZones={takenZones}
                    onSave={saveHub}
                    hasOtherCentralHub={false}
                  />
                )}
                {branches.length > 0 && (
                  <>
                    {central && <span aria-hidden="true" className="h-6 border-l-2 border-dashed border-primary/40" />}
                    <div className="w-full max-h-[820px] overflow-y-auto pr-1">
                      <ul className="grid w-full gap-3 sm:grid-cols-2 lg:grid-cols-3">
                        {branches.map((hub) => (
                          <li key={hub.id}>
                            <HubCard
                              hub={hub}
                              zone={zoneName(hub.zoneId)}
                              assignedZones={getAssignedZones(hub)}
                              busy={busyHub === hub.id}
                              onToggle={toggleHubActive}
                              onDelete={setHubToDelete}
                              zones={zoneList}
                              takenZones={takenZones}
                              onSave={saveHub}
                              hasOtherCentralHub={Boolean(central && central.id !== hub.id)}
                            />
                          </li>
                        ))}
                      </ul>
                    </div>
                  </>
                )}
              </div>
            )}
          </section>

          {/* Add a Hub Form */}
          <form onSubmit={submitHub} className="space-y-4 rounded-2xl border border-border bg-card p-5 shadow-xs">
            <h3 className="flex items-center gap-2 font-bold text-foreground">
              <Plus className="size-4 text-primary" aria-hidden="true" /> Add a new branch / hub
            </h3>

            <div className="grid gap-3 sm:grid-cols-3">
              <div className="space-y-1.5">
                <Label htmlFor="hub-district">Administrative District</Label>
                <DistrictSelect
                  selected={selectedDistrict}
                  onSelect={handleDistrictSelect}
                  districts={districts}
                  existingDistricts={existingDistrictKeys}
                />
                <p className="text-[11px] text-muted-foreground">Select from the 25 official districts.</p>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="hub-code">Hub Code</Label>
                <Input
                  id="hub-code"
                  required
                  maxLength={20}
                  placeholder="e.g. KDY"
                  value={hubForm.code}
                  onChange={(event) => setHubForm({ ...hubForm, code: event.target.value.toUpperCase() })}
                  className="font-mono uppercase tracking-widest"
                />
                <p className="text-[11px] text-muted-foreground">Unique identifier. Cannot be changed later.</p>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="hub-name">Branch Name</Label>
                <Input
                  id="hub-name"
                  required
                  maxLength={100}
                  placeholder="e.g. Kandy Branch"
                  value={hubForm.name}
                  onChange={(event) => setHubForm({ ...hubForm, name: event.target.value })}
                />
                <p className="text-[11px] text-muted-foreground">Descriptive name for operational boards.</p>
              </div>
            </div>

            <label className="flex items-center gap-2 text-xs font-medium pt-1 cursor-pointer">
              <input
                type="checkbox"
                checked={hubForm.central}
                disabled={Boolean(central)}
                onChange={(event) => setHubForm({ ...hubForm, central: event.target.checked })}
                className="size-4 rounded border-input text-primary focus:ring-primary"
              />
              Central hub {central && <span className="text-muted-foreground">({central.name} already is)</span>}
            </label>

            <Button type="submit" disabled={savingHub || !hubForm.code || !hubForm.name || !hubForm.city}>
              {savingHub && <Loader2 className="animate-spin mr-2" aria-hidden="true" />} Add hub
            </Button>
          </form>
        </div>
      )}

      {/* ===================== TAB 2: DELIVERY ZONES ===================== */}
      {tab === "zones" && (
        <div className="space-y-6">
          <div className="grid gap-6 lg:grid-cols-3">
            {/* Zones List */}
            <div className="lg:col-span-2 space-y-4">
              <div className="rounded-2xl border border-border bg-card p-5 shadow-xs">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
                  <div>
                    <h3 className="font-semibold text-foreground">Configured Delivery Zones</h3>
                    <p className="text-xs text-muted-foreground">Service areas served by delivery dispatch branches.</p>
                  </div>
                  <span className="text-xs text-muted-foreground font-medium shrink-0">
                    {zoneSearch.trim()
                      ? `${zones ? zones.filter((zone) => {
                          const q = zoneSearch.trim().toLowerCase();
                          const assignedHub = zone.hubId
                            ? (hubs ?? []).find((h) => h.id === zone.hubId)
                            : servedZoneMap.get(zone.id);
                          return (
                            zone.code.toLowerCase().includes(q) ||
                            zone.name.toLowerCase().includes(q) ||
                            (assignedHub?.name.toLowerCase().includes(q) ?? false) ||
                            (assignedHub?.code.toLowerCase().includes(q) ?? false)
                          );
                        }).length : 0} of ${zoneList.length} zones`
                      : `${zoneList.length} total zones`}
                  </span>
                </div>

                {/* Professional Search Bar */}
                <div className="relative mb-3">
                  <Search
                    className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
                    aria-hidden="true"
                  />
                  <Input
                    type="search"
                    value={zoneSearch}
                    onChange={(e) => setZoneSearch(e.target.value)}
                    placeholder="Search by zone code, area name, or assigned branch..."
                    aria-label="Search delivery zones"
                    className="pl-9 pr-9 bg-background/70"
                  />
                  {zoneSearch && (
                    <button
                      type="button"
                      onClick={() => setZoneSearch("")}
                      aria-label="Clear zone search"
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
                    >
                      <X className="size-3.5" />
                    </button>
                  )}
                </div>

                {zones === null ? (
                  <div className="h-48 animate-pulse rounded-xl bg-muted" />
                ) : zones.length === 0 ? (
                  <p className="py-8 text-center text-sm text-muted-foreground">No zones configured yet.</p>
                ) : (() => {
                  const query = zoneSearch.trim().toLowerCase();
                  const filteredZones = query
                    ? zones.filter((zone) => {
                        const assignedHub = zone.hubId
                          ? (hubs ?? []).find((h) => h.id === zone.hubId)
                          : servedZoneMap.get(zone.id);
                        return (
                          zone.code.toLowerCase().includes(query) ||
                          zone.name.toLowerCase().includes(query) ||
                          (assignedHub?.name.toLowerCase().includes(query) ?? false) ||
                          (assignedHub?.code.toLowerCase().includes(query) ?? false)
                        );
                      })
                    : zones;

                  if (filteredZones.length === 0) {
                    return (
                      <div className="py-10 text-center space-y-2">
                        <p className="text-sm font-medium text-foreground">
                          No delivery zones match &ldquo;{zoneSearch.trim()}&rdquo;
                        </p>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => setZoneSearch("")}
                        >
                          Clear search
                        </Button>
                      </div>
                    );
                  }

                  return (
                  <div className="max-h-[780px] overflow-y-auto pr-1 divide-y divide-border">
                    {filteredZones.map((zone) => {
                      const assignedHub = zone.hubId
                        ? (hubs ?? []).find((h) => h.id === zone.hubId)
                        : servedZoneMap.get(zone.id);
                      const hasBranchHub = Boolean(assignedHub && assignedHub.active);

                      return (
                        <div key={zone.id} className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 py-3.5">
                          <div className="space-y-1">
                            <div className="flex items-center gap-2">
                              <span className="font-mono font-bold uppercase tracking-wider text-xs px-2 py-0.5 rounded bg-muted border border-border/60 text-foreground">
                                {zone.code}
                              </span>
                              <span className="font-medium text-foreground text-sm">{zone.name}</span>
                              <span
                                className={cn(
                                  "inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full border",
                                  zone.active
                                    ? "bg-emerald-500/15 text-emerald-700 border-emerald-500/25 dark:text-emerald-400"
                                    : "bg-amber-500/15 text-amber-700 border-amber-500/30 dark:bg-amber-500/20 dark:text-amber-300 dark:border-amber-500/30"
                                )}
                              >
                                <span
                                  className={cn(
                                    "size-1.5 rounded-full",
                                    zone.active ? "bg-emerald-500" : "bg-amber-500"
                                  )}
                                  aria-hidden="true"
                                />
                                {zone.active ? "Active" : "Inactive"}
                              </span>
                            </div>
                            <div className="flex items-center gap-1.5 text-xs">
                              {hasBranchHub ? (
                                <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-medium">
                                  <Warehouse className="size-3.5" /> Served by {assignedHub?.name} ({assignedHub?.code})
                                </span>
                              ) : (
                                <span className="inline-flex items-center gap-1 text-amber-600 dark:text-amber-400 font-medium">
                                  <AlertTriangle className="size-3.5" /> No active hub assigned
                                </span>
                              )}
                            </div>
                          </div>

                          <div className="flex items-center gap-1 self-end sm:self-auto shrink-0">
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon-sm"
                              title={zone.active ? "Deactivate Zone" : "Activate Zone"}
                              aria-label={zone.active ? "Deactivate Zone" : "Activate Zone"}
                              onClick={() => toggleZoneActive(zone)}
                              className={cn(
                                "size-7 rounded-lg transition-colors",
                                zone.active
                                  ? "text-emerald-600 hover:bg-amber-500/10 hover:text-amber-600"
                                  : "text-muted-foreground hover:bg-emerald-500/10 hover:text-emerald-600"
                              )}
                            >
                              <Power className="size-3.5" aria-hidden="true" />
                            </Button>
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon-sm"
                              title="Edit Zone"
                              aria-label="Edit Zone"
                              onClick={() => setEditingZone(zone)}
                              className="size-7 rounded-lg text-muted-foreground hover:bg-primary/10 hover:text-primary transition-colors"
                            >
                              <Edit2 className="size-3.5" aria-hidden="true" />
                            </Button>
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon-sm"
                              title="Delete Zone"
                              aria-label="Delete Zone"
                              onClick={(e) => {
                                e.stopPropagation();
                                setZoneToDelete(zone);
                              }}
                              className="size-7 rounded-lg text-muted-foreground hover:bg-destructive/10 hover:text-destructive transition-colors"
                            >
                              <Trash2 className="size-3.5" aria-hidden="true" />
                            </Button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                  );
                })()}
              </div>
            </div>

            {/* Add / Edit Zone Form */}
            <div className="space-y-4">
              <div className="rounded-2xl border border-border bg-card p-5 shadow-xs">
                <h3 className="font-semibold text-foreground mb-4 flex items-center gap-2">
                  {editingZone ? (
                    <>
                      <Edit2 className="size-4 text-primary" /> Edit Zone
                    </>
                  ) : (
                    <>
                      <Plus className="size-4 text-primary" /> Add New Zone
                    </>
                  )}
                </h3>

                {editingZone ? (
                  <form onSubmit={handleUpdateZone} className="space-y-4">
                    <div className="space-y-1.5">
                      <Label htmlFor="edit-zone-code">Zone Code</Label>
                      <Input
                        id="edit-zone-code"
                        required
                        maxLength={50}
                        value={editingZone.code}
                        onChange={(e) => setEditingZone({ ...editingZone, code: e.target.value.toUpperCase() })}
                        className="font-mono uppercase tracking-wider"
                      />
                    </div>
                    <div className="space-y-1.5">
                      <Label htmlFor="edit-zone-name">Zone Name</Label>
                      <Input
                        id="edit-zone-name"
                        required
                        maxLength={200}
                        value={editingZone.name}
                        onChange={(e) => setEditingZone({ ...editingZone, name: e.target.value })}
                      />
                    </div>
                    <div className="flex items-center gap-2 pt-1">
                      <input
                        type="checkbox"
                        id="edit-zone-active"
                        checked={editingZone.active}
                        onChange={(e) => setEditingZone({ ...editingZone, active: e.target.checked })}
                        className="rounded border-border text-primary focus:ring-primary"
                      />
                      <Label htmlFor="edit-zone-active" className="cursor-pointer text-xs">
                        Active for bookings
                      </Label>
                    </div>

                    <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2 pt-2">
                      <Button type="submit" disabled={savingZone} className="sm:flex-1">
                        {savingZone ? <Loader2 className="size-4 animate-spin mr-1.5" /> : null}
                        Save Changes
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => setEditingZone(null)}
                        className="shrink-0"
                      >
                        Cancel
                      </Button>
                    </div>
                  </form>
                ) : (
                  <form onSubmit={handleCreateZone} className="space-y-4">
                    {/* 1. Hub dropdown using ThemedSelect */}
                    <div className="space-y-1.5">
                      <Label htmlFor="zone-hub">Assigned Hub</Label>
                      <ThemedSelect
                        id="zone-hub"
                        value={selectedHubId}
                        onChange={handleHubChange}
                        options={hubOptions}
                        placeholder={hubOptions.length === 0 ? "No hubs available (add a hub first)" : "Select a hub..."}
                        disabled={hubOptions.length === 0}
                      />
                      <p className="text-[11px] text-muted-foreground">Select an existing hub this zone belongs to.</p>
                    </div>

                    {/* 2. Zone Name dropdown using ThemedSelect */}
                    <div className="space-y-1.5">
                      <Label htmlFor="zone-name-select">Zone Name / Area</Label>
                      <ThemedSelect
                        id="zone-name-select"
                        value={selectedSubZoneName}
                        onChange={handleSubZoneChange}
                        options={subZoneOptions}
                        placeholder={
                          !selectedHubId
                            ? "Select a hub first"
                            : subZoneOptions.length === 0
                            ? "No cataloged zones for this hub's district"
                            : "Select a zone / area..."
                        }
                        disabled={!selectedHubId || subZoneOptions.length === 0}
                      />
                      <p className="text-[11px] text-muted-foreground">
                        {selectedHub
                          ? `Cataloged zones for ${selectedHub.city}.`
                          : "Select a hub above to load regional zones."}
                      </p>
                    </div>

                    {/* 3. Zone Code (auto-filled) */}
                    <div className="space-y-1.5">
                      <Label htmlFor="zone-code">Zone Code</Label>
                      <Input
                        id="zone-code"
                        required
                        maxLength={50}
                        placeholder="e.g. CMB-MRD"
                        value={zoneForm.code}
                        onChange={(e) => setZoneForm({ ...zoneForm, code: e.target.value.toUpperCase() })}
                        className="font-mono uppercase tracking-wider"
                      />
                      <p className="text-[11px] text-muted-foreground">Unique identifier (auto-filled from selected zone).</p>
                    </div>

                    <Button
                      type="submit"
                      disabled={savingZone || !zoneForm.code || !zoneForm.name}
                      className="w-full"
                    >
                      {savingZone ? <Loader2 className="size-4 animate-spin mr-1.5" /> : <Plus className="size-4 mr-1.5" />}
                      Add Zone
                    </Button>
                  </form>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ===================== TAB 3: ISLAND COVERAGE MAP ===================== */}
      {tab === "coverage" && (
        <IslandCoverageMap
          hubs={hubs ?? []}
          zones={zoneList}
          districts={districts}
          coveragePercent={coveragePercent}
          loading={hubs === null || zones === null}
        />
      )}

      {/* Delete Hub Confirmation Dialog with StatCard */}
      <ConfirmationDialog
        open={hubToDelete !== null}
        onOpenChange={(open) => {
          if (!open) setHubToDelete(null);
        }}
        title="Delete Hub"
        description="Are you sure you want to permanently delete this hub? All associated operations and routing through this hub will be discontinued. This action cannot be undone."
        confirmLabel="Delete Hub"
        cancelLabel="Cancel"
        variant="destructive"
        loading={deletingHub}
        onConfirm={confirmDeleteHub}
        itemSummary={
          hubToDelete
            ? {
                title: hubToDelete.name,
                code: hubToDelete.code,
                badge: hubToDelete.central ? "Central Hub" : "Branch Hub",
                badgeVariant: hubToDelete.central ? "default" : "outline",
                icon: hubToDelete.central ? Warehouse : Building2,
                details: [
                  { label: "City / District", value: hubToDelete.city || "—" },
                  { label: "Status", value: hubToDelete.active ? "Active" : "Inactive" },
                  {
                    label: "Zones Served",
                    value: `${getAssignedZones(hubToDelete).length} zone(s) assigned`,
                  },
                ],
              }
            : undefined
        }
      />

      {/* Delete Zone Confirmation Modal with Statcard */}
      <ConfirmationDialog
        open={zoneToDelete !== null}
        onOpenChange={(open) => !open && setZoneToDelete(null)}
        title="Delete Delivery Zone"
        description="Are you sure you want to delete this delivery zone? Associated pricing rules will also be cleaned up automatically. This action cannot be undone."
        confirmLabel="Delete Zone"
        cancelLabel="Cancel"
        variant="destructive"
        loading={deletingZone}
        onConfirm={confirmDeleteZone}
        itemSummary={
          zoneToDelete
            ? {
                title: zoneToDelete.name,
                code: zoneToDelete.code,
                badge: zoneToDelete.active ? "Active" : "Inactive",
                badgeVariant: zoneToDelete.active ? "success" : "outline",
                icon: MapPin,
                details: [
                  { label: "Zone Code", value: zoneToDelete.code, badge: true },
                  {
                    label: "Serving Hub",
                    value: zoneToDelete.hubId
                      ? (hubs ?? []).find((h) => h.id === zoneToDelete.hubId)?.name || "Assigned Hub"
                      : servedZoneMap.get(zoneToDelete.id)?.name || "None",
                  },
                ],
              }
            : undefined
        }
      />
    </div>
  );
}

/** Searchable combobox dropdown for the 25 Sri Lankan administrative districts */
function DistrictSelect({
  selected,
  onSelect,
  districts,
  existingDistricts,
}: {
  selected: District | null;
  onSelect: (district: District) => void;
  districts: District[];
  existingDistricts: Set<string>;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");

  const filtered = useMemo(() => {
    if (!search.trim()) return districts;
    const q = search.toLowerCase();
    return districts.filter(
      (d) =>
        d.name.toLowerCase().includes(q) ||
        d.code.toLowerCase().includes(q) ||
        d.province.toLowerCase().includes(q)
    );
  }, [districts, search]);

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        className="flex h-9 w-full items-center justify-between rounded-lg border border-input bg-card px-2.5 text-xs text-foreground shadow-xs hover:bg-accent focus:outline-none focus:ring-1 focus:ring-ring"
      >
        {selected ? (
          <span className="flex items-center gap-1.5 font-medium truncate">
            <span>{selected.name}</span>
            <span className="text-[10px] text-muted-foreground font-mono">({selected.code})</span>
          </span>
        ) : (
          <span className="text-muted-foreground">Select a district...</span>
        )}
        <ChevronDown className="size-4 shrink-0 opacity-50" />
      </button>

      {open && (
        <div className="absolute z-50 mt-1 max-h-64 w-full min-w-[280px] overflow-auto rounded-xl border border-border bg-popover p-1 text-popover-foreground shadow-lg animate-in fade-in-0 zoom-in-95 duration-100">
          <div className="sticky top-0 bg-popover p-1 border-b border-border/50">
            <div className="flex items-center gap-1.5 rounded-lg border border-input px-2 py-1 bg-background">
              <Search className="size-3.5 text-muted-foreground shrink-0" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search district, code, province..."
                className="w-full bg-transparent text-xs outline-none placeholder:text-muted-foreground text-foreground"
                autoFocus
              />
            </div>
          </div>
          <div className="py-1">
            {filtered.length === 0 ? (
              <p className="p-3 text-center text-xs text-muted-foreground">No matching district found.</p>
            ) : (
              filtered.map((district) => {
                const isTaken =
                  existingDistricts.has(district.name.toLowerCase()) ||
                  existingDistricts.has(district.code.toLowerCase());
                return (
                  <button
                    key={district.code}
                    type="button"
                    disabled={isTaken}
                    aria-disabled={isTaken}
                    onClick={() => {
                      if (isTaken) return;
                      onSelect(district);
                      setOpen(false);
                      setSearch("");
                    }}
                    className={cn(
                      "flex w-full items-center justify-between rounded-lg px-2.5 py-1.5 text-xs text-left transition-colors",
                      isTaken
                        ? "opacity-45 cursor-not-allowed bg-muted/20 select-none"
                        : selected?.code === district.code
                        ? "bg-accent font-medium text-accent-foreground"
                        : "hover:bg-accent hover:text-accent-foreground"
                    )}
                  >
                    <div className="flex flex-col">
                      <div className="flex items-center gap-1.5">
                        <span className={cn("font-semibold", isTaken ? "text-muted-foreground" : "text-foreground")}>
                          {district.name}
                        </span>
                        <span className="font-mono text-[10px] text-muted-foreground">({district.code})</span>
                        {isTaken && (
                          <span className="text-[10px] bg-muted text-muted-foreground px-1.5 py-0.5 rounded border border-border/40 font-medium">
                            Hub exists
                          </span>
                        )}
                      </div>
                      <span className="text-[10px] text-muted-foreground">
                        {district.province} Province &middot; Postal: {district.postalCode}
                      </span>
                    </div>
                    {!isTaken && selected?.code === district.code && <Check className="size-3.5 text-primary" />}
                  </button>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function HubCard({
  hub,
  zone,
  assignedZones,
  busy,
  onToggle,
  onDelete,
  zones,
  takenZones,
  onSave,
  hasOtherCentralHub,
}: {
  hub: Hub;
  zone: string | undefined;
  assignedZones: ZoneAdmin[];
  busy: boolean;
  onToggle: (hub: Hub) => void;
  onDelete: (hub: Hub) => void;
  zones: ZoneAdmin[];
  /** Zones that already have a hub, active or not: the database allows one hub per zone. */
  takenZones: Set<string | null>;
  onSave: (hub: Hub, changes: { name: string; city: string; zoneId: string | null; central?: boolean }) => Promise<boolean>;
  hasOtherCentralHub: boolean;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({
    name: hub.name,
    city: hub.city,
    zoneId: hub.zoneId ?? "",
    central: hub.central,
  });
  const Icon = hub.central ? Warehouse : Building2;
  const choices = zones.filter((option) => option.id === hub.zoneId || !takenZones.has(option.id));

  const zoneOptions = useMemo(() => {
    const list = [
      {
        value: "",
        label: draft.central ? "None (central hub only)" : "Choose a zone...",
      },
    ];
    choices.forEach((opt) => {
      list.push({
        value: opt.id,
        label: opt.name,
      });
    });
    return list;
  }, [choices, draft.central]);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (
      await onSave(hub, {
        name: draft.name,
        city: draft.city,
        zoneId: draft.zoneId || null,
        central: draft.central,
      })
    ) {
      setEditing(false);
    }
  }

  return (
    <article className={cn(
      "rounded-2xl border p-4 shadow-xs transition-shadow hover:shadow-sm",
      hub.central ? "border-primary bg-accent/60" : "border-border bg-card",
      !hub.active && "opacity-60",
    )}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <span className={cn("flex size-10 items-center justify-center rounded-xl",
            hub.central ? "bg-primary text-primary-foreground shadow-xs" : "bg-primary/10 text-primary")}>
            <Icon className="size-5" aria-hidden="true" />
          </span>
          <div>
            <p className="flex items-center gap-1.5 font-bold text-foreground">
              {hub.name} {hub.central && <Star className="size-3.5 fill-primary text-primary" aria-label="Central hub" />}
            </p>
            <p className="font-mono text-[11px] tracking-[0.15em] text-primary font-semibold">{hub.code}</p>
            <p className="text-xs text-muted-foreground">
              {hub.city}{zone ? ` · primary zone: ${zone}` : hub.central ? " · central only" : ""}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-1 self-start shrink-0">
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            disabled={busy}
            title={hub.active ? "Deactivate Hub" : "Activate Hub"}
            aria-label={hub.active ? "Deactivate Hub" : "Activate Hub"}
            onClick={() => onToggle(hub)}
            className={cn(
              "size-7 rounded-lg transition-colors",
              hub.active
                ? "text-emerald-600 hover:bg-amber-500/10 hover:text-amber-600"
                : "text-muted-foreground hover:bg-emerald-500/10 hover:text-emerald-600"
            )}
          >
            {busy ? (
              <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
            ) : (
              <Power className="size-3.5" aria-hidden="true" />
            )}
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            disabled={busy}
            title="Edit Hub"
            aria-label="Edit Hub"
            onClick={() => {
              setDraft({
                name: hub.name,
                city: hub.city,
                zoneId: hub.zoneId ?? "",
                central: hub.central,
              });
              setEditing((open) => !open);
            }}
            className="size-7 rounded-lg text-muted-foreground hover:bg-primary/10 hover:text-primary transition-colors"
          >
            <Pencil className="size-3.5" aria-hidden="true" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            disabled={busy}
            title="Delete Hub"
            aria-label="Delete Hub"
            onClick={(e) => {
              e.stopPropagation();
              onDelete(hub);
            }}
            className="size-7 rounded-lg text-muted-foreground hover:bg-destructive/10 hover:text-destructive transition-colors"
          >
            <Trash2 className="size-3.5" aria-hidden="true" />
          </Button>
        </div>
      </div>

      {/* Assigned Delivery Zones */}
      <div className="mt-3 border-t border-border/40 pt-2.5">
        <div className="flex items-center justify-between mb-1.5">
          <span className="text-[11px] font-semibold text-foreground flex items-center gap-1.5">
            <MapPin className="size-3 text-primary" aria-hidden="true" />
            Zones Served ({assignedZones.length})
          </span>
        </div>
        {assignedZones.length === 0 ? (
          <p className="text-[11px] text-muted-foreground italic">
            No delivery zones assigned to this hub yet.
          </p>
        ) : (
          <div className="flex flex-wrap gap-1 max-h-36 overflow-y-auto pr-0.5 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
            {assignedZones.map((z) => (
              <span
                key={z.id}
                className="inline-flex items-center gap-1 text-[10px] rounded-md bg-muted px-2 py-0.5 border border-border/50 text-foreground"
              >
                <span className="font-mono text-[9px] text-primary font-bold">{z.code}</span>
                <span className="truncate max-w-[120px]">{z.name}</span>
                {!z.active && <span className="text-[9px] font-semibold text-amber-600 dark:text-amber-400">· Inactive</span>}
              </span>
            ))}
          </div>
        )}
      </div>

      {editing && (
        <form onSubmit={save} className="mt-4 space-y-3 border-t border-dashed border-border pt-4">
          <div className="space-y-1">
            <Label htmlFor={`edit-name-${hub.id}`}>Name</Label>
            <Input id={`edit-name-${hub.id}`} required maxLength={100} value={draft.name}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
          </div>
          <div className="space-y-1">
            <Label htmlFor={`edit-city-${hub.id}`}>City / District</Label>
            <Input id={`edit-city-${hub.id}`} required maxLength={100} value={draft.city}
              onChange={(event) => setDraft({ ...draft, city: event.target.value })} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor={`edit-zone-${hub.id}`}>Primary Serving Zone</Label>
            <ThemedSelect
              id={`edit-zone-${hub.id}`}
              value={draft.zoneId}
              onChange={(val) => setDraft({ ...draft, zoneId: val })}
              options={zoneOptions}
              placeholder={draft.central ? "None (central hub only)" : "Choose a zone"}
            />
            <p className="text-[11px] text-muted-foreground">The code {hub.code} cannot be changed.</p>
          </div>

          {/* Promote Sub Hub to Main Central Hub */}
          <div className="rounded-xl border p-3 space-y-1.5 transition-colors bg-muted/40 border-border/80">
            <label className="flex items-center gap-2.5 text-xs font-semibold cursor-pointer text-foreground">
              <input
                type="checkbox"
                checked={draft.central}
                disabled={hasOtherCentralHub && !hub.central}
                onChange={(event) => setDraft({ ...draft, central: event.target.checked })}
                className="size-4 rounded border-input text-primary focus:ring-primary"
              />
              <span className="flex items-center gap-1.5">
                <Star className={cn("size-3.5", draft.central ? "fill-primary text-primary" : "text-muted-foreground")} />
                Designate as Main Central Hub
              </span>
            </label>
            {hasOtherCentralHub && !hub.central ? (
              <p className="text-[11px] text-muted-foreground pl-6">
                Another hub is already active as the Main Central Hub. Delete or change that central hub first to designate this branch.
              </p>
            ) : !hasOtherCentralHub && !hub.central ? (
              <p className="text-[11px] text-amber-600 dark:text-amber-400 font-medium pl-6">
                ★ No active central hub exists! Check this box to promote this sub-hub to become the Main Central Hub.
              </p>
            ) : (
              <p className="text-[11px] text-muted-foreground pl-6">
                This hub operates as the Main Central Hub for parcel transits across all branches.
              </p>
            )}
          </div>

          <div className="flex gap-2">
            <Button type="submit" size="sm" disabled={busy || (!draft.central && !draft.zoneId)}>
              {busy && <Loader2 className="animate-spin" aria-hidden="true" />} Save
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={() => setEditing(false)}>Cancel</Button>
          </div>
        </form>
      )}
    </article>
  );
}
