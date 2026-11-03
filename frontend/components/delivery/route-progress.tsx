import { Check, Package, X } from "lucide-react";
import { cn } from "cn";
import type { ParcelStatus } from "@/lib/delivery";
import { STATUS_LABELS, formatShortDateTime } from "./format";

const NORMAL_PATH: ParcelStatus[] = ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"];

const SHORT_LABELS: Record<ParcelStatus, string> = {
  CREATED: "Booked",
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
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

/**
 * The Island Route: the parcel's journey as a route line with one stop per status. The line fills
 * up to the current stop and the parcel marker slides forward as updates arrive. A failed delivery
 * ends at a red stop right after the last stop it reached.
 */
export function RouteProgress({ status, history, live = false, compact = false, className }: RouteProgressProps) {
  const failed = status === "FAILED";
  const reachedBeforeFailure = history.filter((entry) => entry.status !== "FAILED").length;
  const stops: ParcelStatus[] = failed
    ? [...NORMAL_PATH.slice(0, Math.max(1, reachedBeforeFailure)), "FAILED"]
    : NORMAL_PATH;
  const current = Math.max(0, stops.indexOf(status));
  const progress = stops.length > 1 ? (current / (stops.length - 1)) * 100 : 0;
  const finished = status === "DELIVERED" || failed;
  const timeOf = new Map(history.map((entry) => [entry.status, entry.occurredAt]));

  return (
    <div className={cn("w-full", className)}>
      <p className="sr-only">Current status: {STATUS_LABELS[status]}</p>
      <div className={cn("relative mx-6", compact ? "mt-6 h-16" : "mt-8 h-24")} aria-hidden="true">
        {/* Road ahead, then the travelled part */}
        <div className="absolute inset-x-0 top-5 border-t-2 border-dashed border-border" />
        <div
          className={cn(
            "absolute left-0 top-[19px] h-1 rounded-full transition-[width] duration-700 ease-out",
            failed ? "bg-gradient-to-r from-primary to-destructive" : "bg-primary",
          )}
          style={{ width: `${progress}%` }}
        />

        {stops.map((stop, index) => {
          const reached = index <= current;
          const isFailure = stop === "FAILED";
          return (
            <div
              key={stop}
              className="absolute top-0 flex w-20 -translate-x-1/2 flex-col items-center text-center"
              style={{ left: `${(index / (stops.length - 1)) * 100}%` }}
            >
              <span
                className={cn(
                  "mt-3 inline-flex size-[18px] items-center justify-center rounded-full border-2 bg-card transition-colors duration-500",
                  isFailure
                    ? "border-destructive bg-destructive text-white"
                    : reached
                      ? "border-primary bg-primary text-primary-foreground"
                      : "border-border text-transparent",
                )}
              >
                {isFailure ? <X className="size-3" /> : reached ? <Check className="size-3" /> : null}
              </span>
              <span
                className={cn(
                  "mt-2 text-[10px] font-semibold leading-tight sm:text-xs",
                  isFailure ? "text-destructive" : reached ? "text-foreground" : "text-muted-foreground",
                )}
              >
                {SHORT_LABELS[stop]}
              </span>
              {!compact && timeOf.has(stop) && (
                <span className="mt-0.5 hidden whitespace-nowrap text-[10px] leading-tight text-muted-foreground sm:block">
                  {formatShortDateTime(timeOf.get(stop))}
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
