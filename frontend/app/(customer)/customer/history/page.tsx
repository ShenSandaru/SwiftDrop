"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowRight, ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "cn";
import { Button } from "@/components/ui/button";
import { MiniRoute } from "@/components/delivery/mini-route";
import { StatusBadge } from "@/components/delivery/status-badge";
import { STATUS_LABELS, formatDate, formatMoney, formatWeight } from "@/components/delivery/format";
import { formatApiError, type PageResponse } from "@/lib/api";
import { PARCEL_STATUSES, listParcels, type ParcelListItem, type ParcelStatus } from "@/lib/delivery";

const PAGE_SIZE = 10;

export default function DeliveryHistoryPage() {
  const [status, setStatus] = useState<ParcelStatus | undefined>(undefined);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<{ key: string; data: PageResponse<ParcelListItem> } | null>(null);
  const [error, setError] = useState<{ key: string; message: string } | null>(null);
  const key = `${status ?? "ALL"}:${page}`;

  useEffect(() => {
    let cancelled = false;
    listParcels(page, PAGE_SIZE, status)
      .then((data) => !cancelled && setResult({ key, data }))
      .catch((failure) => !cancelled && setError({ key, message: formatApiError(failure) }));
    return () => {
      cancelled = true;
    };
  }, [key, page, status]);

  const data = result?.key === key ? result.data : null;
  const failure = error?.key === key ? error.message : null;

  const choose = (next: ParcelStatus | undefined) => {
    setStatus(next);
    setPage(0);
  };

  return (
    <div className="space-y-5">
      <div className="flex gap-2 overflow-x-auto pb-1" role="group" aria-label="Filter by status">
        <FilterChip active={!status} onClick={() => choose(undefined)}>All</FilterChip>
        {PARCEL_STATUSES.map((option) => (
          <FilterChip key={option} active={status === option} onClick={() => choose(option)}>
            {STATUS_LABELS[option]}
          </FilterChip>
        ))}
      </div>

      {failure && <p className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">{failure}</p>}

      {!data && !failure && (
        <div className="space-y-3" aria-hidden="true">
          {[0, 1, 2].map((index) => (
            <div key={index} className="h-20 animate-pulse rounded-2xl border border-border bg-muted" />
          ))}
        </div>
      )}

      {data && data.items.length === 0 && (
        <div className="rounded-2xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
          {status ? `No deliveries are “${STATUS_LABELS[status]}”.` : "You haven't booked any deliveries yet."}{" "}
          <Link href="/customer/new" className="font-semibold text-primary hover:underline">Book a delivery</Link>
        </div>
      )}

      {data && data.items.length > 0 && (
        <ul className="space-y-3">
          {data.items.map((parcel) => (
            <li key={parcel.id}>
              <Link
                href={`/customer/parcels/${parcel.id}`}
                className="grid gap-3 rounded-2xl border border-border bg-card p-4 transition-all hover:border-primary/40 hover:shadow-md sm:grid-cols-[1fr_auto_auto] sm:items-center sm:gap-6"
              >
                <div className="min-w-0 space-y-1">
                  <p className="font-mono text-xs font-semibold tracking-[0.15em] text-primary">{parcel.trackingNumber}</p>
                  <p className="flex items-center gap-2 text-base font-bold text-foreground">
                    <span className="truncate capitalize">{parcel.pickupCity}</span>
                    <ArrowRight className="size-4 shrink-0 text-primary" aria-hidden="true" />
                    <span className="truncate capitalize">{parcel.dropoffCity}</span>
                  </p>
                  <p className="text-xs text-muted-foreground">
                    Booked {formatDate(parcel.createdAt)} · {formatWeight(parcel.weightGrams)}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <MiniRoute status={parcel.status} />
                  <StatusBadge status={parcel.status} />
                </div>
                <p className="text-lg font-extrabold text-foreground sm:text-right">{formatMoney(parcel.fee)}</p>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <nav className="flex items-center justify-between" aria-label="Pages">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
            <ChevronLeft aria-hidden="true" /> Newer
          </Button>
          <span className="text-sm text-muted-foreground">
            Page {data.page + 1} of {data.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
            Older <ChevronRight aria-hidden="true" />
          </Button>
        </nav>
      )}
    </div>
  );
}

function FilterChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        "shrink-0 rounded-full border px-3.5 py-1.5 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
        active ? "border-primary bg-primary text-primary-foreground" : "border-border bg-card text-muted-foreground hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}
