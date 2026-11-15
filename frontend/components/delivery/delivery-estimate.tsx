"use client";

import { useState } from "react";
import { CalendarClock, Hourglass } from "lucide-react";
import { cn } from "cn";
import { formatDay, sriLankaDay } from "@/components/delivery/format";

/**
 * The estimated delivery day from the backend, which works it out from the parcel's route. Shows
 * nothing without an estimate (finished or returning parcels). An estimate that has passed is
 * shown as late, not as a date in the past.
 */
export function DeliveryEstimate({ day, className }: { day?: string | null; className?: string }) {
  // Read once when the component appears, so rendering stays pure
  const [today] = useState(() => sriLankaDay(new Date()));
  if (!day) {
    return null;
  }
  const late = day < today;
  const Icon = late ? Hourglass : CalendarClock;
  return (
    <p
      className={cn(
        "flex items-center gap-3 rounded-2xl border px-4 py-3 text-sm",
        late ? "border-amber-500/30 bg-amber-500/10 text-amber-800 dark:text-amber-200" : "border-primary/20 bg-primary/5 text-foreground",
        className,
      )}
    >
      <Icon className={cn("size-5 shrink-0", late ? "" : "text-primary")} aria-hidden="true" />
      {late ? (
        <span>
          <span className="font-semibold">Taking longer than expected.</span>{" "}
          It was due on {formatDay(day)} and is still on its way.
        </span>
      ) : (
        <span>
          <span className="text-muted-foreground">Estimated delivery</span>{" "}
          <span className="font-bold text-primary">{day === today ? "Today" : formatDay(day)}</span>
        </span>
      )}
    </p>
  );
}
