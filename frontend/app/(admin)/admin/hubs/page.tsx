"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import {
  AlertTriangle,
  Building2,
  Check,
  ChevronDown,
  Loader2,
  MapPin,
  Pencil,
  Plus,
  Search,
  Star,
  Trash2,
  Warehouse,
} from "lucide-react";
import { cn } from "cn";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ConfirmationDialog } from "@/components/ui/confirmation-dialog";
import { formatApiError } from "@/lib/api";
import { createHub, deleteHub, listHubs, updateHub, type Hub } from "@/lib/hubs";
import { listDistricts, type District } from "@/lib/districts";
import { createZone, listAdminZones, type ZoneAdmin } from "@/lib/pricing";

const EMPTY_FORM = { code: "", name: "", city: "", zoneId: "", central: false };

/**
 * The hub network (ADR 0009): a branch per district/zone and one central hub.
 */
export default function AdminHubsPage() {
  const [hubs, setHubs] = useState<Hub[] | null>(null);
  const [zones, setZones] = useState<ZoneAdmin[]>([]);
  const [districts, setDistricts] = useState<District[]>([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [selectedDistrict, setSelectedDistrict] = useState<District | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [busyHub, setBusyHub] = useState<string | null>(null);
  const [hubToDelete, setHubToDelete] = useState<Hub | null>(null);
  const [deletingHub, setDeletingHub] = useState(false);

  const load = useCallback(() => {
    Promise.all([listHubs(), listAdminZones(), listDistricts()])
      .then(([loadedHubs, loadedZones, loadedDistricts]) => {
        setHubs(loadedHubs);
        setZones(loadedZones);
        setDistricts(loadedDistricts);
      })
      .catch((failure) => setError(formatApiError(failure)));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const zoneName = (zoneId: string | null) => zones.find((zone) => zone.id === zoneId)?.name;
  const servedZoneIds = useMemo(() => {
    const set = new Set<string>();
    (hubs ?? []).filter((h) => h.active).forEach((h) => {
      if (h.zoneId) set.add(h.zoneId);
    });
    zones.forEach((z) => {
      if (z.hubId && hubs?.some((h) => h.active && h.id === z.hubId)) {
        set.add(z.id);
      }
    });
    return set;
  }, [hubs, zones]);

  const unserved = zones.filter((zone) => !servedZoneIds.has(zone.id));
  const takenZones = new Set((hubs ?? []).map((hub) => hub.zoneId));
  const central = hubs?.find((hub) => hub.central && hub.active);
  const branches = (hubs ?? []).filter((hub) => hub !== central);

  const getAssignedZones = useCallback(
    (hub: Hub) => zones.filter((z) => z.hubId === hub.id || z.id === hub.zoneId),
    [zones],
  );

  const existingDistrictKeys = useMemo(() => {
    const set = new Set<string>();
    for (const h of hubs ?? []) {
      if (h.city) set.add(h.city.trim().toLowerCase());
      if (h.code) set.add(h.code.trim().toLowerCase());
    }
    return set;
  }, [hubs]);

  function handleDistrictSelect(district: District) {
    if (existingDistrictKeys.has(district.name.toLowerCase()) || existingDistrictKeys.has(district.code.toLowerCase())) {
      return;
    }
    setSelectedDistrict(district);
    const matchingZone = zones.find(
      (z) => z.name.toLowerCase() === district.name.toLowerCase() || z.code.toUpperCase() === district.code.toUpperCase()
    );
    setForm((prev) => ({
      ...prev,
      city: district.name,
      code: district.code,
      name: district.defaultBranchName,
      zoneId: matchingZone ? matchingZone.id : "",
    }));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (existingDistrictKeys.has(form.city.toLowerCase()) || existingDistrictKeys.has(form.code.toLowerCase())) {
      setError(`A hub for ${form.city} (${form.code}) already exists.`);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      let targetZoneId = form.zoneId;
      if (!form.central && !targetZoneId) {
        const match = zones.find(
          (z) => z.name.toLowerCase() === form.city.toLowerCase() || z.code.toUpperCase() === form.code.toUpperCase()
        );
        if (match) {
          targetZoneId = match.id;
        } else {
          // Automatically create the zone for this district
          const created = await createZone({ code: form.code, name: form.city });
          targetZoneId = created.id;
        }
      }
      await createHub({ ...form, zoneId: targetZoneId || null });
      setForm(EMPTY_FORM);
      setSelectedDistrict(null);
      load();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setSaving(false);
    }
  }

  /** Changes name, city, zone, and central flag; the code stays as it is. */
  async function saveHub(
    hub: Hub,
    changes: { name: string; city: string; zoneId: string | null; central?: boolean },
  ) {
    setBusyHub(hub.id);
    setError(null);
    try {
      await updateHub(hub.id, {
        ...hub,
        ...changes,
        central: changes.central ?? hub.central,
      });
      load();
      return true;
    } catch (failure) {
      setError(formatApiError(failure));
      return false;
    } finally {
      setBusyHub(null);
    }
  }

  async function toggleActive(hub: Hub) {
    setBusyHub(hub.id);
    setError(null);
    try {
      await updateHub(hub.id, { ...hub, active: !hub.active });
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
    try {
      await deleteHub(hubToDelete.id);
      setHubs((prev) => (prev ? prev.filter((h) => h.id !== hubToDelete.id) : prev));
      setHubToDelete(null);
      load();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setDeletingHub(false);
    }
  }

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <h2 className="text-xl font-bold text-foreground">Hub network</h2>
        <p className="text-sm text-muted-foreground">
          Parcels travel origin branch &rarr; central hub &rarr; destination branch. Every zone needs an active branch
          before parcels can be booked for it.
        </p>
      </header>

      {error && <p role="alert" className="rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{error}</p>}

      {hubs !== null && unserved.length > 0 && (
        <p className="flex items-start gap-2 rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>
            <strong>Not served yet:</strong> {unserved.map((zone) => zone.name).join(", ")}. Parcels to or from these
            zones are refused until each has an active branch hub.
          </span>
        </p>
      )}
      {hubs !== null && !central && hubs.length > 0 && (
        <p className="flex items-start gap-2 rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          No active central hub: parcels between two different branches cannot be booked.
        </p>
      )}

      {/* The network: central hub in the middle, branches around it */}
      <section aria-label="Hubs" className="rounded-3xl border border-border bg-card p-5">
        {hubs === null ? (
          <div className="h-40 animate-pulse rounded-2xl bg-muted" aria-hidden="true" />
        ) : hubs.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">No hubs yet. Start with the central hub.</p>
        ) : (
          <div className="flex flex-col items-center gap-6">
            {central && (
              <HubCard
                hub={central}
                zone={zoneName(central.zoneId)}
                assignedZones={getAssignedZones(central)}
                busy={busyHub === central.id}
                onToggle={toggleActive}
                onDelete={setHubToDelete}
                zones={zones}
                takenZones={takenZones}
                onSave={saveHub}
                hasOtherCentralHub={false}
              />
            )}
            {branches.length > 0 && (
              <>
                {central && <span aria-hidden="true" className="h-6 border-l-2 border-dashed border-primary/40" />}
                <ul className="grid w-full gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {branches.map((hub) => (
                    <li key={hub.id}>
                      <HubCard
                        hub={hub}
                        zone={zoneName(hub.zoneId)}
                        assignedZones={getAssignedZones(hub)}
                        busy={busyHub === hub.id}
                        onToggle={toggleActive}
                        onDelete={setHubToDelete}
                        zones={zones}
                        takenZones={takenZones}
                        onSave={saveHub}
                        hasOtherCentralHub={Boolean(central && central.id !== hub.id)}
                      />
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>
        )}
      </section>

      {/* Add a Hub Form */}
      <form onSubmit={submit} className="space-y-4 rounded-3xl border border-border bg-card p-5">
        <h3 className="flex items-center gap-2 font-bold text-foreground">
          <Plus className="size-4 text-primary" aria-hidden="true" /> Add a hub
        </h3>

        <div className="grid gap-3 sm:grid-cols-3">
          {/* Field 1: District (Searchable Dropdown) */}
          <div className="space-y-1">
            <Label htmlFor="hub-district">District</Label>
            <DistrictSelect
              selected={selectedDistrict}
              onSelect={handleDistrictSelect}
              districts={districts}
              existingDistricts={existingDistrictKeys}
            />
            <p className="text-xs text-muted-foreground">Select from the 25 administrative districts.</p>
          </div>

          {/* Field 2: Code (Auto-filled) */}
          <div className="space-y-1">
            <Label htmlFor="hub-code">Code</Label>
            <Input
              id="hub-code"
              required
              maxLength={20}
              placeholder="e.g. KDY"
              value={form.code}
              onChange={(event) => setForm({ ...form, code: event.target.value.toUpperCase() })}
              className="font-mono uppercase tracking-widest"
            />
            <p className="text-xs text-muted-foreground">Letters, digits or hyphens. Cannot be changed later.</p>
          </div>

          {/* Field 3: Branch Name (Auto-filled) */}
          <div className="space-y-1">
            <Label htmlFor="hub-name">Branch Name</Label>
            <Input
              id="hub-name"
              required
              maxLength={100}
              placeholder="e.g. Kandy Branch"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
            />
            <p className="text-xs text-muted-foreground">Auto-generated branch name.</p>
          </div>
        </div>

        <label className="flex items-center gap-2 text-sm pt-1">
          <input
            type="checkbox"
            checked={form.central}
            disabled={Boolean(central)}
            onChange={(event) => setForm({ ...form, central: event.target.checked })}
          />
          Central hub {central && <span className="text-muted-foreground">({central.name} already is)</span>}
        </label>

        <Button type="submit" disabled={saving || !form.code || !form.name || !form.city}>
          {saving && <Loader2 className="animate-spin mr-2" aria-hidden="true" />} Add hub
        </Button>
      </form>

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
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return districts;
    return districts.filter(
      (d) =>
        d.name.toLowerCase().includes(q) ||
        d.code.toLowerCase().includes(q) ||
        d.province.toLowerCase().includes(q) ||
        d.postalCode.includes(q)
    );
  }, [search, districts]);

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        id="hub-district"
        onClick={() => setOpen(!open)}
        className="flex h-9 w-full items-center justify-between rounded-lg border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors hover:bg-muted/50 focus:outline-none focus:ring-1 focus:ring-ring"
      >
        {selected ? (
          <span className="flex items-center gap-2 truncate">
            <span className="font-semibold text-foreground">{selected.name}</span>
            <span className="font-mono text-xs text-muted-foreground">[{selected.code}]</span>
            <span className="text-xs text-muted-foreground">({selected.province} Prov.)</span>
          </span>
        ) : (
          <span className="text-muted-foreground">Select a district...</span>
        )}
        <ChevronDown className="size-4 shrink-0 opacity-50" />
      </button>

      {open && (
        <div className="absolute z-50 mt-1 max-h-64 w-full min-w-[280px] overflow-auto rounded-xl border border-border bg-popover p-1 text-popover-foreground shadow-lg">
          <div className="sticky top-0 bg-popover p-1 border-b border-border/50">
            <div className="flex items-center gap-1.5 rounded-lg border border-input px-2 py-1 bg-background">
              <Search className="size-3.5 text-muted-foreground shrink-0" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search district, code, province..."
                className="w-full bg-transparent text-xs outline-none placeholder:text-muted-foreground"
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
  // Its own zone, plus the zones nobody has
  const choices = zones.filter((option) => option.id === hub.zoneId || !takenZones.has(option.id));

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
      "rounded-2xl border p-4",
      hub.central ? "border-primary bg-accent" : "border-border bg-background",
      !hub.active && "opacity-60",
    )}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <span className={cn("flex size-10 items-center justify-center rounded-xl",
            hub.central ? "bg-primary text-primary-foreground" : "bg-accent text-primary")}>
            <Icon className="size-5" aria-hidden="true" />
          </span>
          <div>
            <p className="flex items-center gap-1.5 font-bold text-foreground">
              {hub.name} {hub.central && <Star className="size-3.5 fill-primary text-primary" aria-label="Central hub" />}
            </p>
            <p className="font-mono text-[11px] tracking-[0.15em] text-primary">{hub.code}</p>
            <p className="text-xs text-muted-foreground">
              {hub.city}{zone ? ` · primary zone: ${zone}` : hub.central ? " · central only" : ""}
            </p>
          </div>
        </div>
        <div className="flex flex-col gap-1.5">
          <Button
            variant="outline"
            size="sm"
            disabled={busy}
            onClick={() => {
              setDraft({
                name: hub.name,
                city: hub.city,
                zoneId: hub.zoneId ?? "",
                central: hub.central,
              });
              setEditing((open) => !open);
            }}
          >
            <Pencil aria-hidden="true" /> Edit
          </Button>
          <Button variant="outline" size="sm" disabled={busy} onClick={() => onToggle(hub)}>
            {busy && <Loader2 className="animate-spin" aria-hidden="true" />}
            {hub.active ? "Deactivate" : "Activate"}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={busy}
            onClick={(e) => {
              e.stopPropagation();
              onDelete(hub);
            }}
            className="text-destructive hover:bg-destructive/10 hover:text-destructive border-destructive/30"
          >
            <Trash2 className="size-3.5" aria-hidden="true" /> Delete
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
          <div className="flex flex-wrap gap-1 max-h-24 overflow-y-auto pr-0.5">
            {assignedZones.map((z) => (
              <span
                key={z.id}
                className="inline-flex items-center gap-1 text-[10px] rounded-md bg-muted px-2 py-0.5 border border-border/50 text-foreground"
              >
                <span className="font-mono text-[9px] text-primary font-bold">{z.code}</span>
                <span className="truncate max-w-[120px]">{z.name}</span>
                {!z.active && <span className="text-[8px] text-muted-foreground">(Inactive)</span>}
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
          <div className="space-y-1">
            <Label htmlFor={`edit-zone-${hub.id}`}>Zone it serves</Label>
            <select id={`edit-zone-${hub.id}`} value={draft.zoneId}
              onChange={(event) => setDraft({ ...draft, zoneId: event.target.value })}
              className="h-9 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm">
              <option value="">{draft.central ? "None (central hub only)" : "Choose a zone"}</option>
              {choices.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
            </select>
            <p className="text-xs text-muted-foreground">The code {hub.code} cannot be changed.</p>
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
