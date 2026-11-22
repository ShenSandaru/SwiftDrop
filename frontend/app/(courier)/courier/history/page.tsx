"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { cn } from "cn";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { StatusBadge } from "@/components/delivery/status-badge";
import { TASK_LABELS } from "@/components/delivery/courier-task";
import { formatDate, formatDateTime } from "@/components/delivery/format";
import { formatApiError } from "@/lib/api";
import { listCourierParcels, type CourierParcel } from "@/lib/delivery";
import { summarizeCompletedTasks } from "./model";

export default function CourierDeliveryHistoryPage() {
  const [parcels, setParcels] = useState<CourierParcel[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    listCourierParcels()
      .then((loaded) => {
        if (!cancelled) {
          setParcels(loaded);
          setError(null);
        }
      })
      .catch((failure) => {
        if (!cancelled) {
          setError(formatApiError(failure));
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const completed = parcels?.filter((parcel) => parcel.taskDone) ?? [];
  const summary = summarizeCompletedTasks(parcels ?? []);

  return (
    <div className="mx-auto max-w-xl space-y-5">
      <section aria-labelledby="courier-history-heading" className="space-y-3">
        <div className="space-y-1">
          <h2 className="text-xl font-bold tracking-tight text-foreground" id="courier-history-heading">
            Delivery History
          </h2>
          <p className="text-xs text-muted-foreground">
            Showing tasks completed today · {formatDate(new Date().toISOString())}. Previous days&apos; delivery history is not available from the existing API.
          </p>
        </div>

        {parcels !== null && !error && (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" aria-label="Today's completed task summary">
            <SummaryCard label="Completed tasks" value={summary.totalCompletedTasks} />
            <SummaryCard label="Delivered parcels" value={summary.delivered} />
            <SummaryCard label="Failed attempts" value={summary.failedAttempts} />
            <SummaryCard label="Failed deliveries" value={summary.failedDeliveries} />
            <SummaryCard label="Other task outcomes" value={summary.otherOutcomes} />
          </div>
        )}

        {error && (
          <p role="alert" className="rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
            {error}
          </p>
        )}

        {parcels === null && !error && (
          <div className="space-y-3" role="status" aria-label="Loading completed tasks">
            {[0, 1, 2].map((index) => (
              <div key={index} className="h-24 animate-pulse rounded-2xl border border-border bg-muted" />
            ))}
          </div>
        )}

        {parcels !== null && !error && completed.length === 0 && (
          <p className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
            No completed tasks today.
          </p>
        )}

        {completed.length > 0 && (
          <ul className="space-y-3">
            {completed.map((parcel) => (
              <li key={parcel.id}>
                <article className="grid gap-3 rounded-2xl border border-border bg-card p-4 transition-all sm:grid-cols-[1fr_auto_auto] sm:items-center sm:gap-6">
                  <div className="min-w-0 space-y-1">
                    <p className="font-mono text-xs font-semibold tracking-[0.15em] text-primary">
                      {parcel.trackingNumber}
                    </p>
                    <p className="text-sm font-bold text-foreground">
                      {TASK_LABELS[parcel.task]}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      Last updated {formatDateTime(parcel.updatedAt)}
                    </p>
                  </div>

                  <StatusBadge status={parcel.status} />

                  <Link
                    href={`/courier/parcels/${parcel.id}`}
                    className={cn(buttonVariants({ variant: "outline", size: "sm" }), "w-full sm:w-auto")}
                    aria-label={`View details for ${parcel.trackingNumber}`}
                  >
                    View details
                    <ChevronRight aria-hidden="true" />
                  </Link>
                </article>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function SummaryCard({ label, value }: { label: string; value: number }) {
  return (
    <Card className="rounded-xl border-border bg-card py-0 shadow-xs">
      <CardContent className="p-4">
        <p className="text-xs font-medium text-muted-foreground">{label}</p>
        <p className="mt-1 text-2xl font-bold text-foreground">{value}</p>
      </CardContent>
    </Card>
  );
}
