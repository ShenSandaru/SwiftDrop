import { Check, Circle, RotateCcw, X } from "lucide-react";
import { cn } from "cn";
import type { FailureReason, ParcelStatus } from "@/lib/delivery";
import { FAILURE_REASON_LABELS, STATUS_LABELS, formatDateTime } from "./format";
import { STAGES, furthestStage, isProblem, journeyEnd } from "./journey";

export interface TimelineEntry {
  status: ParcelStatus;
  occurredAt: string;
  failureReason?: FailureReason | null;
  note?: string | null;
  /** The hub of a hub step (ADR 0009), shown after the status. */
  hubName?: string | null;
  /** The delivery attempt of the step. */
  attempt?: number | null;
}

/**
 * The parcel's journey: every step with its time, hub and attempt, then the stages still ahead
 * (greyed). A cancelled or failed parcel ends at that step with its reason; a parcel on its way
 * back has only the return ahead.
 */
export function StatusTimeline({ entries }: { entries: TimelineEntry[] }) {
  const last = entries.at(-1)?.status;
  const end = last ? journeyEnd(last) : null;
  const upcoming: ParcelStatus[] = end
    ? last === "RETURNING" ? ["RETURNED"] : []
    : STAGES.slice(furthestStage(entries) + 1);

  return (
    <ol className="space-y-0" aria-label="Delivery progress">
      {entries.map((entry, index) => {
        const isFailure = isProblem(entry.status);
        const isReturn = entry.status === "RETURNING" || entry.status === "RETURNED";
        const isLast = index === entries.length - 1 && upcoming.length === 0;
        return (
          <li key={index} className="relative flex gap-3 pb-5 last:pb-0">
            {!isLast && <span className="absolute left-[11px] top-6 h-full w-px bg-primary/40" aria-hidden="true" />}
            <span
              className={cn(
                "relative z-10 inline-flex size-6 shrink-0 items-center justify-center rounded-full",
                isFailure ? "bg-destructive text-white" : "bg-primary text-primary-foreground",
              )}
            >
              {isFailure ? <X className="size-3.5" aria-hidden="true" />
                : isReturn ? <RotateCcw className="size-3" aria-hidden="true" />
                  : <Check className="size-3.5" aria-hidden="true" />}
            </span>
            <div className="min-w-0 space-y-0.5">
              <p className={cn("text-sm font-semibold", isFailure ? "text-destructive" : "text-foreground")}>
                {STATUS_LABELS[entry.status]}
                {entry.hubName && <span className="font-normal text-muted-foreground"> · {entry.hubName}</span>}
                {entry.attempt && <span className="font-normal text-muted-foreground"> · attempt {entry.attempt}</span>}
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
