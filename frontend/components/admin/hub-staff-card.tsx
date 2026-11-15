"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle, Building, Loader2, UserCog, X } from "lucide-react";
import { cn } from "cn";
import { ThemedSelect } from "@/components/ui/select";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { formatApiError } from "@/lib/api";
import {
  addStaffToHub,
  listHubStaff,
  removeStaffFromHub,
  type Hub,
  type HubStaffMember,
} from "@/lib/hubs";

type Filter = "all" | "needs-hub";

const needsAHub = (member: HubStaffMember) => member.hubIds.length === 0;

/**
 * Home hubs of dispatchers and couriers (ADR 0012). A dispatcher sees and handles only the parcels
 * of their hubs, and one with no hub sees nothing, so the people who still need a hub are easy to
 * find. The backend enforces all of it; this card only edits the assignments.
 */
export function HubStaffCard({ hubs }: { hubs: Hub[] }) {
  const [staff, setStaff] = useState<HubStaffMember[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<Filter>("all");
  const [busyUser, setBusyUser] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setStaff(await listHubStaff());
    } catch (e) {
      setStaff((current) => current ?? []);
      setError(formatApiError(e));
    }
  }, []);

  useEffect(() => {
    // The state is set after the request returns, not synchronously in the effect
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void reload();
  }, [reload]);

  const hubName = useMemo(() => new Map(hubs.map((hub) => [hub.id, hub.name])), [hubs]);
  const activeHubs = useMemo(
    () => hubs.filter((hub) => hub.active).sort((a, b) => a.name.localeCompare(b.name)),
    [hubs],
  );

  async function change(member: HubStaffMember, action: () => Promise<void>) {
    setBusyUser(member.userId);
    setError(null);
    try {
      await action();
      await reload();
    } catch (e) {
      setError(formatApiError(e));
    } finally {
      setBusyUser(null);
    }
  }

  const waiting = (staff ?? []).filter(needsAHub);
  const waitingDispatchers = waiting.filter((member) => member.roles.includes("DISPATCHER"));
  const visible = (staff ?? []).filter((member) => filter === "all" || needsAHub(member));

  return (
    <section aria-label="Staff and hubs" className="rounded-2xl border border-border bg-card p-5 shadow-xs">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h3 className="flex items-center gap-2 font-bold text-foreground">
            <UserCog className="size-4 text-primary" aria-hidden="true" /> Staff and hubs
          </h3>
          <p className="max-w-2xl text-sm text-muted-foreground">
            A dispatcher sees and handles only the parcels of their hubs. A dispatcher with no hub sees nothing.
            Only admins see every hub.
          </p>
        </div>
        <div role="group" aria-label="Filter staff" className="inline-flex rounded-lg border border-border bg-background p-0.5 text-xs font-semibold">
          {([["all", "Everyone"], ["needs-hub", `Needs a hub${waiting.length ? ` (${waiting.length})` : ""}`]] as const).map(
            ([value, label]) => (
              <button
                key={value}
                type="button"
                aria-pressed={filter === value}
                onClick={() => setFilter(value)}
                className={cn(
                  "rounded-md px-3 py-1.5 transition-colors",
                  filter === value ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
                )}
              >
                {label}
              </button>
            ),
          )}
        </div>
      </div>

      {waitingDispatchers.length > 0 && (
        <Alert variant="warning" className="mt-4">
          <AlertTriangle aria-hidden="true" />
          <AlertTitle>
            {waitingDispatchers.length} {waitingDispatchers.length === 1 ? "dispatcher has" : "dispatchers have"} no hub
          </AlertTitle>
          <AlertDescription>
            They see an empty portal until you give them a hub.
          </AlertDescription>
        </Alert>
      )}
      {error && (
        <Alert variant="destructive" className="mt-4">
          <AlertTriangle aria-hidden="true" />
          <AlertTitle>Staff and hubs</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="mt-4">
        {staff === null ? (
          <div className="h-24 animate-pulse rounded-xl bg-muted" aria-hidden="true" />
        ) : visible.length === 0 ? (
          <p className="rounded-xl border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">
            {filter === "needs-hub" ? "Everyone has a hub." : "No dispatchers or couriers yet."}
          </p>
        ) : (
          <ul className="divide-y divide-border rounded-xl border border-border">
            {visible.map((member) => {
              const taken = new Set(member.hubIds);
              const options = activeHubs
                .filter((hub) => !taken.has(hub.id))
                .map((hub) => ({ value: hub.id, label: hub.name, description: hub.city }));
              const busy = busyUser === member.userId;
              return (
                <li key={member.userId} className="flex flex-col gap-3 px-4 py-3 lg:flex-row lg:items-center">
                  <div className="min-w-0 lg:w-56">
                    <p className="truncate text-sm font-semibold text-foreground">{member.fullName}</p>
                    <p className="text-xs capitalize text-muted-foreground">
                      {member.roles.map((role) => role.toLowerCase()).join(" and ")}
                    </p>
                  </div>

                  <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1.5">
                    {member.hubIds.map((id) => (
                      <span key={id} className="inline-flex items-center gap-1 rounded-full bg-primary/10 py-0.5 pl-2.5 pr-1 text-xs font-semibold text-primary">
                        <Building className="size-3" aria-hidden="true" /> {hubName.get(id) ?? "Unknown hub"}
                        <button
                          type="button"
                          disabled={busy}
                          aria-label={`Remove ${member.fullName} from ${hubName.get(id) ?? "this hub"}`}
                          onClick={() => change(member, () => removeStaffFromHub(id, member.userId))}
                          className="rounded-full p-0.5 hover:bg-primary/20 focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-50"
                        >
                          <X className="size-3" aria-hidden="true" />
                        </button>
                      </span>
                    ))}
                    {needsAHub(member) && (
                      <span className="inline-flex items-center gap-1 rounded-full bg-amber-500/15 px-2.5 py-0.5 text-xs font-semibold text-amber-800 dark:text-amber-200">
                        No hub
                      </span>
                    )}
                  </div>

                  <div className="flex items-center gap-2 lg:justify-end">
                    {busy && <Loader2 className="size-4 animate-spin text-muted-foreground" aria-label="Saving" />}
                    <div className="w-44">
                      <ThemedSelect
                        size="sm"
                        value=""
                        disabled={busy || options.length === 0}
                        onChange={(hubId) => change(member, () => addStaffToHub(hubId, member.userId))}
                        options={options}
                        placeholder="Add to a hub..."
                      />
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
}
