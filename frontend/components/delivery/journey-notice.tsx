import { Info, RotateCcw, XCircle } from "lucide-react";
import { cn } from "cn";
import type { ParcelStatus } from "@/lib/delivery";

function plural(count: number, word: string) {
  return `${count} ${word}${count === 1 ? "" : "s"}`;
}

/** One sentence for the steps a customer may not expect: failed attempts, returns, cancellation. */
function noticeFor(status: ParcelStatus, attempts: number, hubName: string | null): string | null {
  switch (status) {
    case "DELIVERY_ATTEMPT_FAILED":
      return `We couldn't deliver it on attempt ${attempts}. The courier brings it back to the branch for another try or a return.`;
    case "AT_HUB":
      return attempts > 0
        ? `Back at ${hubName ?? "the branch"} after ${plural(attempts, "delivery attempt")}.`
        : null;
    case "RETURNING":
      return attempts > 0
        ? `We couldn't deliver it after ${plural(attempts, "attempt")}, so it is on its way back to the sender.`
        : "It is on its way back to the sender.";
    case "RETURNED":
      return "The parcel was returned to the sender.";
    case "CANCELLED":
      return "This booking was cancelled before pickup.";
    default:
      return null;
  }
}

/** A short explanation under the route when the journey leaves the normal path (ADR 0009 §3, §6). */
export function JourneyNotice({ status, attempts, hubName = null, className }: {
  status: ParcelStatus;
  attempts: number;
  /** The last hub the parcel reached, if known. */
  hubName?: string | null;
  className?: string;
}) {
  const text = noticeFor(status, attempts, hubName);
  if (!text) {
    return null;
  }
  const back = status === "RETURNING" || status === "RETURNED";
  const Icon = status === "CANCELLED" ? XCircle : back ? RotateCcw : Info;
  return (
    <p className={cn(
      "flex items-start gap-2 rounded-xl border p-3 text-sm",
      back ? "border-violet-200 bg-violet-50 text-violet-900 dark:border-violet-800 dark:bg-violet-950/50 dark:text-violet-100"
        : status === "CANCELLED" ? "border-border bg-muted text-muted-foreground"
          : "border-orange-200 bg-orange-50 text-orange-900 dark:border-orange-800 dark:bg-orange-950/50 dark:text-orange-100",
      className,
    )}>
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      {text}
    </p>
  );
}

/** The last hub named in a history, for the notice. */
export function lastHubName(history: { hubName?: string | null }[]): string | null {
  return history.findLast((entry) => entry.hubName)?.hubName ?? null;
}

/** The highest attempt in a history; the public view has no attempt counter of its own. */
export function attemptsIn(history: { attempt?: number | null }[]): number {
  return history.reduce((highest, entry) => Math.max(highest, entry.attempt ?? 0), 0);
}
