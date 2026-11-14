"use client";

import { useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { AlertCircle, ClipboardList, Eye, RefreshCw, Search, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { formatDateTime } from "@/components/delivery/format";
import { formatApiError, type PageResponse } from "@/lib/api";
import {
  listDispatchActivityHistory,
  type DispatchActivityAction,
  type DispatchActivityItem,
} from "@/lib/dispatch";

const PAGE_SIZE = 20;

const ACTIONS: Array<{ value: DispatchActivityAction; label: string }> = [
  { value: "PICKUP_ASSIGNED", label: "Pickup assigned" },
  { value: "DELIVERY_ASSIGNED", label: "Delivery assigned" },
  { value: "RETURN_ASSIGNED", label: "Return assigned" },
  { value: "COURIER_REASSIGNED", label: "Courier reassigned" },
  { value: "HUB_SCAN", label: "Hub scan" },
  { value: "TRANSIT", label: "Transit" },
  { value: "FAILURE", label: "Failure" },
  { value: "RETURN_INITIATED", label: "Return initiated" },
  { value: "CANCELLATION", label: "Cancellation" },
];

interface ActivityFilters {
  from?: string;
  to?: string;
  actionType?: DispatchActivityAction;
  trackingNumber?: string;
  dispatcherId?: string;
}

export function DispatchHistory() {
  const [filters, setFilters] = useState<ActivityFilters>({});
  const [pageNumber, setPageNumber] = useState(0);
  const [page, setPage] = useState<PageResponse<DispatchActivityItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [selectedActivity, setSelectedActivity] = useState<DispatchActivityItem | null>(null);

  useEffect(() => {
    let active = true;
    listDispatchActivityHistory({ ...filters, page: pageNumber, size: PAGE_SIZE })
      .then((loaded) => {
        if (active) {
          setPage(loaded);
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setError(formatApiError(failure));
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [filters, pageNumber, refreshKey]);

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const from = dateBoundary(String(form.get("from") ?? ""), false);
    const to = dateBoundary(String(form.get("to") ?? ""), true);
    const actionType = String(form.get("actionType") ?? "");
    setFilters({
      from,
      to,
      actionType: isActivityAction(actionType) ? actionType : undefined,
      trackingNumber: String(form.get("trackingNumber") ?? "").trim() || undefined,
      dispatcherId: String(form.get("dispatcherId") ?? "").trim() || undefined,
    });
    setPageNumber(0);
    setLoading(true);
    setError(null);
  }

  function clearFilters() {
    setFilters({});
    setPageNumber(0);
    setLoading(true);
    setError(null);
    setRefreshKey((current) => current + 1);
  }

  function refreshHistory() {
    setLoading(true);
    setError(null);
    setRefreshKey((current) => current + 1);
  }

  function goToPage(nextPage: number) {
    setLoading(true);
    setError(null);
    setPageNumber(nextPage);
  }

  return (
    <section className="space-y-5" aria-labelledby="dispatch-history-heading">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 id="dispatch-history-heading" className="flex items-center gap-2 text-lg font-bold">
            <ClipboardList className="size-5 text-primary" aria-hidden="true" />
            Dispatcher Activity History
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Read-only record of courier assignments and dispatcher parcel operations.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={refreshHistory}
          disabled={loading}
        >
          <RefreshCw className={`mr-1.5 size-3.5 ${loading ? "animate-spin" : ""}`} aria-hidden="true" />
          Refresh
        </Button>
      </div>

      <Card>
        <CardContent>
          <form onSubmit={applyFilters} className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
            <label className="space-y-1 text-xs font-medium text-muted-foreground">
              From date
              <Input type="date" name="from" />
            </label>
            <label className="space-y-1 text-xs font-medium text-muted-foreground">
              Through date
              <Input type="date" name="to" />
            </label>
            <label className="space-y-1 text-xs font-medium text-muted-foreground">
              Action type
              <select
                name="actionType"
                defaultValue=""
                className="h-8 w-full rounded-lg border border-input bg-background px-2.5 text-sm text-foreground focus-visible:border-ring focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
              >
                <option value="">All actions</option>
                {ACTIONS.map((action) => (
                  <option key={action.value} value={action.value}>
                    {action.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="space-y-1 text-xs font-medium text-muted-foreground">
              Tracking number
              <Input name="trackingNumber" placeholder="Search parcel" />
            </label>
            <label className="space-y-1 text-xs font-medium text-muted-foreground">
              Dispatcher ID
              <Input name="dispatcherId" placeholder="UUID" />
            </label>
            <div className="flex items-end gap-2">
              <Button type="submit" className="flex-1">
                <Search className="mr-1.5 size-4" aria-hidden="true" />
                Apply
              </Button>
              <Button type="reset" variant="outline" onClick={clearFilters}>
                Clear
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          {error ? (
            <div role="alert" className="flex flex-col items-center gap-3 px-5 py-12 text-center">
              <AlertCircle className="size-8 text-destructive" aria-hidden="true" />
              <p className="text-sm font-medium">Activity history could not be loaded.</p>
              <p className="text-sm text-muted-foreground">{error}</p>
              <Button type="button" variant="outline" size="sm" onClick={refreshHistory}>
                Try again
              </Button>
            </div>
          ) : loading ? (
            <div role="status" className="px-5 py-12 text-center text-sm text-muted-foreground">
              Loading dispatcher activity…
            </div>
          ) : page?.items.length ? (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[780px] text-left text-sm">
                <thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-4 py-3 font-semibold">Time</th>
                    <th scope="col" className="px-4 py-3 font-semibold">Parcel</th>
                    <th scope="col" className="px-4 py-3 font-semibold">Activity</th>
                    <th scope="col" className="px-4 py-3 font-semibold">Dispatcher</th>
                    <th scope="col" className="px-4 py-3 font-semibold">Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {page.items.map((activity) => (
                    <tr key={activity.id} className="hover:bg-muted/30">
                      <td className="whitespace-nowrap px-4 py-3 text-xs text-muted-foreground">
                        {formatDateTime(activity.occurredAt)}
                      </td>
                      <td className="px-4 py-3">
                        <Link
                          href={`/track?code=${encodeURIComponent(activity.trackingNumber)}`}
                          className="font-mono text-xs font-semibold text-primary hover:underline"
                        >
                          {activity.trackingNumber}
                        </Link>
                      </td>
                      <td className="px-4 py-3 font-medium">{actionLabel(activity.actionType)}</td>
                      <td className="px-4 py-3">
                        <span className="block text-sm">{activity.actorName}</span>
                        {activity.actorId && (
                          <span className="font-mono text-[10px] text-muted-foreground">{activity.actorId}</span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={() => setSelectedActivity(activity)}
                        >
                          <Eye className="mr-1.5 size-3.5" aria-hidden="true" />
                          View
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="px-5 py-12 text-center">
              <ClipboardList className="mx-auto size-8 text-muted-foreground/60" aria-hidden="true" />
              <p className="mt-3 text-sm font-semibold">No activity found</p>
              <p className="mt-1 text-sm text-muted-foreground">
                No dispatcher activity matches the selected filters.
              </p>
            </div>
          )}

          {!error && page && (
            <div className="flex flex-col gap-3 border-t border-border px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-xs text-muted-foreground">
                Showing {page.totalItems === 0 ? 0 : page.page * page.size + 1}–
                {Math.min((page.page + 1) * page.size, page.totalItems)} of {page.totalItems}
              </p>
              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => goToPage(Math.max(0, page.page - 1))}
                  disabled={loading || page.page <= 0}
                >
                  Previous
                </Button>
                <span className="min-w-16 text-center text-xs text-muted-foreground">
                  {page.totalPages === 0 ? "0 / 0" : `${page.page + 1} / ${page.totalPages}`}
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => goToPage(page.page + 1)}
                  disabled={loading || page.totalPages === 0 || page.page >= page.totalPages - 1}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {selectedActivity && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="activity-details-heading"
            className="w-full max-w-lg rounded-xl border border-border bg-card p-5 shadow-xl"
          >
            <div className="flex items-start justify-between gap-3">
              <div>
                <h3 id="activity-details-heading" className="text-lg font-bold">Activity details</h3>
                <p className="mt-1 text-sm text-muted-foreground">{actionLabel(selectedActivity.actionType)}</p>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label="Close activity details"
                onClick={() => setSelectedActivity(null)}
              >
                <X aria-hidden="true" />
              </Button>
            </div>
            <dl className="mt-5 grid gap-3 text-sm sm:grid-cols-2">
              <Detail label="Tracking number" value={selectedActivity.trackingNumber} />
              <Detail label="Timestamp" value={formatDateTime(selectedActivity.occurredAt)} />
              <Detail label="Dispatcher" value={selectedActivity.actorName} />
              <Detail label="Dispatcher ID" value={selectedActivity.actorId ?? "Unavailable"} />
              <Detail label="Task type" value={selectedActivity.details.assignmentType} />
              <Detail label="Courier ID" value={selectedActivity.details.courierId} />
              <Detail label="Status transition" value={statusTransition(selectedActivity)} />
              <Detail label="Hub" value={selectedActivity.details.hubCode} />
              <Detail label="Delivery attempt" value={selectedActivity.details.attempt} />
              <Detail label="Failure reason" value={selectedActivity.details.failureReason} />
            </dl>
          </section>
        </div>
      )}
    </section>
  );
}

function Detail({ label, value }: { label: string; value: string | number | null | undefined }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-words">{value ?? "—"}</dd>
    </div>
  );
}

function dateBoundary(date: string, endExclusive: boolean): string | undefined {
  if (!date) {
    return undefined;
  }
  const boundaryDate = new Date(`${date}T00:00:00Z`);
  if (endExclusive) {
    boundaryDate.setUTCDate(boundaryDate.getUTCDate() + 1);
  }
  return `${boundaryDate.toISOString().slice(0, 10)}T00:00:00+05:30`;
}

function isActivityAction(value: string): value is DispatchActivityAction {
  return ACTIONS.some((action) => action.value === value);
}

function actionLabel(action: DispatchActivityAction): string {
  return ACTIONS.find((item) => item.value === action)?.label ?? action;
}

function statusTransition(activity: DispatchActivityItem): string | null {
  const { fromStatus, toStatus } = activity.details;
  return toStatus ? `${fromStatus ?? "—"} → ${toStatus}` : null;
}
