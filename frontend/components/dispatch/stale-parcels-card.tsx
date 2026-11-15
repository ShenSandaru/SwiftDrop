"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, ChevronRight, Hourglass } from "lucide-react";
import { StatusBadge } from "@/components/delivery/status-badge";
import { ReadOnlyBadge } from "@/components/dispatch/read-only-badge";
import { formatApiError, type PageResponse } from "@/lib/api";
import { listStaleDispatchParcels, type DispatchParcelItem } from "@/lib/dispatch";

interface Loaded {
  page: PageResponse<DispatchParcelItem>;
  /** When the list arrived, to say how long each parcel has waited. */
  at: number;
}

/** "5 hours", "2 days": how long ago, rounded down, never less than an hour. */
function waited(sinceIso: string, now: number): string {
  const hours = Math.max(1, Math.floor((now - new Date(sinceIso).getTime()) / 3_600_000));
  if (hours < 48) {
    return `${hours} ${hours === 1 ? "hour" : "hours"}`;
  }
  return `${Math.floor(hours / 24)} days`;
}

/**
 * Parcels that need attention: unfinished, but with no scan, status change or assignment for too
 * long. Catches a missed scan, a booking nobody collected, or a lost parcel. The backend decides
 * which parcels are stale; this card only lists them.
 */
export function StaleParcelsCard({ refreshKey, onDetails }: {
  refreshKey: number;
  onDetails: (parcel: DispatchParcelItem) => void;
}) {
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listStaleDispatchParcels()
      .then((page) => {
        if (!cancelled) {
          setLoaded({ page, at: Date.now() });
          setError(null);
        }
      })
      .catch((e) => {
        if (!cancelled) setError(formatApiError(e));
      });
    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  if (error) {
    return (
      <p role="alert" className="flex items-start gap-2 rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
        <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        <span>Could not check for parcels that need attention. {error}</span>
      </p>
    );
  }
  if (!loaded) {
    return <div className="h-20 animate-pulse rounded-xl bg-muted" role="status" aria-label="Checking for stale parcels" />;
  }

  const { page, at } = loaded;
  if (page.totalItems === 0) {
    return (
      <p className="flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-3 text-sm text-muted-foreground shadow-xs">
        <CheckCircle2 className="size-4 text-primary" aria-hidden="true" />
        Every unfinished parcel has moved recently. Nothing needs attention.
      </p>
    );
  }

  return (
    <section aria-labelledby="heading-stale-parcels" className="overflow-hidden rounded-xl border border-amber-500/40 bg-card shadow-xs">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-amber-500/30 bg-amber-500/10 px-5 py-3">
        <h3 id="heading-stale-parcels" className="flex items-center gap-2 text-sm font-bold text-foreground">
          <Hourglass className="size-4 text-amber-600 dark:text-amber-400" aria-hidden="true" />
          Needs attention
        </h3>
        <span className="rounded-full bg-amber-500/20 px-2.5 py-0.5 text-xs font-bold text-amber-800 dark:text-amber-200">
          {page.totalItems} {page.totalItems === 1 ? "parcel" : "parcels"} with no recent activity
        </span>
      </div>
      <ul className="divide-y divide-border">
        {page.items.map((parcel) => (
          <li key={parcel.id}>
            <button
              type="button"
              onClick={() => onDetails(parcel)}
              className="flex w-full items-center gap-3 px-5 py-3 text-left transition-colors hover:bg-muted/60 focus-visible:bg-muted/60 focus-visible:outline-none"
            >
              <div className="min-w-0 flex-1">
                <p className="font-mono text-sm font-semibold text-foreground">{parcel.trackingNumber}</p>
                <p className="truncate text-xs text-muted-foreground">
                  {parcel.pickupCity} to {parcel.dropoffCity}
                  {parcel.currentHubCode ? ` · at ${parcel.currentHubCode}` : ""}
                  {parcel.nextHubCode ? ` · heading to ${parcel.nextHubCode}` : ""}
                </p>
              </div>
              <ReadOnlyBadge readOnly={parcel.readOnly} />
              <StatusBadge status={parcel.status} />
              <span className="hidden shrink-0 text-xs font-semibold text-amber-700 dark:text-amber-300 sm:inline">
                {waited(parcel.updatedAt, at)} without activity
              </span>
              <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            </button>
          </li>
        ))}
      </ul>
      {page.totalItems > page.items.length && (
        <p className="border-t border-border px-5 py-2 text-xs text-muted-foreground">
          Showing the {page.items.length} that have waited longest.
        </p>
      )}
    </section>
  );
}
