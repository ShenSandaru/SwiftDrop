"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  AlertCircle,
  ArrowRight,
  ArrowUpRight,
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  LayoutDashboard,
  MapPin,
  MapPinned,
  Package,
  PackageCheck,
  PackagePlus,
  RefreshCw,
  Search,
  Truck,
  Warehouse,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { StatusBadge } from "@/components/delivery/status-badge";
import { formatDate, formatDateTime, STATUS_LABELS } from "@/components/delivery/format";
import { HubBoard } from "@/components/dispatch/hub-board";
import { ParcelDialog } from "@/components/dispatch/parcel-dialog";
import { formatApiError, type PageResponse } from "@/lib/api";
import type { ParcelStatus } from "@/lib/delivery";
import type {
  DispatchAssignment,
  DispatchAssignmentType,
  DispatchCourier,
  DispatchParcelItem,
} from "@/lib/dispatch";
import {
  countDispatchParcels,
  getCourierWorkloads,
  listActiveDispatchAssignments,
  listAllActiveDispatchAssignments,
  listAllDispatchCouriers,
  listDispatchParcels,
} from "@/lib/dispatch";

const PARCEL_PAGE_SIZE = 8;
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

type DispatchMainTab = "overview" | "pickup" | "warehouse" | "delivery" | "tracking";

interface DashboardMetrics {
  total: number | null;
  pendingPickup: number | null;
  activePickups: number | null;
  activeDeliveries: number | null;
  delivered: number | null;
}

const EMPTY_METRICS: DashboardMetrics = {
  total: null,
  pendingPickup: null,
  activePickups: null,
  activeDeliveries: null,
  delivered: null,
};

