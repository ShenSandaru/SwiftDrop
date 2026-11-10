"use client";

import { useState } from "react";
import { ArrowRight, Loader2, PackageX, RotateCcw, ScanLine, Truck, UserPlus, Warehouse } from "lucide-react";
import { cn } from "cn";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/delivery/status-badge";
import { FAILURE_REASON_LABELS } from "@/components/delivery/format";
import { formatApiError } from "@/lib/api";
import type { FailureReason } from "@/lib/delivery";
import {
  assignDispatchCourier,
  markParcelFailed,
  scanParcelAtHub,
  sendParcelToNextHub,
  startParcelReturn,
  type DispatchAction,
  type DispatchCourier,
  type DispatchParcelItem,
} from "@/lib/dispatch";

interface Lane {
  key: string;
  title: string;
  hint: string;
  icon: typeof ScanLine;
  matches: (actions: DispatchAction[]) => boolean;
}

/** One lane per next step; a parcel sits in the lane of the step the backend offers. */
const LANES: Lane[] = [
  { key: "scan", title: "Scan in at hub", hint: "Picked up, arriving from a line haul, or back from a failed attempt",
    icon: ScanLine, matches: (actions) => actions.includes("SCAN_AT_HUB") },
  { key: "send", title: "Send onwards", hint: "At a hub on the way; load on the next line haul",
    icon: Truck, matches: (actions) => actions.includes("SEND_TO_NEXT_HUB") },
  { key: "deliver", title: "Ready for delivery", hint: "At the destination branch; assign a delivery courier",
    icon: UserPlus, matches: (actions) => actions.includes("ASSIGN_DELIVERY") },
  { key: "return", title: "Returns", hint: "No attempt left, or refused: back to the sender",
    icon: RotateCcw, matches: (actions) => actions.includes("START_RETURN") || actions.includes("ASSIGN_RETURN") },
];

const FAILURE_CHOICES: FailureReason[] = ["DAMAGED", "OTHER"];

/**
 * The hub network board (ADR 0009): parcels between pickup and delivery, grouped by the next
 * dispatcher step. Every button comes from the parcel's `actions`, so the rules live only in the
 * backend.
 */
export function HubBoard({ parcels, couriers, loading, error, onChanged, onFeedback }: {
  parcels: DispatchParcelItem[];
  couriers: DispatchCourier[];
  loading: boolean;
  error: string | null;
  onChanged: () => void;
  onFeedback: (message: string) => void;
}) {
  const [busy, setBusy] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function run(parcel: DispatchParcelItem, step: () => Promise<unknown>, done: string) {
    setBusy(parcel.id);
    setActionError(null);
    try {
      await step();
      onFeedback(`${parcel.trackingNumber}: ${done}`);
      onChanged();
    } catch (failure) {
      setActionError(`${parcel.trackingNumber}: ${formatApiError(failure)}`);
    } finally {
      setBusy(null);
    }
  }

  if (loading) {
    return <div className="h-48 animate-pulse rounded-2xl border border-border bg-muted" aria-label="Loading hub board" />;
  }
  if (error) {
    return <p role="alert" className="rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{error}</p>;
  }

  const inNetwork = parcels.filter((parcel) => LANES.some((lane) => lane.matches(parcel.actions)));
  if (inNetwork.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-border p-10 text-center">
        <Warehouse className="size-8 text-muted-foreground" aria-hidden="true" />
        <p className="font-semibold text-foreground">Nothing waiting at the hubs</p>
        <p className="text-sm text-muted-foreground">Picked-up parcels appear here for their first scan.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {actionError && (
        <p role="alert" className="rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
          {actionError}
        </p>
      )}
      <div className="grid gap-4 md:grid-cols-2 2xl:grid-cols-4">
        {LANES.map((lane) => {
          const items = inNetwork.filter((parcel) => lane.matches(parcel.actions));
          const Icon = lane.icon;
          return (
            <section key={lane.key} aria-label={lane.title} className="flex flex-col rounded-2xl border border-border bg-muted/40 p-3">
              <header className="mb-3 space-y-0.5 px-1">
                <h3 className="flex items-center gap-2 text-sm font-bold text-foreground">
                  <Icon className="size-4 text-primary" aria-hidden="true" /> {lane.title}
                  <span className="ml-auto rounded-full bg-card px-2 py-0.5 text-xs font-semibold text-muted-foreground">
                    {items.length}
                  </span>
                </h3>
                <p className="text-xs text-muted-foreground">{lane.hint}</p>
              </header>
              <ul className="space-y-2">
                {items.map((parcel) => (
                  <li key={parcel.id}>
                    <HubCard parcel={parcel} couriers={couriers} busy={busy === parcel.id} run={run} />
                  </li>
                ))}
                {items.length === 0 && <li className="px-1 py-4 text-center text-xs text-muted-foreground">Empty</li>}
              </ul>
            </section>
          );
        })}
      </div>
    </div>
  );
}

