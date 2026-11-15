"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { AlertTriangle, ArrowLeftRight, Loader2, Plus, Route, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { ThemedSelect } from "@/components/ui/select";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { ConfirmationDialog } from "@/components/ui/confirmation-dialog";
import { formatApiError } from "@/lib/api";
import { createHubLink, deleteHubLink, listHubLinks, type Hub, type HubLink } from "@/lib/hubs";

/**
 * Direct links between branch hubs (ADR 0011). Parcels between two linked branches travel
 * directly; every other pair of branches still goes through the central hub. The backend
 * enforces the rules; this card only offers hubs that can be linked.
 */
export function HubLinksCard({ hubs }: { hubs: Hub[] }) {
  const [links, setLinks] = useState<HubLink[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [hubId, setHubId] = useState("");
  const [otherHubId, setOtherHubId] = useState("");
  const [confirmAdd, setConfirmAdd] = useState(false);
  const [linkToRemove, setLinkToRemove] = useState<HubLink | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    listHubLinks()
      .then((loaded) => {
        if (!cancelled) setLinks(loaded);
      })
      .catch((e) => {
        if (!cancelled) {
          setLinks([]);
          setError(formatApiError(e));
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const hubById = useMemo(() => new Map(hubs.map((hub) => [hub.id, hub])), [hubs]);
  const central = hubs.find((hub) => hub.central && hub.active);

  // Only active branches can be linked; the central hub already reaches every branch directly
  const linkable = useMemo(
    () => hubs.filter((hub) => hub.active && !hub.central).sort((a, b) => a.name.localeCompare(b.name)),
    [hubs],
  );

  const isLinked = (a: string, b: string) =>
    (links ?? []).some(
      (link) => (link.hubId === a && link.otherHubId === b) || (link.hubId === b && link.otherHubId === a),
    );

  const firstOptions = linkable.map((hub) => ({ value: hub.id, label: hub.name, description: hub.city }));
  const secondOptions = linkable
    .filter((hub) => hub.id !== hubId)
    .map((hub) => ({
      value: hub.id,
      label: hub.name,
      description: hub.city,
      badge: hubId && isLinked(hubId, hub.id) ? "Linked" : undefined,
      disabled: Boolean(hubId) && isLinked(hubId, hub.id),
    }));

  const hubName = (id: string) => hubById.get(id)?.name ?? "Unknown hub";
  const canAdd = Boolean(hubId && otherHubId && hubId !== otherHubId && !isLinked(hubId, otherHubId));

  function askToAdd(event: FormEvent) {
    event.preventDefault();
    if (canAdd) {
      setError(null);
      setConfirmAdd(true);
    }
  }

  async function addLink() {
    setBusy(true);
    try {
      const created = await createHubLink(hubId, otherHubId);
      setLinks((current) => [...(current ?? []), created]);
      setHubId("");
      setOtherHubId("");
    } catch (e) {
      setError(formatApiError(e));
    } finally {
      setBusy(false);
      setConfirmAdd(false);
    }
  }

  async function removeLink() {
    if (!linkToRemove) return;
    const target = linkToRemove;
    setBusy(true);
    try {
      await deleteHubLink(target.hubId, target.otherHubId);
      setLinks((current) =>
        (current ?? []).filter(
          (link) => !(link.hubId === target.hubId && link.otherHubId === target.otherHubId),
        ),
      );
    } catch (e) {
      setError(formatApiError(e));
    } finally {
      setBusy(false);
      setLinkToRemove(null);
    }
  }

  return (
    <section aria-label="Direct links" className="rounded-2xl border border-border bg-card p-5 shadow-xs">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h3 className="flex items-center gap-2 font-bold text-foreground">
            <Route className="size-4 text-primary" aria-hidden="true" /> Direct links between branches
          </h3>
          <p className="max-w-2xl text-sm text-muted-foreground">
            Parcels between two linked branches travel directly. Every other pair of branches goes through{" "}
            {central ? central.name : "the central hub"}.
          </p>
        </div>
        {links !== null && (
          <span className="rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">
            {links.length} {links.length === 1 ? "link" : "links"}
          </span>
        )}
      </div>

      {error && (
        <Alert variant="destructive" className="mt-4">
          <AlertTriangle aria-hidden="true" />
          <AlertTitle>Direct links</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="mt-4">
        {links === null ? (
          <div className="h-16 animate-pulse rounded-xl bg-muted" aria-hidden="true" />
        ) : links.length === 0 ? (
          <p className="rounded-xl border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">
            No direct links yet. Link two nearby branches so their parcels skip the central hub.
          </p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {links.map((link) => (
              <li
                key={`${link.hubId}-${link.otherHubId}`}
                className="flex items-center gap-3 rounded-xl border border-border bg-background px-4 py-3"
              >
                <div className="flex min-w-0 flex-1 items-center gap-2">
                  <span className="min-w-0 flex-1 truncate text-sm font-semibold text-foreground">
                    {hubName(link.hubId)}
                  </span>
                  <span className="flex shrink-0 items-center gap-1 text-primary" aria-hidden="true">
                    <span className="w-3 border-t-2 border-dashed border-primary/50" />
                    <ArrowLeftRight className="size-4" />
                    <span className="w-3 border-t-2 border-dashed border-primary/50" />
                  </span>
                  <span className="sr-only">linked with</span>
                  <span className="min-w-0 flex-1 truncate text-right text-sm font-semibold text-foreground">
                    {hubName(link.otherHubId)}
                  </span>
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="shrink-0 text-muted-foreground hover:text-destructive"
                  aria-label={`Remove the link between ${hubName(link.hubId)} and ${hubName(link.otherHubId)}`}
                  onClick={() => setLinkToRemove(link)}
                >
                  <Trash2 className="size-4" aria-hidden="true" />
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <form onSubmit={askToAdd} className="mt-5 grid gap-4 border-t border-border pt-5 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
        <div className="space-y-1.5">
          <Label htmlFor="link-hub">First branch</Label>
          <ThemedSelect
            id="link-hub"
            value={hubId}
            onChange={(value) => {
              setHubId(value);
              if (value === otherHubId || (otherHubId && isLinked(value, otherHubId))) setOtherHubId("");
            }}
            options={firstOptions}
            placeholder="Select a branch..."
            disabled={linkable.length < 2}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="link-other-hub">Second branch</Label>
          <ThemedSelect
            id="link-other-hub"
            value={otherHubId}
            onChange={setOtherHubId}
            options={secondOptions}
            placeholder="Select a branch..."
            disabled={!hubId}
          />
        </div>
        <Button type="submit" disabled={!canAdd || busy}>
          {busy ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Plus className="size-4" aria-hidden="true" />}
          Add link
        </Button>
      </form>
      {linkable.length < 2 && (
        <p className="mt-2 text-xs text-muted-foreground">A link needs two active branch hubs.</p>
      )}

      <ConfirmationDialog
        open={confirmAdd}
        onOpenChange={setConfirmAdd}
        title="Link these branches?"
        description={`Parcels picked up from now on between ${hubName(hubId)} and ${hubName(otherHubId)} will skip the central hub. Parcels already on their way keep their route.`}
        confirmLabel="Add link"
        variant="default"
        loading={busy}
        onConfirm={addLink}
      />
      <ConfirmationDialog
        open={linkToRemove !== null}
        onOpenChange={(open) => {
          if (!open) setLinkToRemove(null);
        }}
        title="Remove this link?"
        description={
          linkToRemove
            ? `New parcels between ${hubName(linkToRemove.hubId)} and ${hubName(linkToRemove.otherHubId)} will go through the central hub again. Parcels already on their way keep their direct route.`
            : ""
        }
        confirmLabel="Remove link"
        variant="warning"
        loading={busy}
        onConfirm={removeLink}
      />
    </section>
  );
}
