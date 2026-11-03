import { Check, Circle, X } from "lucide-react";
import { cn } from "cn";
import type { FailureReason, ParcelStatus } from "@/lib/delivery";
import { FAILURE_REASON_LABELS, STATUS_LABELS, formatDateTime } from "./format";

export interface TimelineEntry {
  status: ParcelStatus;
  occurredAt: string;
  failureReason?: FailureReason | null;
  note?: string | null;
}

const NORMAL_PATH: ParcelStatus[] = ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"];

/**
 * The parcel's journey: completed steps with their times, then the steps still ahead (greyed). A
 * failed parcel ends at the failure, with its reason.
 */
export function StatusTimeline({ entries }: { entries: TimelineEntry[] }) {
  const failed = entries.some((entry) => entry.status === "FAILED");
  const reached = new Set(entries.map((entry) => entry.status));
  const upcoming = failed ? [] : NORMAL_PATH.filter((status) => !reached.has(status));

  return (
    <ol className="space-y-0" aria-label="Delivery progress">
      {entries.map((entry, index) => {
        const isFailure = entry.status === "FAILED";
        const isLast = index === entries.length - 1 && upcoming.length === 0;
        return (
          <li key={`${entry.status}-${entry.occurredAt}`} className="relative flex gap-3 pb-5 last:pb-0">
            {!isLast && <span className="absolute left-[11px] top-6 h-full w-px bg-primary/40" aria-hidden="true" />}
            <span
              className={cn(
                "relative z-10 inline-flex size-6 shrink-0 items-center justify-center rounded-full",
                isFailure ? "bg-destructive text-white" : "bg-primary text-primary-foreground",
              )}
            >
              {isFailure ? <X className="size-3.5" aria-hidden="true" /> : <Check className="size-3.5" aria-hidden="true" />}
            </span>
            <div className="min-w-0 space-y-0.5">
              <p className={cn("text-sm font-semibold", isFailure ? "text-destructive" : "text-foreground")}>
                {STATUS_LABELS[entry.status]}
              </p>
              <p className="text-xs text-muted-foreground">{formatDateTime(entry.occurredAt)}</p>
              {entry.failureReason && (
                <p className="text-xs text-destructive">{FAILURE_REASON_LABELS[entry.failureReason]}</p>
              )}
              {entry.note && <p className="text-xs italic text-muted-foreground break-words">“{entry.note}”</p>}
            </div>
          </li>
        );
      })}
      {upcoming.map((status, index) => (
        <li key={status} className="relative flex gap-3 pb-5 last:pb-0">
          {index < upcoming.length - 1 && (
            <span className="absolute left-[11px] top-6 h-full w-px bg-border" aria-hidden="true" />
          )}
          <span className="relative z-10 inline-flex size-6 shrink-0 items-center justify-center rounded-full border border-border bg-card text-muted-foreground">
            <Circle className="size-2.5" aria-hidden="true" />
          </span>
          <p className="pt-0.5 text-sm text-muted-foreground">{STATUS_LABELS[status]}</p>
        </li>
      ))}
    </ol>
  );
}
