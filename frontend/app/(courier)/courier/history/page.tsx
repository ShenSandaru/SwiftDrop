"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { ChevronLeft, ChevronRight, RefreshCw, Search, X } from "lucide-react";
import { cn } from "cn";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ThemedSelect } from "@/components/ui/select";
import { StatusBadge } from "@/components/delivery/status-badge";
import { TASK_LABELS } from "@/components/delivery/courier-task";
import { formatDate, formatDateTime, STATUS_LABELS } from "@/components/delivery/format";
import { formatApiError } from "@/lib/api";
import {
  listCourierParcels,
  PARCEL_STATUSES,
  type CourierParcel,
  type CourierTask,
  type ParcelStatus,
} from "@/lib/delivery";
import {
  filterTasksByStatus,
  filterTasksByType,
  searchCompletedTasks,
  sortHistoryTasks,
  summarizeCompletedTasks,
  type HistorySortOrder,
} from "./model";

const TASK_FILTERS: Array<CourierTask | "ALL"> = ["ALL", "PICKUP", "DELIVERY", "RETURN"];
const HISTORY_PAGE_SIZE = 8;

export default function CourierDeliveryHistoryPage() {
  const [parcels, setParcels] = useState<CourierParcel[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<ParcelStatus | "ALL">("ALL");
  const [taskFilter, setTaskFilter] = useState<CourierTask | "ALL">("ALL");
  const [sortOrder, setSortOrder] = useState<HistorySortOrder>("UPDATED_DESC");
  const [page, setPage] = useState(0);
  const mountedRef = useRef(false);
  const refreshInFlightRef = useRef(false);
  const refreshSequenceRef = useRef(0);

  useEffect(() => {
    let cancelled = false;
    mountedRef.current = true;

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
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });

    return () => {
      cancelled = true;
      mountedRef.current = false;
      refreshSequenceRef.current += 1;
      refreshInFlightRef.current = false;
    };
  }, []);

  const completed = useMemo(() => parcels?.filter((parcel) => parcel.taskDone) ?? [], [parcels]);
  const matchingCompleted = useMemo(
    () =>
      sortHistoryTasks(
        filterTasksByType(filterTasksByStatus(searchCompletedTasks(completed, search), statusFilter), taskFilter),
        sortOrder
      ),
    [completed, search, statusFilter, taskFilter, sortOrder]
  );
  const pageCount = Math.ceil(matchingCompleted.length / HISTORY_PAGE_SIZE);
  const visiblePage = Math.min(page, Math.max(pageCount - 1, 0));
  const pagedCompleted = matchingCompleted.slice(
    visiblePage * HISTORY_PAGE_SIZE,
    (visiblePage + 1) * HISTORY_PAGE_SIZE
  );
  const hasActiveFilters =
    Boolean(search.trim()) || statusFilter !== "ALL" || taskFilter !== "ALL" || sortOrder !== "UPDATED_DESC";
  const firstResult = matchingCompleted.length === 0 ? 0 : visiblePage * HISTORY_PAGE_SIZE + 1;
  const lastResult = Math.min((visiblePage + 1) * HISTORY_PAGE_SIZE, matchingCompleted.length);
  const summary = summarizeCompletedTasks(parcels ?? []);

  async function refreshHistory() {
    if (isLoading || refreshInFlightRef.current) return;

    refreshInFlightRef.current = true;
    const requestSequence = ++refreshSequenceRef.current;
    setIsRefreshing(true);
    setRefreshError(null);
    setError(null);
    try {
      const loaded = await listCourierParcels();
      if (!mountedRef.current || requestSequence !== refreshSequenceRef.current) return;
      setParcels(loaded);
      setPage(0);
    } catch (failure) {
      if (!mountedRef.current || requestSequence !== refreshSequenceRef.current) return;
      const message = formatApiError(failure);
      if (parcels === null) {
        setError(message);
      } else {
        setRefreshError(message);
      }
    } finally {
      if (requestSequence === refreshSequenceRef.current) {
        refreshInFlightRef.current = false;
        if (mountedRef.current) {
          setIsRefreshing(false);
        }
      }
    }
  }

  function resetFilters() {
    setSearch("");
    setStatusFilter("ALL");
    setTaskFilter("ALL");
    setSortOrder("UPDATED_DESC");
    setPage(0);
  }

  return (
    <div className="mx-auto max-w-xl space-y-5">
      <section aria-labelledby="courier-history-heading" className="space-y-3">
        <div className="flex items-start justify-between gap-3">
          <div className="space-y-1">
            <h2 className="text-xl font-bold tracking-tight text-foreground" id="courier-history-heading">
              Delivery History
            </h2>
            <p className="text-xs text-muted-foreground">
              Showing tasks completed today · {formatDate(new Date().toISOString())}. Previous days&apos; delivery history is not available from the existing API.
            </p>
          </div>
          <Button
            aria-busy={isRefreshing}
            aria-label={error && parcels === null ? "Retry loading delivery history" : "Refresh delivery history"}
            disabled={isLoading || isRefreshing}
            onClick={refreshHistory}
            size="sm"
            variant="outline"
          >
            <RefreshCw aria-hidden="true" />
            {isRefreshing ? "Refreshing" : error && parcels === null ? "Retry" : "Refresh"}
          </Button>
        </div>

        {parcels !== null && !error && (
          <div className="grid gap-3 sm:grid-cols-2" aria-label="Today's completed task summary">
            <SummaryCard label="Completed tasks" value={summary.totalCompletedTasks} />
            <SummaryCard label="Delivered parcels" value={summary.delivered} />
            <SummaryCard label="Failed attempts" value={summary.failedAttempts} />
            <SummaryCard label="Failed deliveries" value={summary.failedDeliveries} />
            <SummaryCard label="Other task outcomes" value={summary.otherOutcomes} />
          </div>
        )}

        <div className="grid gap-3 sm:grid-cols-2 sm:items-end">
          <label className="relative block">
            <span className="sr-only">Search by tracking number</span>
            <Search
              aria-hidden="true"
              className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
            />
            <Input
              autoComplete="off"
              className="pl-9"
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              placeholder="Search by tracking number"
              type="search"
              value={search}
            />
          </label>
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground" htmlFor="courier-history-status">
              Status
            </label>
            <ThemedSelect
              id="courier-history-status"
              onChange={(value) => {
                setStatusFilter(value as ParcelStatus | "ALL");
                setPage(0);
              }}
              options={[
                { value: "ALL", label: "All statuses" },
                ...PARCEL_STATUSES.map((status) => ({ value: status, label: STATUS_LABELS[status] })),
              ]}
              value={statusFilter}
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground" htmlFor="courier-history-task">
              Task type
            </label>
            <ThemedSelect
              id="courier-history-task"
              onChange={(value) => {
                setTaskFilter(value as CourierTask | "ALL");
                setPage(0);
              }}
              options={TASK_FILTERS.map((task) => ({
                value: task,
                label: task === "ALL" ? "All task types" : TASK_LABELS[task],
              }))}
              value={taskFilter}
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground" htmlFor="courier-history-sort">
              Sort by
            </label>
            <ThemedSelect
              id="courier-history-sort"
              onChange={(value) => {
                setSortOrder(value as HistorySortOrder);
                setPage(0);
              }}
              options={[
                { value: "UPDATED_DESC", label: "Recently updated" },
                { value: "UPDATED_ASC", label: "Oldest updated" },
                { value: "TRACKING_ASC", label: "Tracking number (A–Z)" },
                { value: "TRACKING_DESC", label: "Tracking number (Z–A)" },
              ]}
              value={sortOrder}
            />
          </div>
        </div>

        {hasActiveFilters && (
          <div className="flex justify-end">
            <Button onClick={resetFilters} size="sm" variant="ghost">
              <X aria-hidden="true" />
              Clear filters
            </Button>
          </div>
        )}

        {parcels !== null && !error && matchingCompleted.length > 0 && (
          <p className="text-xs text-muted-foreground" role="status">
            Showing {firstResult}–{lastResult} of {matchingCompleted.length} completed tasks
          </p>
        )}

        {error && (
          <p role="alert" className="rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
            {error}
          </p>
        )}

        {refreshError && (
          <p role="alert" className="rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
            Could not refresh the latest tasks. Showing the last loaded data. {refreshError}
          </p>
        )}

        {parcels === null && (isLoading || isRefreshing) && (
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

        {parcels !== null && !error && completed.length > 0 && matchingCompleted.length === 0 && (
          <p className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
            No completed tasks match the selected criteria.
          </p>
        )}

        {matchingCompleted.length > 0 && (
          <ul className="space-y-3">
            {pagedCompleted.map((parcel) => (
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

        {pageCount > 1 && (
          <nav aria-label="Delivery history pages" className="flex items-center justify-between">
            <Button disabled={visiblePage === 0} onClick={() => setPage(visiblePage - 1)} size="sm" variant="outline">
              <ChevronLeft aria-hidden="true" /> Previous
            </Button>
            <span className="text-sm text-muted-foreground">
              Page {visiblePage + 1} of {pageCount}
            </span>
            <Button
              disabled={visiblePage + 1 >= pageCount}
              onClick={() => setPage(visiblePage + 1)}
              size="sm"
              variant="outline"
            >
              Next <ChevronRight aria-hidden="true" />
            </Button>
          </nav>
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
