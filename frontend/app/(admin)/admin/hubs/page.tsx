"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { AlertTriangle, Building2, Loader2, Pencil, Plus, Star, Warehouse } from "lucide-react";
import { cn } from "cn";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { formatApiError } from "@/lib/api";
import { listZones, type Zone } from "@/lib/delivery";
import { createHub, listHubs, updateHub, type Hub } from "@/lib/hubs";

const EMPTY_FORM = { code: "", name: "", city: "", zoneId: "", central: false };

/**
 * The hub network (ADR 0009): a branch per zone and one central hub. Zones without an active hub
 * cannot be booked, so they are flagged at the top.
 */
export default function AdminHubsPage() {
  const [hubs, setHubs] = useState<Hub[] | null>(null);
  const [zones, setZones] = useState<Zone[]>([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [busyHub, setBusyHub] = useState<string | null>(null);

  const load = useCallback(() => {
    Promise.all([listHubs(), listZones()])
      .then(([loadedHubs, loadedZones]) => {
        setHubs(loadedHubs);
        setZones(loadedZones);
      })
      .catch((failure) => setError(formatApiError(failure)));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const zoneName = (zoneId: string | null) => zones.find((zone) => zone.id === zoneId)?.name;
  const servedZones = useMemo(
    () => new Set((hubs ?? []).filter((hub) => hub.active && hub.zoneId).map((hub) => hub.zoneId)),
    [hubs],
  );
  const unserved = zones.filter((zone) => !servedZones.has(zone.id));
  const takenZones = new Set((hubs ?? []).map((hub) => hub.zoneId));
  const freeZones = zones.filter((zone) => !takenZones.has(zone.id));
  const central = hubs?.find((hub) => hub.central && hub.active);
  const branches = (hubs ?? []).filter((hub) => hub !== central);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await createHub({ ...form, zoneId: form.zoneId || null });
      setForm(EMPTY_FORM);
      load();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setSaving(false);
    }
  }

  /** Changes name, city and zone; the code and the central flag stay as they are. */
  async function saveHub(hub: Hub, changes: { name: string; city: string; zoneId: string | null }) {
    setBusyHub(hub.id);
    setError(null);
    try {
      await updateHub(hub.id, { ...hub, ...changes });
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

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <h2 className="text-xl font-bold text-foreground">Hub network</h2>
        <p className="text-sm text-muted-foreground">
          Parcels travel origin branch → central hub → destination branch. Every zone needs an active branch
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
            {central && <HubCard hub={central} zone={zoneName(central.zoneId)} busy={busyHub === central.id} onToggle={toggleActive}
              zones={zones} takenZones={takenZones} onSave={saveHub} />}
            {branches.length > 0 && (
              <>
                {central && <span aria-hidden="true" className="h-6 border-l-2 border-dashed border-primary/40" />}
                <ul className="grid w-full gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {branches.map((hub) => (
                    <li key={hub.id}>
                      <HubCard hub={hub} zone={zoneName(hub.zoneId)} busy={busyHub === hub.id} onToggle={toggleActive}
                        zones={zones} takenZones={takenZones} onSave={saveHub} />
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>
        )}
      </section>

      <form onSubmit={submit} className="space-y-4 rounded-3xl border border-border bg-card p-5">
        <h3 className="flex items-center gap-2 font-bold text-foreground">
          <Plus className="size-4 text-primary" aria-hidden="true" /> Add a hub
        </h3>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <Label htmlFor="hub-code">Code</Label>
            <Input id="hub-code" required maxLength={20} placeholder="KDY" value={form.code}
              onChange={(event) => setForm({ ...form, code: event.target.value.toUpperCase() })}
              className="font-mono uppercase tracking-widest" />
            <p className="text-xs text-muted-foreground">Letters, digits or hyphens. Cannot be changed later.</p>
          </div>
          <div className="space-y-1">
            <Label htmlFor="hub-name">Name</Label>
            <Input id="hub-name" required maxLength={100} placeholder="Kandy branch" value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="hub-city">City</Label>
            <Input id="hub-city" required maxLength={100} placeholder="Kandy" value={form.city}
              onChange={(event) => setForm({ ...form, city: event.target.value })} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="hub-zone">Zone it serves</Label>
            <select id="hub-zone" value={form.zoneId} onChange={(event) => setForm({ ...form, zoneId: event.target.value })}
              className="h-9 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm">
              <option value="">{form.central ? "None (central hub only)" : "Choose a zone"}</option>
              {freeZones.map((zone) => <option key={zone.id} value={zone.id}>{zone.name}</option>)}
            </select>
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={form.central} disabled={Boolean(central)}
            onChange={(event) => setForm({ ...form, central: event.target.checked })} />
          Central hub {central && <span className="text-muted-foreground">({central.name} already is)</span>}
        </label>
        <Button type="submit" disabled={saving || (!form.central && !form.zoneId)}>
          {saving && <Loader2 className="animate-spin" aria-hidden="true" />} Add hub
        </Button>
      </form>
    </div>
  );
}

function HubCard({ hub, zone, busy, onToggle, zones, takenZones, onSave }: {
  hub: Hub;
  zone: string | undefined;
  busy: boolean;
  onToggle: (hub: Hub) => void;
  zones: Zone[];
  /** Zones that already have a hub, active or not: the database allows one hub per zone. */
  takenZones: Set<string | null>;
  onSave: (hub: Hub, changes: { name: string; city: string; zoneId: string | null }) => Promise<boolean>;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({ name: hub.name, city: hub.city, zoneId: hub.zoneId ?? "" });
  const Icon = hub.central ? Warehouse : Building2;
  // Its own zone, plus the zones nobody has
  const choices = zones.filter((option) => option.id === hub.zoneId || !takenZones.has(option.id));

  async function save(event: FormEvent) {
    event.preventDefault();
    if (await onSave(hub, { name: draft.name, city: draft.city, zoneId: draft.zoneId || null })) {
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
              {hub.city}{zone ? ` · serves ${zone}` : hub.central ? " · central only" : ""}
            </p>
          </div>
        </div>
        <div className="flex flex-col gap-1.5">
          <Button variant="outline" size="sm" disabled={busy} onClick={() => setEditing((open) => !open)}>
            <Pencil aria-hidden="true" /> Edit
          </Button>
          <Button variant="outline" size="sm" disabled={busy} onClick={() => onToggle(hub)}>
            {busy && <Loader2 className="animate-spin" aria-hidden="true" />}
            {hub.active ? "Deactivate" : "Activate"}
          </Button>
        </div>
      </div>

      {editing && (
        <form onSubmit={save} className="mt-4 space-y-3 border-t border-dashed border-border pt-4">
          <div className="space-y-1">
            <Label htmlFor={`edit-name-${hub.id}`}>Name</Label>
            <Input id={`edit-name-${hub.id}`} required maxLength={100} value={draft.name}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
          </div>
          <div className="space-y-1">
            <Label htmlFor={`edit-city-${hub.id}`}>City</Label>
            <Input id={`edit-city-${hub.id}`} required maxLength={100} value={draft.city}
              onChange={(event) => setDraft({ ...draft, city: event.target.value })} />
          </div>
          <div className="space-y-1">
            <Label htmlFor={`edit-zone-${hub.id}`}>Zone it serves</Label>
            <select id={`edit-zone-${hub.id}`} value={draft.zoneId}
              onChange={(event) => setDraft({ ...draft, zoneId: event.target.value })}
              className="h-9 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm">
              <option value="">{hub.central ? "None (central hub only)" : "Choose a zone"}</option>
              {choices.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
            </select>
            <p className="text-xs text-muted-foreground">The code {hub.code} cannot be changed.</p>
          </div>
          <div className="flex gap-2">
            <Button type="submit" size="sm" disabled={busy || (!hub.central && !draft.zoneId)}>
              {busy && <Loader2 className="animate-spin" aria-hidden="true" />} Save
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={() => setEditing(false)}>Cancel</Button>
          </div>
        </form>
      )}
    </article>
  );
}
