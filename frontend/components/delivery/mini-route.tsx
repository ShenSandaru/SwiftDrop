import { cn } from "cn";
import type { ParcelStatus } from "@/lib/delivery";
import { STATUS_LABELS } from "./format";
import { STAGES, journeyEnd, stageOf } from "./journey";

/**
 * A five-dot Island Route for lists: dots fill up to the current stage (ADR 0009 §2). A cancelled
 * or failed parcel turns red; one going back to the sender turns violet, like its status badge.
 */
export function MiniRoute({ status, className }: { status: ParcelStatus; className?: string }) {
  const end = journeyEnd(status);
  const stopped = end === "CANCELLED" || end === "FAILED";
  const back = end === "RETURN";
  const reached = end ? -1 : stageOf(status);
  const tone = stopped ? "bg-destructive" : "bg-violet-500";

  return (
    <div className={cn("flex items-center", className)} role="img" aria-label={`Progress: ${STATUS_LABELS[status]}`}>
      {STAGES.map((stop, index) => (
        <div key={stop} className="flex items-center">
          {index > 0 && (
            <span
              className={cn(
                "h-0.5 w-4 sm:w-6",
                end ? `${tone} opacity-40` : index <= reached ? "bg-primary" : "bg-border",
              )}
            />
          )}
          <span
            className={cn(
              "size-2.5 rounded-full",
              end ? tone : index <= reached ? "bg-primary" : "border border-border bg-card",
              !end && index === reached && status !== "DELIVERED" && "ring-4 ring-primary/20",
              back && status === "RETURNING" && index === 0 && "ring-4 ring-violet-500/20",
            )}
          />
        </div>
      ))}
    </div>
  );
}
