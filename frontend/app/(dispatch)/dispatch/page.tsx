"use client";

import { useEffect, useMemo, useState, type ReactNode } from "react";
import Link from "next/link";
import {
  AlertCircle,
  ArrowUpRight,
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Info,
  MapPin,
  Package,
  PackageCheck,
  RefreshCw,
  Search,
  Truck,
  UserRound,
  Users,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { StatusBadge } from "@/components/delivery/status-badge";
import { formatDate, formatDateTime, STATUS_LABELS } from "@/components/delivery/format";
import { ParcelDialog } from "@/components/dispatch/parcel-dialog";
import { formatApiError, type PageResponse, type UserResponse } from "@/lib/api";
import type { ParcelStatus } from "@/lib/delivery";
import type {
  DispatchAssignment,
  DispatchAssignmentType,
  DispatchParcelItem,
} from "@/lib/dispatch";
import {
  countDispatchParcels,
  getCourierWorkloads,
  listActiveDispatchAssignments,
  listAllActiveDispatchAssignments,
  listAllDispatchCouriers,
  listAwaitingDeliveryAssignments,
  listDispatchParcels,
} from "@/lib/dispatch";

const PARCEL_PAGE_SIZE = 8;
const COURIER_PAGE_SIZE = 8;
const FILTER_STATUSES: Array<ParcelStatus | "ALL"> = [
  "ALL",
  "CREATED",
  "PICKED_UP",
  "IN_TRANSIT",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "FAILED",
];
const SOURCE_FILTERS = ["ALL", "CUSTOMER", "MERCHANT"] as const;
type SourceFilter = (typeof SOURCE_FILTERS)[number];

interface DashboardMetrics {
  total: number | null;
  pendingPickup: number | null;
  activePickups: number | null;
  awaitingDelivery: number | null;
  activeDeliveries: number | null;
  delivered: number | null;
}

interface MetricCardProps {
  label: string;
  value: number | null;
  description: string;
  icon: typeof Package;
  unavailable?: boolean;
}

const EMPTY_METRICS: DashboardMetrics = {
  total: null,
  pendingPickup: null,
  activePickups: null,
  awaitingDelivery: null,
  activeDeliveries: null,
  delivered: null,
};

function isParcelStatus(value: ParcelStatus | "ALL"): value is ParcelStatus {
  return value !== "ALL";
}

function parcelSourceLabel(source: DispatchParcelItem["source"]): string {
  return source === "CUSTOMER" ? "Customer booking" : "Merchant order";
}

function parcelLocation(parcel: DispatchParcelItem): string {
  return `${parcel.pickupCity} → ${parcel.dropoffCity}`;
}

export default function DispatchPortalPage() {
  const [refreshKey, setRefreshKey] = useState(0);
  const today = useMemo(
    () =>
      new Intl.DateTimeFormat("en-LK", {
        dateStyle: "full",
        timeZone: "Asia/Colombo",
      }).format(new Date()),
    [],
  );

  const [metrics, setMetrics] = useState<DashboardMetrics>(EMPTY_METRICS);
  const [metricsLoading, setMetricsLoading] = useState(true);
  const [metricsError, setMetricsError] = useState<string | null>(null);

  const [parcelStatus, setParcelStatus] = useState<ParcelStatus | "ALL">("CREATED");
  const [sourceFilter, setSourceFilter] = useState<SourceFilter>("ALL");
  const [assignmentFilter, setAssignmentFilter] = useState<"ALL" | "ASSIGNED" | "UNASSIGNED">("ALL");
  const [search, setSearch] = useState("");
  const [parcelPageNumber, setParcelPageNumber] = useState(0);
  const [parcelPage, setParcelPage] = useState<PageResponse<DispatchParcelItem> | null>(null);
  const [parcelPageLoading, setParcelPageLoading] = useState(true);
  const [parcelPageError, setParcelPageError] = useState<string | null>(null);

  const [activeParcels, setActiveParcels] = useState<DispatchParcelItem[]>([]);
  const [activeLoading, setActiveLoading] = useState(true);
  const [activeError, setActiveError] = useState<string | null>(null);

  const [deliveryPageNumber, setDeliveryPageNumber] = useState(0);
  const [deliveryPage, setDeliveryPage] = useState<PageResponse<DispatchParcelItem> | null>(null);
  const [deliveryLoading, setDeliveryLoading] = useState(true);
  const [deliveryError, setDeliveryError] = useState<string | null>(null);

  const [courierPageNumber, setCourierPageNumber] = useState(0);
  const [couriers, setCouriers] = useState<UserResponse[]>([]);
  const [workloads, setWorkloads] = useState<Record<string, number> | null>(null);
  const [activeAssignments, setActiveAssignments] = useState<DispatchAssignment[]>([]);
  const [couriersLoading, setCouriersLoading] = useState(true);
  const [couriersError, setCouriersError] = useState<string | null>(null);
  const [assignmentFeedback, setAssignmentFeedback] = useState<string | null>(null);

  const [selectedParcel, setSelectedParcel] = useState<
    | { parcel: DispatchParcelItem; mode: "details" }
    | { parcel: DispatchParcelItem; mode: "assignment"; assignmentType: DispatchAssignmentType }
    | null
  >(null);

  useEffect(() => {
    let active = true;
    const requests = [
      countDispatchParcels(),
      listDispatchParcels({ page: 0, size: 1, status: "CREATED", assigned: false }),
      listActiveDispatchAssignments("PICKUP", 0, 1),
      listAwaitingDeliveryAssignments(0, 1),
      listActiveDispatchAssignments("DELIVERY", 0, 1),
      countDispatchParcels("DELIVERED"),
    ] as const;

    Promise.allSettled(requests).then((results) => {
      if (!active) {
        return;
      }

      const readCount = (index: number) => {
        const result = results[index];
        if (result.status !== "fulfilled") {
          return null;
        }
        return typeof result.value === "number" ? result.value : result.value.totalItems;
      };
      setMetrics({
        total: readCount(0),
        pendingPickup: readCount(1),
        activePickups: readCount(2),
        awaitingDelivery: readCount(3),
        activeDeliveries: readCount(4),
        delivered: readCount(5),
      });
      if (results.some((result) => result.status === "rejected")) {
        setMetricsError("Some parcel totals could not be loaded. Unavailable values are shown as —.");
      }
      setMetricsLoading(false);
    });

    return () => {
      active = false;
    };
  }, [refreshKey]);

  useEffect(() => {
    let active = true;
    listDispatchParcels({
      page: parcelPageNumber,
      size: PARCEL_PAGE_SIZE,
      status: isParcelStatus(parcelStatus) ? parcelStatus : undefined,
      source: sourceFilter === "ALL" ? undefined : sourceFilter,
      assigned: assignmentFilter === "ALL" ? undefined : assignmentFilter === "ASSIGNED",
      search,
    })
      .then((loaded) => {
        if (active) {
          setParcelPage(loaded);
          setParcelPageError(null);
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setParcelPageError(formatApiError(failure));
        }
      })
      .finally(() => {
        if (active) {
          setParcelPageLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [parcelPageNumber, parcelStatus, sourceFilter, assignmentFilter, search, refreshKey]);

  useEffect(() => {
    let active = true;
    Promise.all([
      listDispatchParcels({ page: 0, size: 5, status: "PICKED_UP", assigned: true }),
      listDispatchParcels({ page: 0, size: 5, status: "IN_TRANSIT", assigned: true }),
      listDispatchParcels({ page: 0, size: 5, status: "OUT_FOR_DELIVERY", assigned: true }),
    ])
      .then((pages) => {
        if (active) {
          setActiveParcels(
            pages
              .flatMap((page) => page.items)
              .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
              .slice(0, 8),
          );
          setActiveError(null);
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setActiveError(formatApiError(failure));
        }
      })
      .finally(() => {
        if (active) {
          setActiveLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [refreshKey]);

  useEffect(() => {
    let active = true;
    listAwaitingDeliveryAssignments(deliveryPageNumber, PARCEL_PAGE_SIZE)
      .then((loaded) => {
        if (active) {
          setDeliveryPage(loaded);
          setDeliveryError(null);
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setDeliveryError(formatApiError(failure));
        }
      })
      .finally(() => {
        if (active) {
          setDeliveryLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [deliveryPageNumber, refreshKey]);

  useEffect(() => {
    let active = true;
    Promise.all([
      listAllDispatchCouriers(),
      getCourierWorkloads(),
      listAllActiveDispatchAssignments("PICKUP"),
      listAllActiveDispatchAssignments("DELIVERY"),
    ])
      .then(([loadedCouriers, loadedWorkloads, pickups, deliveries]) => {
        if (active) {
          setCouriers(loadedCouriers);
          setWorkloads(loadedWorkloads);
          setActiveAssignments([...pickups, ...deliveries]);
          setCouriersError(null);
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setCouriersError(formatApiError(failure));
          setWorkloads(null);
          setActiveAssignments([]);
        }
      })
      .finally(() => {
        if (active) {
          setCouriersLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [refreshKey]);

  const visibleParcels = parcelPage?.items ?? [];
  const courierItems = couriers.slice(
    courierPageNumber * COURIER_PAGE_SIZE,
    (courierPageNumber + 1) * COURIER_PAGE_SIZE,
  );
  const courierTotalPages = Math.ceil(couriers.length / COURIER_PAGE_SIZE);
  const assignmentByCourier = useMemo(
    () => new Map(activeAssignments.map((assignment) => [assignment.courierId, assignment])),
    [activeAssignments],
  );
  const courierById = useMemo(
    () => new Map(couriers.map((courier) => [courier.id, courier.fullName])),
    [couriers],
  );

  function refreshDashboard() {
    setMetricsLoading(true);
    setParcelPageLoading(true);
    setActiveLoading(true);
    setDeliveryLoading(true);
    setCouriersLoading(true);
    setMetricsError(null);
    setParcelPageError(null);
    setActiveError(null);
    setDeliveryError(null);
    setCouriersError(null);
    setAssignmentFeedback(null);
    setRefreshKey((current) => current + 1);
  }

  function changeParcelStatus(value: string) {
    setParcelPageLoading(true);
    setParcelPageError(null);
    setParcelPageNumber(0);
    setSearch("");
    setParcelStatus(value as ParcelStatus | "ALL");
  }

  function changeParcelPage(nextPage: number) {
    setParcelPageLoading(true);
    setParcelPageError(null);
    setParcelPageNumber(nextPage);
  }

  function changeCourierPage(nextPage: number) {
    setCourierPageNumber(nextPage);
  }

  function changeDeliveryPage(nextPage: number) {
    setDeliveryLoading(true);
    setDeliveryError(null);
    setDeliveryPageNumber(nextPage);
  }

  function courierAvailability(courier: UserResponse): "AVAILABLE" | "BUSY" | "INACTIVE" | "UNKNOWN" {
    if (!courier.enabled) {
      return "INACTIVE";
    }
    if (workloads === null) {
      return "UNKNOWN";
    }
    return (workloads[courier.id] ?? 0) > 0 ? "BUSY" : "AVAILABLE";
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div className="space-y-2">
          <div className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-accent px-3 py-1 text-xs font-semibold text-primary">
            <Truck className="size-3.5" aria-hidden="true" />
            Dispatch operations
          </div>
          <h1 className="text-2xl font-extrabold tracking-tight text-foreground sm:text-3xl">
            Dispatcher Dashboard
          </h1>
          <p className="text-sm text-muted-foreground">
            Manage parcel pickups, courier assignments, and deliveries
          </p>
          <p className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
            <CalendarDays className="size-3.5" aria-hidden="true" />
            <time suppressHydrationWarning>{today}</time>
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          onClick={refreshDashboard}
          disabled={metricsLoading || parcelPageLoading || activeLoading || deliveryLoading || couriersLoading}
          className="w-full sm:w-auto"
        >
          <RefreshCw
            className={
              metricsLoading || parcelPageLoading || activeLoading || deliveryLoading || couriersLoading
                ? "animate-spin"
                : ""
            }
            aria-hidden="true"
          />
          Refresh dashboard
        </Button>
      </header>

      <nav aria-label="Dashboard sections" className="flex gap-2 overflow-x-auto pb-1">
        <SectionLink href="#pickup-requests">Pickup requests</SectionLink>
        <SectionLink href="#delivery-assignments">Delivery assignments</SectionLink>
        <SectionLink href="#active-deliveries">Active deliveries</SectionLink>
        <SectionLink href="#couriers">Couriers</SectionLink>
        <SectionLink href="#parcel-tracking">Parcel tracking</SectionLink>
      </nav>

      {metricsError && (
        <p role="status" className="flex items-start gap-2 rounded-lg border border-border bg-muted/50 p-3 text-xs text-muted-foreground">
          <Info className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
          {metricsError}
        </p>
      )}
      {assignmentFeedback && (
        <p role="status" className="rounded-lg border border-primary/20 bg-accent p-3 text-sm text-primary">
          {assignmentFeedback}
        </p>
      )}

      <section aria-label="Parcel statistics" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <MetricCard
          label="Total parcels"
          value={metrics.total}
          description="All parcels visible to dispatch"
          icon={Package}
          loading={metricsLoading}
        />
        <MetricCard
          label="Pending pickup requests"
          value={metrics.pendingPickup}
          description="CREATED parcels with no active pickup courier"
          icon={ClipboardList}
          loading={metricsLoading}
        />
        <MetricCard
          label="Active pickups"
          value={metrics.activePickups}
          description="Active pickup assignments"
          icon={MapPin}
          loading={metricsLoading}
        />
        <MetricCard
          label="Awaiting delivery assignment"
          value={metrics.awaitingDelivery}
          description="Picked-up parcels with no current delivery courier"
          icon={Users}
          loading={metricsLoading}
        />
        <MetricCard
          label="Active deliveries"
          value={metrics.activeDeliveries}
          description="Active delivery assignments"
          icon={Truck}
          loading={metricsLoading}
        />
        <MetricCard
          label="Delivered parcels"
          value={metrics.delivered}
          description="Parcels in the DELIVERED status"
          icon={PackageCheck}
          loading={metricsLoading}
        />
      </section>

      <section id="pickup-requests" className="scroll-mt-6 space-y-4">
        <SectionHeading
          eyebrow="Dispatch queue"
          title="Pickup requests"
          description="Customer and merchant parcels with their current pickup assignment state."
        />
        <Card className="rounded-xl border-border bg-card shadow-xs">
          <CardContent className="space-y-4 p-4 sm:p-5">
            <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto_auto_auto]">
              <label className="relative block">
                <span className="sr-only">Search parcel IDs or tracking numbers</span>
                <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
                <Input
                  value={search}
                  onChange={(event) => {
                    setSearch(event.target.value);
                    setParcelPageNumber(0);
                    setParcelPageLoading(true);
                  }}
                  placeholder="Search parcel ID or tracking number"
                  className="h-10 pl-9"
                />
              </label>
              <label className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
                <span>Status</span>
                <select
                  value={parcelStatus}
                  onChange={(event) => changeParcelStatus(event.target.value)}
                  className="h-10 min-w-36 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                >
                  {FILTER_STATUSES.map((status) => (
                    <option key={status} value={status}>
                      {status === "ALL" ? "All statuses" : STATUS_LABELS[status]}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
                <span>Source</span>
                <select
                  value={sourceFilter}
                  onChange={(event) => {
                    setSourceFilter(event.target.value as SourceFilter);
                    setParcelPageNumber(0);
                    setParcelPageLoading(true);
                  }}
                  className="h-10 min-w-32 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                >
                  {SOURCE_FILTERS.map((source) => (
                    <option key={source} value={source}>
                      {source === "ALL" ? "All sources" : source === "CUSTOMER" ? "Customer" : "Merchant"}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
                <span>Assignment</span>
                <select
                  value={assignmentFilter}
                  onChange={(event) => {
                    setAssignmentFilter(event.target.value as typeof assignmentFilter);
                    setParcelPageNumber(0);
                    setParcelPageLoading(true);
                  }}
                  className="h-10 min-w-36 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                >
                  <option value="ALL">All assignments</option>
                  <option value="UNASSIGNED">Unassigned</option>
                  <option value="ASSIGNED">Assigned</option>
                </select>
              </label>
            </div>

            {parcelPageError && <ErrorMessage message={parcelPageError} />}
            {parcelPageLoading && <LoadingRows label="Loading parcel requests" />}
            {!parcelPageLoading && !parcelPageError && visibleParcels.length === 0 && (
              <EmptyState
                icon={Search}
                title={parcelPage?.items.length ? "No parcels match these filters" : "No parcel requests found"}
                description={
                  parcelPage?.items.length
                    ? "Try another search or source filter."
                    : "There are no records for this status in the current parcel API."
                }
              />
            )}

            {!parcelPageLoading && !parcelPageError && visibleParcels.length > 0 && (
              <>
                <div className="hidden overflow-x-auto rounded-lg border border-border md:block">
                  <table className="w-full min-w-[980px] text-left text-sm">
                    <thead className="bg-muted/60 text-[11px] uppercase tracking-wider text-muted-foreground">
                      <tr>
                        <th className="px-4 py-3 font-semibold">Parcel / tracking</th>
                        <th className="px-4 py-3 font-semibold">Customer or merchant</th>
                        <th className="px-4 py-3 font-semibold">Route</th>
                        <th className="px-4 py-3 font-semibold">Status</th>
                        <th className="px-4 py-3 font-semibold">Requested</th>
                        <th className="px-4 py-3 font-semibold">Assigned courier</th>
                        <th className="px-4 py-3 font-semibold">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {visibleParcels.map((parcel) => (
                        <ParcelTableRow
                          key={parcel.id}
                          parcel={parcel}
                          courierName={parcel.assignedCourierId ? courierById.get(parcel.assignedCourierId) : undefined}
                          onDetails={() => setSelectedParcel({ parcel, mode: "details" })}
                          onAssign={() => setSelectedParcel({
                            parcel,
                            mode: "assignment",
                            assignmentType: "PICKUP",
                          })}
                        />
                      ))}
                    </tbody>
                  </table>
                </div>
                <div className="space-y-3 md:hidden">
                  {visibleParcels.map((parcel) => (
                    <ParcelMobileCard
                      key={parcel.id}
                      parcel={parcel}
                      courierName={parcel.assignedCourierId ? courierById.get(parcel.assignedCourierId) : undefined}
                      onDetails={() => setSelectedParcel({ parcel, mode: "details" })}
                      onAssign={() => setSelectedParcel({
                        parcel,
                        mode: "assignment",
                        assignmentType: "PICKUP",
                      })}
                    />
                  ))}
                </div>
              </>
            )}

            <Pagination
              page={parcelPage?.page ?? parcelPageNumber}
              totalPages={parcelPage?.totalPages ?? 0}
              totalItems={parcelPage?.totalItems ?? 0}
              pageSize={PARCEL_PAGE_SIZE}
              loading={parcelPageLoading}
              onPrevious={() => changeParcelPage(Math.max(0, parcelPageNumber - 1))}
              onNext={() =>
                changeParcelPage(Math.min((parcelPage?.totalPages ?? 1) - 1, parcelPageNumber + 1))
              }
            />
          </CardContent>
        </Card>
      </section>

      <section id="delivery-assignments" className="scroll-mt-6 space-y-4">
        <SectionHeading
          eyebrow="Handoffs"
          title="Awaiting delivery assignment"
          description="Parcels whose pickup courier has completed collection and is ready to be released to a delivery courier."
        />
        <Card className="rounded-xl border-border bg-card shadow-xs">
          <CardContent className="space-y-4 p-4 sm:p-5">
            {deliveryError && <ErrorMessage message={deliveryError} />}
            {deliveryLoading && <LoadingRows label="Loading delivery assignment queue" />}
            {!deliveryLoading && !deliveryError && (deliveryPage?.items.length ?? 0) === 0 && (
              <EmptyState
                icon={CheckCircle2}
                title="No parcels awaiting delivery assignment"
                description="Picked-up parcels will appear here after the courier completes collection."
              />
            )}
            {!deliveryLoading && !deliveryError && (deliveryPage?.items.length ?? 0) > 0 && (
              <>
                <div className="hidden overflow-x-auto rounded-lg border border-border md:block">
                  <table className="w-full min-w-[780px] text-left text-sm">
                    <thead className="bg-muted/60 text-[11px] uppercase tracking-wider text-muted-foreground">
                      <tr>
                        <th className="px-4 py-3 font-semibold">Parcel</th>
                        <th className="px-4 py-3 font-semibold">Pickup complete</th>
                        <th className="px-4 py-3 font-semibold">Destination</th>
                        <th className="px-4 py-3 font-semibold">Current courier</th>
                        <th className="px-4 py-3 font-semibold">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {deliveryPage?.items.map((parcel) => (
                        <tr key={parcel.id}>
                          <td className="px-4 py-3">
                            <span className="block font-mono text-xs font-semibold text-primary">{parcel.trackingNumber}</span>
                            <span className="mt-1 block text-[10px] text-muted-foreground">ID {parcel.id.slice(0, 8)}…</span>
                          </td>
                          <td className="px-4 py-3"><StatusBadge status="PICKED_UP" /></td>
                          <td className="px-4 py-3 text-muted-foreground">{parcel.dropoffCity}</td>
                          <td className="px-4 py-3 text-xs text-muted-foreground">
                            {parcel.assignedCourierId ? courierById.get(parcel.assignedCourierId) ?? parcel.assignedCourierId : "Not assigned"}
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-1">
                              <Button type="button" variant="ghost" size="sm" onClick={() => setSelectedParcel({ parcel, mode: "details" })}>
                                View
                              </Button>
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={() => setSelectedParcel({ parcel, mode: "assignment", assignmentType: "DELIVERY" })}
                              >
                                Assign delivery
                              </Button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div className="space-y-3 md:hidden">
                  {deliveryPage?.items.map((parcel) => (
                    <article key={parcel.id} className="space-y-3 rounded-lg border border-border bg-background p-3">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="truncate font-mono text-xs font-semibold text-primary">{parcel.trackingNumber}</p>
                          <p className="mt-1 text-[10px] text-muted-foreground">ID {parcel.id.slice(0, 8)}…</p>
                        </div>
                        <StatusBadge status="PICKED_UP" />
                      </div>
                      <p className="text-sm text-muted-foreground">Destination: {parcel.dropoffCity}</p>
                      <div className="flex gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={() => setSelectedParcel({ parcel, mode: "details" })} className="flex-1">
                          View details
                        </Button>
                        <Button type="button" size="sm" onClick={() => setSelectedParcel({ parcel, mode: "assignment", assignmentType: "DELIVERY" })} className="flex-1">
                          Assign delivery
                        </Button>
                      </div>
                    </article>
                  ))}
                </div>
              </>
            )}
            <Pagination
              page={deliveryPage?.page ?? deliveryPageNumber}
              totalPages={deliveryPage?.totalPages ?? 0}
              totalItems={deliveryPage?.totalItems ?? 0}
              pageSize={PARCEL_PAGE_SIZE}
              loading={deliveryLoading}
              onPrevious={() => changeDeliveryPage(Math.max(0, deliveryPageNumber - 1))}
              onNext={() => changeDeliveryPage(Math.min((deliveryPage?.totalPages ?? 1) - 1, deliveryPageNumber + 1))}
            />
          </CardContent>
        </Card>
      </section>

      <section id="active-deliveries" className="scroll-mt-6 space-y-4">
        <SectionHeading
          eyebrow="In progress"
          title="Active deliveries"
          description="Parcels with an active delivery assignment, sorted by last update."
        />
        <Card className="rounded-xl border-border bg-card shadow-xs">
          <CardContent className="p-4 sm:p-5">
            {activeError && <ErrorMessage message={activeError} />}
            {activeLoading && <LoadingRows label="Loading active deliveries" />}
            {!activeLoading && !activeError && activeParcels.length === 0 && (
              <EmptyState
                icon={CheckCircle2}
                title="No active deliveries"
                description="No active delivery assignment records were returned."
              />
            )}
            {!activeLoading && !activeError && activeParcels.length > 0 && (
              <>
                <div className="hidden overflow-x-auto rounded-lg border border-border md:block">
                  <table className="w-full min-w-[760px] text-left text-sm">
                    <thead className="bg-muted/60 text-[11px] uppercase tracking-wider text-muted-foreground">
                      <tr>
                        <th className="px-4 py-3 font-semibold">Parcel</th>
                        <th className="px-4 py-3 font-semibold">Destination</th>
                        <th className="px-4 py-3 font-semibold">Delivery courier</th>
                        <th className="px-4 py-3 font-semibold">Status</th>
                        <th className="px-4 py-3 font-semibold">Last updated</th>
                        <th className="px-4 py-3 font-semibold">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {activeParcels.map((parcel) => (
                        <ActiveDeliveryRow
                          key={parcel.id}
                          parcel={parcel}
                          courierName={parcel.assignedCourierId ? courierById.get(parcel.assignedCourierId) : undefined}
                          onDetails={() => setSelectedParcel({ parcel, mode: "details" })}
                        />
                      ))}
                    </tbody>
                  </table>
                </div>
                <div className="space-y-3 md:hidden">
                  {activeParcels.map((parcel) => (
                    <ActiveDeliveryCard
                      key={parcel.id}
                      parcel={parcel}
                      courierName={parcel.assignedCourierId ? courierById.get(parcel.assignedCourierId) : undefined}
                      onDetails={() => setSelectedParcel({ parcel, mode: "details" })}
                    />
                  ))}
                </div>
              </>
            )}
          </CardContent>
        </Card>
      </section>

      <section id="couriers" className="scroll-mt-6 space-y-4">
        <SectionHeading
          eyebrow="Fleet"
          title="Courier accounts"
          description="Availability is derived from active assignment records, not account status alone."
        />
        <Card className="rounded-xl border-border bg-card shadow-xs">
          <CardContent className="space-y-4 p-4 sm:p-5">
            <div className="flex items-start gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
              <Info className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
              <p>
                AVAILABLE couriers have no active pickup or delivery task. BUSY couriers already hold a task; INACTIVE couriers have disabled accounts. The backend revalidates availability when an assignment is submitted.
              </p>
            </div>
            {couriersError && <ErrorMessage message={couriersError} />}
            {couriersLoading && <LoadingRows label="Loading courier accounts" />}
            {!couriersLoading && !couriersError && courierItems.length === 0 && (
              <EmptyState
                icon={Users}
                title="No courier accounts found"
                description="Create courier accounts through the existing staff user management workflow."
              />
            )}
            {!couriersLoading && !couriersError && courierItems.length > 0 && (
              <>
                <div className="space-y-2">
                  {courierItems.map((courier) => (
                    <CourierAccountCard
                      key={courier.id}
                      courier={courier}
                      availability={courierAvailability(courier)}
                      assignment={assignmentByCourier.get(courier.id) ?? null}
                      onAssign={() => {
                        setParcelStatus("CREATED");
                        setSourceFilter("ALL");
                        setAssignmentFilter("UNASSIGNED");
                        setSearch("");
                        setParcelPageNumber(0);
                        setParcelPageLoading(true);
                        document.getElementById("pickup-requests")?.scrollIntoView({ behavior: "smooth" });
                      }}
                    />
                  ))}
                </div>
                <Pagination
                  page={courierPageNumber}
                  totalPages={courierTotalPages}
                  totalItems={couriers.length}
                  pageSize={COURIER_PAGE_SIZE}
                  loading={couriersLoading}
                  onPrevious={() => changeCourierPage(Math.max(0, courierPageNumber - 1))}
                  onNext={() => changeCourierPage(Math.min(courierTotalPages - 1, courierPageNumber + 1))}
                />
              </>
            )}
          </CardContent>
        </Card>
      </section>

      <section id="parcel-tracking" className="scroll-mt-6">
        <div className="flex flex-col gap-3 rounded-xl border border-primary/20 bg-accent p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
          <div className="flex items-start gap-3">
            <MapPin className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden="true" />
            <div>
              <h2 className="text-sm font-bold text-foreground">Parcel tracking</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                Open parcel details for the staff timeline, or use public tracking with a tracking number.
              </p>
            </div>
          </div>
          <Link
            href="/track"
            className="inline-flex h-9 items-center justify-center gap-2 rounded-lg border border-primary/30 bg-card px-3 text-sm font-medium text-primary transition-colors hover:bg-primary/5"
          >
            Open tracking
            <ArrowUpRight className="size-4" aria-hidden="true" />
          </Link>
        </div>
      </section>

      {selectedParcel && (
        <ParcelDialog
          parcel={selectedParcel.parcel}
          mode={selectedParcel.mode}
          assignmentType={selectedParcel.mode === "assignment" ? selectedParcel.assignmentType : null}
          couriers={couriers}
          workloads={workloads}
          couriersLoading={couriersLoading}
          onAssigned={(assignment) => {
            setSelectedParcel(null);
            refreshDashboard();
            setAssignmentFeedback(
              `${assignment.type === "PICKUP" ? "Pickup" : "Delivery"} courier assigned successfully.`,
            );
          }}
          onClose={() => setSelectedParcel(null)}
        />
      )}
    </div>
  );
}

function MetricCard({
  label,
  value,
  description,
  icon: Icon,
  unavailable = false,
  loading = false,
}: MetricCardProps & { loading?: boolean }) {
  return (
    <Card className="rounded-xl border-border bg-card shadow-xs">
      <CardContent className="flex h-full items-start justify-between gap-3 p-4">
        <div className="min-w-0">
          <p className="text-xs font-medium text-muted-foreground">{label}</p>
          {loading && !unavailable ? (
            <div className="mt-2 h-8 w-16 animate-pulse rounded-md bg-muted" aria-hidden="true" />
          ) : (
            <p className="mt-1 text-2xl font-extrabold tracking-tight text-foreground">
              {unavailable || value === null ? "—" : value.toLocaleString("en-US")}
            </p>
          )}
          <p className="mt-2 text-[11px] leading-relaxed text-muted-foreground">{description}</p>
        </div>
        <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-lg border border-primary/15 bg-accent text-primary">
          <Icon className="size-5" aria-hidden="true" />
        </span>
      </CardContent>
    </Card>
  );
}

function SectionHeading({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description: string;
}) {
  return (
    <div className="space-y-1">
      <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-primary">{eyebrow}</p>
      <h2 className="text-lg font-bold tracking-tight text-foreground">{title}</h2>
      <p className="max-w-3xl text-sm text-muted-foreground">{description}</p>
    </div>
  );
}

function SectionLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <a
      href={href}
      className="shrink-0 rounded-full border border-border bg-card px-3 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:border-primary/30 hover:bg-accent hover:text-primary focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      {children}
    </a>
  );
}

function ParcelTableRow({
  parcel,
  courierName,
  onDetails,
  onAssign,
}: {
  parcel: DispatchParcelItem;
  courierName?: string;
  onDetails: () => void;
  onAssign: () => void;
}) {
  return (
    <tr className="align-top">
      <td className="px-4 py-3">
        <span className="block font-mono text-xs font-semibold text-primary">{parcel.trackingNumber}</span>
        <span className="mt-1 block text-[10px] text-muted-foreground">ID {parcel.id.slice(0, 8)}…</span>
      </td>
      <td className="px-4 py-3 text-foreground">{parcelSourceLabel(parcel.source)}</td>
      <td className="px-4 py-3 text-muted-foreground">{parcelLocation(parcel)}</td>
      <td className="px-4 py-3"><StatusBadge status={parcel.status} /></td>
      <td className="px-4 py-3 text-xs text-muted-foreground">{formatDate(parcel.createdAt)}</td>
      <td className="px-4 py-3 text-xs text-muted-foreground">
        {parcel.assignedCourierId ? courierName ?? parcel.assignedCourierId : "Unassigned"}
      </td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-1">
          <Button type="button" variant="ghost" size="sm" onClick={onDetails} aria-label={`View ${parcel.trackingNumber} details`}>
            View
          </Button>
          {parcel.status === "CREATED" && (
            <Button type="button" variant="outline" size="sm" onClick={onAssign}>
              {parcel.assignedCourierId ? "Reassign pickup" : "Assign pickup"}
            </Button>
          )}
        </div>
      </td>
    </tr>
  );
}

function ParcelMobileCard({
  parcel,
  courierName,
  onDetails,
  onAssign,
}: {
  parcel: DispatchParcelItem;
  courierName?: string;
  onDetails: () => void;
  onAssign: () => void;
}) {
  return (
    <article className="space-y-3 rounded-lg border border-border bg-background p-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-mono text-xs font-semibold text-primary">{parcel.trackingNumber}</p>
          <p className="mt-1 text-[10px] text-muted-foreground">ID {parcel.id.slice(0, 8)}… · {parcelSourceLabel(parcel.source)}</p>
        </div>
        <StatusBadge status={parcel.status} />
      </div>
      <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
        <MapPin className="size-4 shrink-0 text-primary" aria-hidden="true" />
        <span className="truncate">{parcelLocation(parcel)}</span>
      </p>
      <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
        <span>Requested {formatDate(parcel.createdAt)}</span>
        <span className="truncate">Courier: {parcel.assignedCourierId ? courierName ?? parcel.assignedCourierId : "Unassigned"}</span>
      </div>
      <div className="flex gap-2">
        <Button type="button" variant="outline" size="sm" onClick={onDetails} className="flex-1">
          View details
        </Button>
        {parcel.status === "CREATED" && (
          <Button type="button" size="sm" onClick={onAssign} className="flex-1">
            {parcel.assignedCourierId ? "Reassign pickup" : "Assign pickup"}
          </Button>
        )}
      </div>
    </article>
  );
}

function ActiveDeliveryRow({
  parcel,
  courierName,
  onDetails,
}: {
  parcel: DispatchParcelItem;
  courierName?: string;
  onDetails: () => void;
}) {
  return (
    <tr>
      <td className="px-4 py-3">
        <span className="block font-mono text-xs font-semibold text-primary">{parcel.trackingNumber}</span>
        <span className="mt-1 block text-[10px] text-muted-foreground">ID {parcel.id.slice(0, 8)}…</span>
      </td>
      <td className="px-4 py-3 text-muted-foreground">{parcel.dropoffCity}</td>
      <td className="px-4 py-3 text-xs text-muted-foreground">{courierName ?? parcel.assignedCourierId ?? "Not assigned"}</td>
      <td className="px-4 py-3"><StatusBadge status={parcel.status} /></td>
      <td className="px-4 py-3 text-xs text-muted-foreground">{formatDateTime(parcel.updatedAt)}</td>
      <td className="px-4 py-3">
        <Button type="button" variant="ghost" size="sm" onClick={onDetails}>View details</Button>
      </td>
    </tr>
  );
}

function ActiveDeliveryCard({
  parcel,
  courierName,
  onDetails,
}: {
  parcel: DispatchParcelItem;
  courierName?: string;
  onDetails: () => void;
}) {
  return (
    <article className="flex flex-col gap-3 rounded-lg border border-border bg-background p-3">
      <div className="flex items-start justify-between gap-3">
        <span className="min-w-0">
          <span className="block truncate font-mono text-xs font-semibold text-primary">{parcel.trackingNumber}</span>
          <span className="mt-1 block text-xs text-muted-foreground">Destination: {parcel.dropoffCity}</span>
        </span>
        <StatusBadge status={parcel.status} />
      </div>
      <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
        <span>Courier: {courierName ?? parcel.assignedCourierId ?? "Not assigned"}</span>
        <span>{formatDateTime(parcel.updatedAt)}</span>
      </div>
      <Button type="button" variant="outline" size="sm" onClick={onDetails}>View details</Button>
    </article>
  );
}

function CourierAccountCard({
  courier,
  availability,
  assignment,
  onAssign,
}: {
  courier: UserResponse;
  availability: "AVAILABLE" | "BUSY" | "INACTIVE" | "UNKNOWN";
  assignment: DispatchAssignment | null;
  onAssign: () => void;
}) {
  return (
    <article className="flex flex-col gap-3 rounded-lg border border-border bg-background p-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex min-w-0 items-center gap-3">
        <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-accent text-primary">
          <UserRound className="size-4" aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-foreground">{courier.fullName}</p>
          <p className="truncate font-mono text-[10px] text-muted-foreground">{courier.id}</p>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2 text-xs sm:min-w-[440px] sm:grid-cols-3">
        <CourierValue label="Availability" value={availability} />
        <CourierValue
          label="Current parcel"
          value={assignment ? `ID ${assignment.parcelId.slice(0, 8)}…` : "None"}
        />
        <CourierValue label="Active task type" value={assignment?.type ?? "None"} />
      </div>
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={availability !== "AVAILABLE"}
        onClick={onAssign}
        title={availability === "AVAILABLE" ? "Choose an unassigned parcel" : "Only available couriers can be assigned"}
      >
        {availability === "AVAILABLE" ? "Select parcel" : "Unavailable"}
      </Button>
    </article>
  );
}

function CourierValue({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-muted/60 px-2.5 py-2">
      <p className="text-[10px] font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-0.5 font-semibold text-foreground">{value}</p>
    </div>
  );
}

function Pagination({
  page,
  totalPages,
  totalItems,
  pageSize,
  loading,
  onPrevious,
  onNext,
}: {
  page: number;
  totalPages: number;
  totalItems: number;
  pageSize: number;
  loading: boolean;
  onPrevious: () => void;
  onNext: () => void;
}) {
  const firstItem = totalItems === 0 ? 0 : page * pageSize + 1;
  const lastItem = Math.min((page + 1) * pageSize, totalItems);

  return (
    <div className="flex flex-col gap-3 border-t border-border pt-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-xs text-muted-foreground">
        Showing {firstItem}–{lastItem} of {totalItems}
      </p>
      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onPrevious}
          disabled={loading || page <= 0}
          aria-label="Previous page"
        >
          <ChevronLeft aria-hidden="true" />
          Previous
        </Button>
        <span className="min-w-16 text-center text-xs text-muted-foreground">
          {totalPages === 0 ? "0 / 0" : `${page + 1} / ${totalPages}`}
        </span>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onNext}
          disabled={loading || totalPages === 0 || page >= totalPages - 1}
          aria-label="Next page"
        >
          Next
          <ChevronRight aria-hidden="true" />
        </Button>
      </div>
    </div>
  );
}

function LoadingRows({ label }: { label: string }) {
  return (
    <div role="status" aria-label={label} className="space-y-2">
      {[0, 1, 2].map((row) => (
        <div key={row} className="h-14 animate-pulse rounded-lg border border-border bg-muted/60" />
      ))}
      <span className="sr-only">{label}</span>
    </div>
  );
}

function ErrorMessage({ message }: { message: string }) {
  return (
    <div role="alert" className="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
      <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}

function EmptyState({
  icon: Icon,
  title,
  description,
}: {
  icon: typeof Search;
  title: string;
  description: string;
}) {
  return (
    <div className="rounded-lg border border-dashed border-border bg-background p-8 text-center">
      <Icon className="mx-auto size-8 text-muted-foreground" aria-hidden="true" />
      <h3 className="mt-3 text-sm font-semibold text-foreground">{title}</h3>
      <p className="mx-auto mt-1 max-w-md text-xs text-muted-foreground">{description}</p>
    </div>
  );
}