function HubCard({ parcel, couriers, busy, run }: {
  parcel: DispatchParcelItem;
  couriers: DispatchCourier[];
  busy: boolean;
  run: (parcel: DispatchParcelItem, step: () => Promise<unknown>, done: string) => void;
}) {
  const [courierId, setCourierId] = useState("");
  const [failing, setFailing] = useState(false);
  const [reason, setReason] = useState<FailureReason>("DAMAGED");
  const has = (action: DispatchAction) => parcel.actions.includes(action);
  const taskType = has("ASSIGN_RETURN") ? "RETURN" : "DELIVERY";

  return (
    <article className="space-y-3 rounded-xl border border-border bg-card p-3 shadow-xs">
      <div className="flex flex-wrap items-start justify-between gap-x-2 gap-y-1.5">
        <div className="min-w-0">
          <p className="break-all font-mono text-xs font-semibold tracking-[0.12em] text-primary">{parcel.trackingNumber}</p>
          <p className="truncate text-xs text-muted-foreground">
            <span className="capitalize">{parcel.pickupCity}</span> → <span className="capitalize">{parcel.dropoffCity}</span>
          </p>
        </div>
        <StatusBadge status={parcel.status} />
      </div>

      <RouteStrip parcel={parcel} />

      {(parcel.deliveryAttempts > 0 || parcel.lastFailureReason) && (
        <p className="text-xs text-muted-foreground">
          {parcel.deliveryAttempts} attempt{parcel.deliveryAttempts === 1 ? "" : "s"}
          {parcel.lastFailureReason && <> · last: {FAILURE_REASON_LABELS[parcel.lastFailureReason]}</>}
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        {has("SCAN_AT_HUB") && parcel.expectedHubId && (
          <Button size="sm" disabled={busy}
            onClick={() => run(parcel, () => scanParcelAtHub(parcel.id, parcel.expectedHubId!), `scanned in at ${parcel.expectedHubCode}`)}>
            {busy ? <Loader2 className="animate-spin" aria-hidden="true" /> : <ScanLine aria-hidden="true" />}
            Scan in at {parcel.expectedHubCode}
          </Button>
        )}
        {has("SEND_TO_NEXT_HUB") && (
          <Button size="sm" disabled={busy}
            onClick={() => run(parcel, () => sendParcelToNextHub(parcel.id), "sent to the next hub")}>
            {busy ? <Loader2 className="animate-spin" aria-hidden="true" /> : <Truck aria-hidden="true" />}
            Send onwards
          </Button>
        )}
        {has("START_RETURN") && (
          <Button size="sm" disabled={busy}
            onClick={() => run(parcel, () => startParcelReturn(parcel.id), "return started")}>
            <RotateCcw aria-hidden="true" /> Start return
          </Button>
        )}
      </div>

      {(has("ASSIGN_DELIVERY") || has("ASSIGN_RETURN")) && (
        <div className="flex flex-wrap gap-2">
          <label htmlFor={`courier-${parcel.id}`} className="sr-only">
            {taskType === "RETURN" ? "Return courier" : "Delivery courier"}
          </label>
          <select id={`courier-${parcel.id}`} value={courierId} onChange={(event) => setCourierId(event.target.value)}
            className="h-8 min-w-[10rem] flex-1 rounded-lg border border-input bg-transparent px-2 text-xs">
            <option value="">{parcel.assignedCourierId ? "Reassign to…" : "Choose a courier…"}</option>
            {couriers.map((courier) => (
              <option key={courier.id} value={courier.id} disabled={courier.id === parcel.assignedCourierId}>
                {courier.fullName}
              </option>
            ))}
          </select>
          <Button size="sm" disabled={busy || !courierId}
            onClick={() => run(parcel, () => assignDispatchCourier(parcel.id, courierId, taskType),
              taskType === "RETURN" ? "return courier assigned" : "delivery courier assigned")}>
            <UserPlus aria-hidden="true" /> Assign
          </Button>
        </div>
      )}
      {parcel.assignedCourierId && (has("ASSIGN_DELIVERY") || has("ASSIGN_RETURN")) && (
        <p className="text-[11px] text-primary">
          Courier assigned: {couriers.find((courier) => courier.id === parcel.assignedCourierId)?.fullName ?? "assigned"}
        </p>
      )}

      {has("MARK_FAILED") && (
        failing ? (
          <div className="flex gap-2">
            <label htmlFor={`reason-${parcel.id}`} className="sr-only">Failure reason</label>
            <select id={`reason-${parcel.id}`} value={reason} onChange={(event) => setReason(event.target.value as FailureReason)}
              className="h-8 min-w-0 flex-1 rounded-lg border border-input bg-transparent px-2 text-xs">
              {FAILURE_CHOICES.map((choice) => <option key={choice} value={choice}>{FAILURE_REASON_LABELS[choice]}</option>)}
            </select>
            <Button size="sm" variant="destructive" disabled={busy}
              onClick={() => run(parcel, () => markParcelFailed(parcel.id, reason), "marked failed")}>
              Confirm
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setFailing(false)}>Keep</Button>
          </div>
        ) : (
          <button type="button" onClick={() => setFailing(true)}
            className="inline-flex items-center gap-1 text-[11px] font-medium text-muted-foreground hover:text-destructive">
            <PackageX className="size-3" aria-hidden="true" /> Lost or damaged?
          </button>
        )
      )}
    </article>
  );
}

/** The route as hub codes, with the parcel's position: at a hub, or travelling towards one. */
function RouteStrip({ parcel }: { parcel: DispatchParcelItem }) {
  if (!parcel.originHubCode || !parcel.destinationHubCode) {
    return null;
  }
  const stops = [parcel.originHubCode];
  if (parcel.viaHubCode) {
    stops.push(parcel.viaHubCode);
  }
  if (parcel.destinationHubCode !== parcel.originHubCode) {
    stops.push(parcel.destinationHubCode);
  }
  return (
    <ol className="flex items-center gap-1 text-[11px] font-semibold" aria-label="Route">
      {stops.map((code, index) => {
        const here = code === parcel.currentHubCode;
        const heading = parcel.status === "IN_TRANSIT" && code === parcel.nextHubCode;
        return (
          <li key={`${code}-${index}`} className="flex items-center gap-1">
            {index > 0 && <ArrowRight className={cn("size-3", heading ? "text-primary" : "text-muted-foreground/50")} aria-hidden="true" />}
            <span className={cn("rounded-md border px-1.5 py-0.5 font-mono tracking-wider",
              here ? "border-primary bg-primary text-primary-foreground"
                : heading ? "border-primary/50 text-primary"
                  : "border-border text-muted-foreground")}>
              {code}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
