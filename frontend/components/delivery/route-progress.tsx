import { Check, Package, RotateCcw, X } from "lucide-react";
import { cn } from "cn";
import type { ParcelStatus } from "@/lib/delivery";
import { STATUS_LABELS, formatShortDateTime } from "./format";
import { STAGES, furthestStage, journeyEnd, stageOf } from "./journey";

const SHORT_LABELS: Record<ParcelStatus, string> = {
  CREATED: "Booked",
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  OUT_FOR_DELIVERY: "Out for delivery",
  AT_HUB: "At hub",
  DELIVERY_ATTEMPT_FAILED: "Attempt failed",
  DELIVERED: "Delivered",
  RETURNING: "Returning",
  RETURNED: "Returned",
  CANCELLED: "Cancelled",
  FAILED: "Failed",
};

export interface RouteProgressProps {
  status: ParcelStatus;
  history: { status: ParcelStatus; occurredAt: string }[];
  /** Pulses the parcel marker while updates arrive live. */
  live?: boolean;
  /** Hides the times under the stops (lists, cards). */
  compact?: boolean;
  className?: string;
}

interface Stop {
  label: string;
  kind: "stage" | "problem" | "return";
  time: string | undefined;
}

/**
 * The Island Route: the parcel's journey as a route line with one stop per stage (ADR 0009 §2).
 * The line fills up to the current stop and the parcel marker slides forward as updates arrive.
 * Hub scans and line hauls share the "In transit" stop, attempts share "Out for delivery". A
 * cancelled or failed parcel ends at a red stop, a returned one at a return stop.
 */
export function RouteProgress({ status, history, live = false, compact = false, className }: RouteProgressProps) {
  const end = journeyEnd(status);
  const firstTime = (matches: (entry: ParcelStatus) => boolean) =>
    history.find((entry) => matches(entry.status))?.occurredAt;

  // The stages the parcel reached (all of them on the normal path), then the end stop if any
  const lastStage = end ? (end === "CANCELLED" ? 0 : furthestStage(history)) : STAGES.length - 1;
  // On the way back the return stop is still ahead
  const current = end ? (status === "RETURNING" ? lastStage : lastStage + 1) : Math.max(0, stageOf(status));
  const stops: Stop[] = STAGES.slice(0, lastStage + 1).map((stage, index) => ({
    // The current stage shows what is happening in it, such as "At hub" or "Attempt failed"
    label: SHORT_LABELS[!end && index === current ? status : stage],
    kind: "stage",
    time: firstTime((entry) => stageOf(entry) === index),
  }));
  if (end === "RETURN") {
    stops.push({ label: SHORT_LABELS.RETURNED, kind: "return", time: firstTime((entry) => entry === "RETURNED") });
  } else if (end) {
    stops.push({ label: SHORT_LABELS[status], kind: "problem", time: firstTime((entry) => entry === status) });
  }

  const finished = end ? status !== "RETURNING" : status === "DELIVERED";
  // On the way back the marker sits halfway to the return stop
  const position = status === "RETURNING" ? current + 0.5 : current;
  const progress = stops.length > 1 ? (position / (stops.length - 1)) * 100 : 0;

  return (
    <div className={cn("w-full", className)}>
      <p className="sr-only">Current status: {STATUS_LABELS[status]}</p>
      <div className={cn("relative mx-6", compact ? "mt-6 h-16" : "mt-8 h-24")} aria-hidden="true">
        {/* Road ahead, then the travelled part */}
        <div className="absolute inset-x-0 top-5 border-t-2 border-dashed border-border" />
        <div
          className={cn(
            "absolute left-0 top-[19px] h-1 rounded-full transition-[width] duration-700 ease-out",
            end === "CANCELLED" || end === "FAILED" ? "bg-gradient-to-r from-primary to-destructive" : "bg-primary",
          )}
          style={{ width: `${progress}%` }}
        />

        {stops.map((stop, index) => {
          const reached = index <= current;
          const problem = stop.kind === "problem";
          const back = stop.kind === "return";
          return (
            <div
              key={index}
              className="absolute top-0 flex w-20 -translate-x-1/2 flex-col items-center text-center"
              style={{ left: `${(index / (stops.length - 1)) * 100}%` }}
            >
              <span
                className={cn(
                  "mt-3 inline-flex size-[18px] items-center justify-center rounded-full border-2 bg-card transition-colors duration-500",
                  problem
                    ? "border-destructive bg-destructive text-white"
                    : reached
                      ? "border-primary bg-primary text-primary-foreground"
                      : back
                        ? "border-primary/50 text-primary"
                        : "border-border text-transparent",
                )}
              >
                {problem ? <X className="size-3" /> : back ? <RotateCcw className="size-2.5" /> : reached ? <Check className="size-3" /> : null}
              </span>
              <span
                className={cn(
                  "mt-2 text-[10px] font-semibold leading-tight sm:text-xs",
                  problem ? "text-destructive" : reached ? "text-foreground" : "text-muted-foreground",
                )}
              >
                {stop.label}
              </span>
              {!compact && stop.time && (
                <span className="mt-0.5 hidden whitespace-nowrap text-[10px] leading-tight text-muted-foreground sm:block">
                  {formatShortDateTime(stop.time)}
                </span>
              )}
            </div>
          );
        })}

        {/* The parcel itself */}
        {!finished && (
          <div
            className="absolute -top-7 -translate-x-1/2 transition-[left] duration-700 ease-out"
            style={{ left: `${progress}%` }}
          >
            {live && <span className="absolute inset-0 animate-ping rounded-full bg-primary/40" />}
            <span className="relative inline-flex size-8 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg ring-4 ring-background">
              <Package className="size-4" />
            </span>
            {/* Pin tip pointing at the stop */}
            <span className="absolute left-1/2 top-full -mt-1 size-2.5 -translate-x-1/2 rotate-45 bg-primary" />
          </div>
        )}
      </div>
    </div>
  );
}