export default function DispatchPage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<DispatchMainTab>("overview");
  const [refreshKey, setRefreshKey] = useState(0);

  const today = useMemo(
    () =>
      new Intl.DateTimeFormat("en-US", {
        timeZone: "Asia/Colombo",
        dateStyle: "medium",
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

  const [warehouseParcels, setWarehouseParcels] = useState<DispatchParcelItem[]>([]);
  const [warehouseLoading, setWarehouseLoading] = useState(true);
  const [warehouseError, setWarehouseError] = useState<string | null>(null);

  const [couriers, setCouriers] = useState<DispatchCourier[]>([]);
  const [workloads, setWorkloads] = useState<Record<string, number> | null>(null);
  const [activeAssignments, setActiveAssignments] = useState<DispatchAssignment[]>([]);
  const [couriersLoading, setCouriersLoading] = useState(true);
  const [assignmentFeedback, setAssignmentFeedback] = useState<string | null>(null);

  const [quickTrackCode, setQuickTrackCode] = useState("");

  const [selectedParcel, setSelectedParcel] = useState<
    | { parcel: DispatchParcelItem; mode: "details" }
    | { parcel: DispatchParcelItem; mode: "assignment"; assignmentType: DispatchAssignmentType }
    | null
  >(null);

  // 1. Overall Metrics
  useEffect(() => {
    let active = true;
    const requests = [
      countDispatchParcels(),
      listDispatchParcels({ page: 0, size: 1, status: "CREATED", assigned: false }),
      listActiveDispatchAssignments("PICKUP", 0, 1),
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
        activeDeliveries: readCount(3),
        delivered: readCount(4),
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

  // 2. Pickup / Main Parcel Page
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

  // 3. Active Deliveries
  useEffect(() => {
    let active = true;
    Promise.all([
      listDispatchParcels({ page: 0, size: 10, status: "PICKED_UP", assigned: true }),
      listDispatchParcels({ page: 0, size: 10, status: "IN_TRANSIT", assigned: true }),
      listDispatchParcels({ page: 0, size: 10, status: "OUT_FOR_DELIVERY", assigned: true }),
    ])
      .then((pages) => {
        if (active) {
          setActiveParcels(
            pages
              .flatMap((page) => page.items)
              .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
              .slice(0, 15),
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

  // 4. Warehouse & Hub Parcels (Items collected & being sorted / transferred)
  useEffect(() => {
    let active = true;
    Promise.all([
      ...(["PICKED_UP", "AT_HUB", "IN_TRANSIT", "DELIVERY_ATTEMPT_FAILED", "RETURNING"] as const).map((status) =>
        listDispatchParcels({ page: 0, size: 25, status })),
    ])
      .then((pages) => {
        if (active) {
          setWarehouseParcels(
            pages
              .flatMap((page) => page.items)
              .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt)),
          );
          setWarehouseError(null);
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setWarehouseError(formatApiError(failure));
        }
      })
      .finally(() => {
        if (active) {
          setWarehouseLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [refreshKey]);

  // 5. Couriers, Workloads, Active Assignments
  useEffect(() => {
    let active = true;
    Promise.allSettled([
      listAllDispatchCouriers(),
      getCourierWorkloads(),
      listAllActiveDispatchAssignments("PICKUP"),
      listAllActiveDispatchAssignments("DELIVERY"),
    ])
      .then(([couriersResult, workloadsResult, pickupsResult, deliveriesResult]) => {
        if (!active) {
          return;
        }

        if (couriersResult.status === "fulfilled") {
          setCouriers(couriersResult.value);
        } else {
          setCouriers([]);
        }

        if (workloadsResult.status === "fulfilled") {
          setWorkloads(workloadsResult.value);
        } else {
          setWorkloads(null);
        }

        const pickups = pickupsResult.status === "fulfilled" ? pickupsResult.value : [];
        const deliveries = deliveriesResult.status === "fulfilled" ? deliveriesResult.value : [];
        setActiveAssignments([...pickups, ...deliveries]);
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

  const visibleParcels = useMemo(() => parcelPage?.items ?? [], [parcelPage]);
  const courierById = useMemo(
    () => new Map(couriers.map((courier) => [courier.id, courier.fullName])),
    [couriers],
  );

  const dialogCouriers = useMemo<DispatchCourier[]>(() => {
    if (couriers.length > 0) {
      return couriers;
    }

    const courierIds = new Set<string>();
    if (workloads) {
      for (const id of Object.keys(workloads)) {
        courierIds.add(id);
      }
    }
    for (const assignment of activeAssignments) {
      courierIds.add(assignment.courierId);
    }
    for (const parcel of activeParcels) {
      if (parcel.assignedCourierId) {
        courierIds.add(parcel.assignedCourierId);
      }
    }
    for (const parcel of visibleParcels) {
      if (parcel.assignedCourierId) {
        courierIds.add(parcel.assignedCourierId);
      }
    }

    return Array.from(courierIds).map((id) => ({
      id,
      fullName: `Courier ${id.slice(0, 8)}`,
    }));
  }, [couriers, workloads, activeAssignments, activeParcels, visibleParcels]);

  function refreshDashboard() {
    setMetricsLoading(true);
    setParcelPageLoading(true);
    setActiveLoading(true);
    setWarehouseLoading(true);
    setCouriersLoading(true);
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

  const warehouseCount = warehouseParcels.length;

  return (
    <div className="space-y-6">
      {/* Top Operations Header & Quick Status */}
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-card p-4 shadow-xs sm:flex-row sm:items-center sm:justify-between sm:p-5">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1.5 font-bold text-primary">
              <Truck className="size-3.5" aria-hidden="true" />
              Dispatch Control Tower
            </span>
            <span aria-hidden="true">·</span>
            <span className="inline-flex items-center gap-1.5">
              <CalendarDays className="size-3.5" aria-hidden="true" />
              <time suppressHydrationWarning>{today}</time>
            </span>
            <span aria-hidden="true">·</span>
            <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
              Island Network Active
            </span>
          </div>
          <p className="text-sm text-muted-foreground">
            Coordinate first-mile pickups, warehouse sorting & branch transfers, and final-mile deliveries.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={refreshDashboard}
          disabled={metricsLoading || parcelPageLoading || activeLoading || couriersLoading}
          className="w-full shrink-0 sm:w-auto"
        >
          <RefreshCw
            className={`size-3.5 mr-1.5 ${
              metricsLoading || parcelPageLoading || activeLoading || couriersLoading
                ? "animate-spin"
                : ""
            }`}
            aria-hidden="true"
          />
          Refresh data
        </Button>
      </div>

      {/* Main Operational Tabs Bar */}
      <div className="border-b border-border">
        <nav aria-label="Dispatch navigation tabs" className="flex gap-2 overflow-x-auto pb-px">
          <TabButton
            active={activeTab === "overview"}
            onClick={() => setActiveTab("overview")}
            icon={LayoutDashboard}
            label="Overview"
          />
          <TabButton
            active={activeTab === "pickup"}
            onClick={() => setActiveTab("pickup")}
            icon={PackagePlus}
            label="Pickup Operations"
            badge={metrics.pendingPickup ?? 0}
            badgeVariant="warning"
          />
          <TabButton
            active={activeTab === "warehouse"}
            onClick={() => setActiveTab("warehouse")}
            icon={Warehouse}
            label="Warehouse & Sorting"
            badge={warehouseCount}
            badgeVariant="accent"
          />
          <TabButton
            active={activeTab === "delivery"}
            onClick={() => setActiveTab("delivery")}
            icon={Truck}
            label="Delivery Operations"
            badge={metrics.activeDeliveries ?? 0}
            badgeVariant="primary"
          />
          <TabButton
            active={activeTab === "tracking"}
            onClick={() => setActiveTab("tracking")}
            icon={MapPin}
            label="Live Tracking"
          />
        </nav>
      </div>

      {assignmentFeedback && (
        <div role="status" className="flex items-center justify-between gap-3 rounded-xl border border-primary/30 bg-accent p-4 text-sm font-medium text-primary">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="size-4 shrink-0 text-primary" aria-hidden="true" />
            <span>{assignmentFeedback}</span>
          </div>
          <button
            type="button"
            onClick={() => setAssignmentFeedback(null)}
            className="text-primary hover:opacity-75 text-xs font-semibold"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* ======================================================== */}
      {/* TAB 1: OVERVIEW & OPERATIONS PIPELINE                    */}
      {/* ======================================================== */}
      {activeTab === "overview" && (
        <div className="space-y-6">
          {/* Island Dispatch Hero Banner */}
          <section className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-primary via-primary/95 to-primary/90 px-6 py-7 text-primary-foreground sm:px-8 sm:py-8 shadow-sm">
            <svg
              className="pointer-events-none absolute -right-12 -top-10 h-64 w-96 opacity-20"
              viewBox="0 0 320 220"
              fill="none"
              aria-hidden="true"
            >
              <path
                d="M10 190 C 80 120, 120 200, 180 120 S 260 40, 310 20"
                stroke="currentColor"
                strokeWidth="3.5"
                strokeDasharray="10 10"
                strokeLinecap="round"
              />
              <circle cx="10" cy="190" r="7" fill="currentColor" />
              <circle cx="180" cy="120" r="6" fill="currentColor" />
              <circle cx="310" cy="20" r="8" fill="currentColor" />
            </svg>
            <div className="relative max-w-2xl space-y-3">
              <p className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.15em] opacity-85">
                <MapPinned className="size-4" aria-hidden="true" /> Island-Wide Logistic Command
              </p>
              <h2 className="text-2xl font-extrabold leading-tight sm:text-3xl">
                Dispatch, Warehouse & Delivery Control
              </h2>
              <p className="text-sm opacity-90 sm:text-base leading-relaxed">
                Connect customer bookings to couriers, intake and sort parcels at regional warehouse hubs, and assign transfer routes to destination branches for final delivery.
              </p>
              <div className="flex flex-wrap gap-2.5 pt-2">
                <Button
                  type="button"
                  size="sm"
                  onClick={() => setActiveTab("pickup")}
                  className="bg-primary-foreground text-primary hover:bg-primary-foreground/90 font-semibold"
                >
                  <PackagePlus className="size-4 mr-1.5" /> First-Mile Pickups ({metrics.pendingPickup ?? 0})
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => setActiveTab("warehouse")}
                  className="border-primary-foreground/40 bg-primary-foreground/10 text-primary-foreground hover:bg-primary-foreground/20 font-semibold"
                >
                  <Warehouse className="size-4 mr-1.5" /> Warehouse & Sorting ({warehouseCount})
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => setActiveTab("delivery")}
                  className="border-primary-foreground/40 bg-primary-foreground/10 text-primary-foreground hover:bg-primary-foreground/20 font-semibold"
                >
                  <Truck className="size-4 mr-1.5" /> Final Deliveries ({metrics.activeDeliveries ?? 0})
                </Button>
              </div>
            </div>
          </section>

          {/* Quick Metrics Cards */}
          {metricsError && (
            <div className="flex items-center gap-2 rounded-xl border border-destructive/20 bg-destructive/10 p-3.5 text-xs text-destructive">
              <AlertCircle className="size-4 shrink-0" aria-hidden="true" />
              <span>{metricsError}</span>
            </div>
          )}
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            <MetricCard
              label="Total parcels"
              value={metrics.total}
              description="Visible to dispatch network"
              icon={Package}
              loading={metricsLoading}
            />
            <MetricCard
              label="Pending pickups"
              value={metrics.pendingPickup}
              description="Awaiting courier pickup"
              icon={ClipboardList}
              loading={metricsLoading}
            />
            <MetricCard
              label="Warehouse & sorting"
              value={warehouseCount}
              description="Inbound intake & transfers"
              icon={Warehouse}
              loading={warehouseLoading}
            />
            <MetricCard
              label="Active deliveries"
              value={metrics.activeDeliveries}
              description="Out for delivery runs"
              icon={Truck}
              loading={metricsLoading}
            />
            <MetricCard
              label="Delivered parcels"
              value={metrics.delivered}
              description="Successfully completed"
              icon={PackageCheck}
              loading={metricsLoading}
            />
          </div>

          {/* 3-Stage Interactive Lifecycle Cards */}
          <section className="space-y-4">
            <SectionHeading
              id="lifecycle-overview"
              eyebrow="Workflow pipeline"
              title="Parcel Delivery Lifecycle"
              description="Track parcels through the three mandatory operational phases from pickup to final recipient."
            />
            <div className="grid gap-4 md:grid-cols-3">
              {/* Stage 1 */}
              <div className="flex flex-col justify-between rounded-xl border border-border bg-card p-5 shadow-xs transition-shadow hover:shadow-sm">
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary font-bold text-sm">
                      1
                    </span>
                    <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-semibold text-muted-foreground">
                      First-Mile
                    </span>
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-foreground">Pickup Operations</h3>
                    <p className="mt-1 text-xs text-muted-foreground leading-relaxed">
                      Customer bookings and merchant parcel orders waiting for courier pickup assignment. Couriers collect items from senders.
                    </p>
                  </div>
                  <div className="rounded-lg border border-border bg-muted/30 p-2.5 text-xs">
                    <span className="text-muted-foreground">Active queue: </span>
                    <strong className="text-foreground">{metrics.pendingPickup ?? 0} pending pickups</strong>
                  </div>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setActiveTab("pickup")}
                  className="mt-4 w-full justify-between"
                >
                  <span>Open Pickup Queue</span>
                  <ArrowRight className="size-4" />
                </Button>
              </div>

              {/* Stage 2 */}
              <div className="flex flex-col justify-between rounded-xl border border-border bg-card p-5 shadow-xs transition-shadow hover:shadow-sm">
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary font-bold text-sm">
                      2
                    </span>
                    <span className="rounded-full bg-accent px-2.5 py-0.5 text-xs font-semibold text-primary">
                      Warehouse Hub
                    </span>
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-foreground">Warehouse & Hub Sorting</h3>
                    <p className="mt-1 text-xs text-muted-foreground leading-relaxed">
                      Picked-up items arrive at origin sorting hub. Dispatchers sort items by destination province and assign inter-hub transfer routes to nearest branches.
                    </p>
                  </div>
                  <div className="rounded-lg border border-border bg-muted/30 p-2.5 text-xs">
                    <span className="text-muted-foreground">In hub & transit: </span>
                    <strong className="text-foreground">{warehouseCount} items in sorting</strong>
                  </div>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setActiveTab("warehouse")}
                  className="mt-4 w-full justify-between"
                >
                  <span>Manage Warehouse Hub</span>
                  <ArrowRight className="size-4" />
                </Button>
              </div>

              {/* Stage 3 */}
              <div className="flex flex-col justify-between rounded-xl border border-border bg-card p-5 shadow-xs transition-shadow hover:shadow-sm">
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary font-bold text-sm">
                      3
                    </span>
                    <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-semibold text-muted-foreground">
                      Final-Mile
                    </span>
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-foreground">Delivery Operations</h3>
                    <p className="mt-1 text-xs text-muted-foreground leading-relaxed">
                      Parcels arriving at destination branches are assigned to delivery couriers for final handover, live GPS tracking, and delivery proof capture.
                    </p>
                  </div>
                  <div className="rounded-lg border border-border bg-muted/30 p-2.5 text-xs">
                    <span className="text-muted-foreground">In delivery: </span>
                    <strong className="text-foreground">{metrics.activeDeliveries ?? 0} active runs</strong>
                  </div>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setActiveTab("delivery")}
                  className="mt-4 w-full justify-between"
                >
                  <span>Open Deliveries</span>
                  <ArrowRight className="size-4" />
                </Button>
              </div>
            </div>
          </section>

          {/* Quick Tracking Search Box */}
          <Card className="rounded-xl border-border bg-card shadow-xs">
            <CardContent className="p-5">
              <form
                className="flex flex-col gap-3 sm:flex-row sm:items-center"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (quickTrackCode.trim()) {
                    router.push(`/track?code=${encodeURIComponent(quickTrackCode.trim())}`);
                  }
                }}
              >
                <div className="flex size-10 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
                  <Search className="size-5" />
                </div>
                <div className="min-w-0 flex-1">
                  <label htmlFor="quick-track-input" className="block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Quick Island Tracking Lookup
                  </label>
                  <p className="text-xs text-muted-foreground">
                    Enter any tracking number to inspect real-time events, coordinates, and journey history.
                  </p>
                </div>
                <div className="flex w-full gap-2 sm:w-auto">
                  <Input
                    id="quick-track-input"
                    value={quickTrackCode}
                    onChange={(e) => setQuickTrackCode(e.target.value)}
                    placeholder="e.g. SDNTQ67FQDFA"
                    className="h-10 min-w-56"
                  />
                  <Button type="submit" size="default">
                    Track
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      )}

      {/* ======================================================== */}
      {/* TAB 2: PICKUP OPERATIONS                                */}
      {/* ======================================================== */}
      {activeTab === "pickup" && (
        <section aria-labelledby="heading-pickup-requests" className="space-y-4">
          <SectionHeading
            id="heading-pickup-requests"
            eyebrow="First-mile queue"
            title="Pickup Requests & Courier Dispatch"
            description="Review customer booking requests and merchant orders awaiting first-mile courier pickup."
          />
          <Card className="rounded-xl border-border bg-card py-0 shadow-xs">
            <CardContent className="space-y-4 p-4 sm:p-5">
              {/* Filters */}
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto]">
                <label className="relative block sm:col-span-2 lg:col-span-1">
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
                    className="h-10 bg-background pl-9"
                  />
                </label>
                <label className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground sm:justify-start">
                  <span className="shrink-0">Status</span>
                  <select
                    value={parcelStatus}
                    onChange={(event) => changeParcelStatus(event.target.value)}
                    className="h-10 w-full min-w-36 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 sm:w-auto"
                  >
                    {FILTER_STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {status === "ALL" ? "All statuses" : STATUS_LABELS[status]}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground sm:justify-start">
                  <span className="shrink-0">Source</span>
                  <select
                    value={sourceFilter}
                    onChange={(event) => {
                      setSourceFilter(event.target.value as SourceFilter);
                      setParcelPageNumber(0);
                      setParcelPageLoading(true);
                    }}
                    className="h-10 w-full min-w-32 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 sm:w-auto"
                  >
                    {SOURCE_FILTERS.map((source) => (
                      <option key={source} value={source}>
                        {source === "ALL" ? "All sources" : source === "CUSTOMER" ? "Customer" : "Merchant"}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="flex items-center justify-between gap-2 text-xs font-medium text-muted-foreground sm:col-span-2 sm:justify-start lg:col-span-1">
                  <span className="shrink-0">Assignment</span>
                  <select
                    value={assignmentFilter}
                    onChange={(event) => {
                      setAssignmentFilter(event.target.value as typeof assignmentFilter);
                      setParcelPageNumber(0);
                      setParcelPageLoading(true);
                    }}
                    className="h-10 w-full min-w-36 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 sm:w-auto"
                  >
                    <option value="ALL">All assignments</option>
                    <option value="UNASSIGNED">Unassigned</option>
                    <option value="ASSIGNED">Assigned</option>
                  </select>
                </label>
              </div>

              {parcelPageError && <ErrorMessage message={parcelPageError} />}
              {parcelPageLoading && <LoadingRows label="Loading pickup requests" />}
              {!parcelPageLoading && !parcelPageError && visibleParcels.length === 0 && (
                <EmptyState
                  icon={Search}
                  title={parcelPage?.items.length ? "No parcels match these filters" : "No pickup requests found"}
                  description={
                    parcelPage?.items.length
                      ? "Try another search, status, or source filter."
                      : "There are currently no parcels in the pickup queue."
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
                          <th className="px-4 py-3 font-semibold">Source</th>
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
                            onAssign={() =>
                              setSelectedParcel({
                                parcel,
                                mode: "assignment",
                                assignmentType: "PICKUP",
                              })
                            }
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
                        onAssign={() =>
                          setSelectedParcel({
                            parcel,
                            mode: "assignment",
                            assignmentType: "PICKUP",
                          })
                        }
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
      )}

      {/* ======================================================== */}
      {/* TAB 3: WAREHOUSE & HUB SORTING OPERATIONS                */}
      {/* ======================================================== */}
      {activeTab === "warehouse" && (
        <section aria-labelledby="heading-warehouse-ops" className="space-y-5">
          <SectionHeading
            id="heading-warehouse-ops"
            eyebrow="Hub network"
            title="Hubs & line haul"
            description="Scan picked-up parcels in at their origin branch, send them along their route through the central hub, then hand them to a delivery courier at the destination branch. Failed attempts come back here for another try or the return."
          />
          <HubBoard
            parcels={warehouseParcels}
            couriers={dialogCouriers}
            loading={warehouseLoading}
            error={warehouseError}
            onChanged={refreshDashboard}
            onFeedback={setAssignmentFeedback}
          />
        </section>
      )}

      {/* ======================================================== */}
      {/* TAB 4: DELIVERY OPERATIONS                               */}
      {/* ======================================================== */}
      {activeTab === "delivery" && (
        <section aria-labelledby="heading-delivery-ops" className="space-y-4">
          <SectionHeading
            id="heading-delivery-ops"
            eyebrow="Final-mile execution"
            title="Final-Mile Delivery Operations"
            description="Assign delivery couriers at destination branches, monitor active courier runs, and inspect delivery confirmations."
          />
          <Card className="rounded-xl border-border bg-card py-0 shadow-xs">
            <CardContent className="space-y-4 p-4 sm:p-5">
              <div className="grid gap-3 sm:grid-cols-3 border-b border-border pb-4">
                <div className="rounded-lg border border-border bg-muted/20 p-3">
                  <span className="text-[11px] font-semibold uppercase text-muted-foreground">Active delivery runs</span>
                  <p className="mt-1 text-2xl font-bold text-foreground">{metrics.activeDeliveries ?? 0}</p>
                </div>
                <div className="rounded-lg border border-border bg-muted/20 p-3">
                  <span className="text-[11px] font-semibold uppercase text-muted-foreground">Out on road</span>
                  <p className="mt-1 text-2xl font-bold text-foreground">{activeParcels.length}</p>
                </div>
                <div className="rounded-lg border border-border bg-muted/20 p-3">
                  <span className="text-[11px] font-semibold uppercase text-muted-foreground">Completed today</span>
                  <p className="mt-1 text-2xl font-bold text-foreground">{metrics.delivered ?? 0}</p>
                </div>
              </div>

              {activeError && <ErrorMessage message={activeError} />}
              {activeLoading && <LoadingRows label="Loading delivery assignments" />}

              {!activeLoading && !activeError && activeParcels.length === 0 && (
                <EmptyState
                  icon={CheckCircle2}
                  title="No active deliveries"
                  description="No parcels are currently in flight for delivery. Once items reach the destination branch, dispatch couriers here."
                />
              )}

              {!activeLoading && !activeError && activeParcels.length > 0 && (
                <>
                  <div className="hidden overflow-x-auto rounded-lg border border-border md:block">
                    <table className="w-full min-w-[760px] text-left text-sm">
                      <thead className="bg-muted/60 text-[11px] uppercase tracking-wider text-muted-foreground">
                        <tr>
                          <th className="px-4 py-3 font-semibold">Parcel</th>
                          <th className="px-4 py-3 font-semibold">Destination City</th>
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
      )}

      {/* ======================================================== */}
      {/* TAB 5: PARCEL TRACKING                                   */}
      {/* ======================================================== */}
      {activeTab === "tracking" && (
        <section aria-labelledby="heading-tracking" className="space-y-4">
          <SectionHeading
            id="heading-tracking"
            eyebrow="Island tracking"
            title="Live Parcel Tracking & Telemetry"
            description="Inspect real-time status timelines, coordinate updates, and public delivery proof by tracking code."
          />
          <Card className="rounded-xl border-border bg-card shadow-xs">
            <CardContent className="space-y-6 p-5">
              <form
                className="flex flex-col gap-3 sm:flex-row sm:items-center"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (quickTrackCode.trim()) {
                    router.push(`/track?code=${encodeURIComponent(quickTrackCode.trim())}`);
                  }
                }}
              >
                <div className="relative flex-1">
                  <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
                  <Input
                    value={quickTrackCode}
                    onChange={(e) => setQuickTrackCode(e.target.value)}
                    placeholder="Enter tracking number, e.g. SDNTQ67FQDFA"
                    className="h-11 pl-9"
                  />
                </div>
                <Button type="submit" size="lg" className="shrink-0">
                  <Search className="size-4 mr-2" /> Track parcel
                </Button>
              </form>

              <div className="rounded-lg border border-border bg-muted/30 p-4 text-xs text-muted-foreground space-y-2">
                <p className="font-semibold text-foreground">Staff & Public Tracking Access</p>
                <p>
                  Every parcel in the SwiftDrop network emits live Server-Sent Events (SSE) updates as couriers capture pickups, hub operators record sorting scans, and recipients acknowledge delivery proofs.
                </p>
                <p>
                  Clicking tracking from any parcel row above will open the comprehensive visual event timeline.
                </p>
              </div>
            </CardContent>
          </Card>
        </section>
      )}

      {/* Parcel Details / Courier Assignment Dialog */}
      {selectedParcel && (
        <ParcelDialog
          parcel={selectedParcel.parcel}
          mode={selectedParcel.mode}
          assignmentType={selectedParcel.mode === "assignment" ? selectedParcel.assignmentType : null}
          couriers={dialogCouriers}
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

function TabButton({
  active,
  onClick,
  icon: Icon,
  label,
  badge,
  badgeVariant = "default",
}: {
  active: boolean;
  onClick: () => void;
  icon: typeof LayoutDashboard;
  label: string;
  badge?: number;
  badgeVariant?: "default" | "primary" | "warning" | "accent";
}) {
  const badgeClasses = {
    default: "bg-muted text-muted-foreground",
    primary: "bg-primary text-primary-foreground",
    warning: "bg-amber-500/15 text-amber-700 dark:text-amber-300 font-bold",
    accent: "bg-accent text-primary font-bold",
  }[badgeVariant];

  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-2 px-3.5 py-2.5 text-xs font-semibold whitespace-nowrap transition-all rounded-lg border ${
        active
          ? "border-primary/20 bg-primary text-primary-foreground shadow-xs"
          : "border-transparent text-muted-foreground hover:bg-muted hover:text-foreground"
      }`}
    >
      <Icon className={`size-4 shrink-0 ${active ? "text-primary-foreground" : "text-primary"}`} aria-hidden="true" />
      <span>{label}</span>
      {typeof badge === "number" && (
        <span
          className={`rounded-full px-2 py-0.5 text-[10px] font-mono transition-colors ${
            active ? "bg-primary-foreground/20 text-primary-foreground" : badgeClasses
          }`}
        >
          {badge}
        </span>
      )}
    </button>
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
    <div className="flex h-full flex-col justify-between gap-3 rounded-lg border border-border bg-background p-4 shadow-2xs">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-medium text-muted-foreground">{label}</p>
          {loading && !unavailable ? (
            <div className="mt-2 h-8 w-16 animate-pulse rounded-md bg-muted" aria-hidden="true" />
          ) : (
            <p className="mt-1 font-mono text-2xl font-bold text-foreground">
              {unavailable || value === null ? "—" : value}
            </p>
          )}
        </div>
        <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
          <Icon className="size-4" aria-hidden="true" />
        </span>
      </div>
      <p className="text-xs text-muted-foreground">{description}</p>
    </div>
  );
}

function SectionHeading({
  id,
  eyebrow,
  title,
  description,
}: {
  id: string;
  eyebrow: string;
  title: string;
  description: string;
}) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-semibold uppercase tracking-wider text-primary">{eyebrow}</p>
      <h2 id={id} className="text-xl font-bold tracking-tight text-foreground">
        {title}
      </h2>
      <p className="text-xs text-muted-foreground">{description}</p>
    </div>
  );
}

function ErrorMessage({ message }: { message: string }) {
  return (
    <p role="alert" className="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
      <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>{message}</span>
    </p>
  );
}

function LoadingRows({ label }: { label: string }) {
  return (
    <div className="space-y-2" role="status" aria-label={label}>
      {Array.from({ length: 4 }).map((_, index) => (
        <div key={index} className="h-12 w-full animate-pulse rounded-lg bg-muted" />
      ))}
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
    <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-border p-8 text-center">
      <span className="inline-flex size-10 items-center justify-center rounded-full bg-muted text-muted-foreground">
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <h3 className="text-sm font-semibold text-foreground">{title}</h3>
      <p className="max-w-sm text-xs text-muted-foreground">{description}</p>
    </div>
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
    <tr className="hover:bg-muted/40">
      <td className="px-4 py-3">
        <span className="block font-mono text-xs font-semibold text-primary">{parcel.trackingNumber}</span>
        <span className="mt-0.5 block text-[10px] text-muted-foreground">
          {parcel.merchantOrderReference ? `Ref: ${parcel.merchantOrderReference}` : `ID ${parcel.id.slice(0, 8)}…`}
        </span>
      </td>
      <td className="px-4 py-3 text-foreground">{parcelSourceLabel(parcel.source)}</td>
      <td className="px-4 py-3 text-muted-foreground">{parcelLocation(parcel)}</td>
      <td className="px-4 py-3"><StatusBadge status={parcel.status} /></td>
      <td className="px-4 py-3 text-xs text-muted-foreground">{formatDate(parcel.createdAt)}</td>
      <td className="px-4 py-3 text-xs text-muted-foreground">
        {parcel.assignedCourierId ? courierName ?? `Courier ${parcel.assignedCourierId.slice(0, 8)}…` : "Unassigned"}
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
        <span className="truncate">
          Courier: {parcel.assignedCourierId ? courierName ?? `Courier ${parcel.assignedCourierId.slice(0, 8)}…` : "Unassigned"}
        </span>
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
    <tr className="hover:bg-muted/40">
      <td className="px-4 py-3">
        <Link
          href={`/track?code=${encodeURIComponent(parcel.trackingNumber)}`}
          className="font-mono text-xs font-semibold text-primary hover:underline inline-flex items-center gap-1"
        >
          {parcel.trackingNumber}
          <ArrowUpRight className="size-3" />
        </Link>
        <span className="mt-0.5 block text-[10px] text-muted-foreground">ID {parcel.id.slice(0, 8)}…</span>
      </td>
      <td className="px-4 py-3 text-muted-foreground">{parcel.dropoffCity}</td>
      <td className="px-4 py-3 text-xs text-muted-foreground">
        {parcel.assignedCourierId ? courierName ?? `Courier ${parcel.assignedCourierId.slice(0, 8)}…` : "Not assigned"}
      </td>
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
          <Link
            href={`/track?code=${encodeURIComponent(parcel.trackingNumber)}`}
            className="font-mono text-xs font-semibold text-primary hover:underline inline-flex items-center gap-1"
          >
            {parcel.trackingNumber}
            <ArrowUpRight className="size-3" />
          </Link>
          <span className="mt-1 block text-xs text-muted-foreground">Destination: {parcel.dropoffCity}</span>
        </span>
        <StatusBadge status={parcel.status} />
      </div>
      <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
        <span>
          Courier: {parcel.assignedCourierId ? courierName ?? `Courier ${parcel.assignedCourierId.slice(0, 8)}…` : "Not assigned"}
        </span>
        <span>{formatDateTime(parcel.updatedAt)}</span>
      </div>
      <Button type="button" variant="outline" size="sm" onClick={onDetails}>View details</Button>
    </article>
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

interface MetricCardProps {
  label: string;
  value: number | null;
  description: string;
  icon: typeof Package;
  unavailable?: boolean;
}

function parcelLocation(parcel: DispatchParcelItem): string {
  if (parcel.pickupCity && parcel.dropoffCity) {
    return `${parcel.pickupCity} → ${parcel.dropoffCity}`;
  }
  return parcel.dropoffCity || parcel.pickupCity || "Island route";
}

function parcelSourceLabel(source: DispatchParcelItem["source"]): string {
  return source === "CUSTOMER" ? "Customer" : "Merchant";
}

function isParcelStatus(value: string): value is ParcelStatus {
  return (FILTER_STATUSES as string[]).includes(value) && value !== "ALL";
}
