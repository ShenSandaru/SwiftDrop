import { cn } from "cn";
import type { ParcelStatus } from "@/lib/delivery";
import { STATUS_LABELS } from "./format";

const ORDER: ParcelStatus[] = ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"];

/** A five-dot Island Route for lists: dots fill up to the current stop; a failed parcel turns red. */
export function MiniRoute({ status, className }: { status: ParcelStatus; className?: string }) {
  const failed = status === "FAILED";
  const reached = failed ? -1 : ORDER.indexOf(status);

  return (
    <div className={cn("flex items-center", className)} role="img" aria-label={`Progress: ${STATUS_LABELS[status]}`}>
      {ORDER.map((stop, index) => (
        <div key={stop} className="flex items-center">
          {index > 0 && (
            <span
              className={cn(
                "h-0.5 w-4 sm:w-6",
                failed ? "bg-destructive/40" : index <= reached ? "bg-primary" : "bg-border",
              )}
            />
          )}
          <span
            className={cn(
              "size-2.5 rounded-full",
              failed ? "bg-destructive" : index <= reached ? "bg-primary" : "border border-border bg-card",
              !failed && index === reached && status !== "DELIVERED" && "ring-4 ring-primary/20",
            )}
          />
        </div>
      ))}
    </div>
  );
}
