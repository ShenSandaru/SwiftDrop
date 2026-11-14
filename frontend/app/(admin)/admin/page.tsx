"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowRight,
  Building2,
  CalendarDays,
  CheckCircle2,
  Coins,
  Clock,
  Globe,
  Loader2,
  MapPin,
  Package,
  ShieldCheck,
  SlidersHorizontal,
  Store,
  Truck,
  Users,
  Warehouse,
} from "lucide-react";
import { cn } from "cn";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { api, formatApiError, type PageResponse, type UserResponse } from "@/lib/api";
import {
  countDispatchParcels,
  listDispatchCouriers,
  type DispatchCourier,
} from "@/lib/dispatch";
import { listHubs, type Hub } from "@/lib/hubs";
import {
  getPricingConfig,
  listAdminZones,
  type PricingConfigData,
  type ZoneAdmin,
} from "@/lib/pricing";

interface MerchantApplicationItem {
  merchantId: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED";
  businessName: string;
  businessType: string;
  businessRegistrationNo: string;
  contactPhone: string;
  submittedAt: string;
}

interface ParcelStageCounts {
  total: number;
  created: number;
  inTransitPipeline: number;
  outForDelivery: number;
  delivered: number;
  failedAttempt: number;
}

const TOTAL_SRI_LANKA_DISTRICTS = 25;

export default function AdminPortalOverviewPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastSyncedAt, setLastSyncedAt] = useState<Date | null>(null);

  const [hubs, setHubs] = useState<Hub[]>([]);
  const [zones, setZones] = useState<ZoneAdmin[]>([]);
  const [pricingConfig, setPricingConfig] = useState<PricingConfigData | null>(null);
  const [couriers, setCouriers] = useState<DispatchCourier[]>([]);

  const [pendingMerchants, setPendingMerchants] = useState<MerchantApplicationItem[]>([]);
  const [pendingMerchantCount, setPendingMerchantCount] = useState(0);
  const [approvedMerchantCount, setApprovedMerchantCount] = useState(0);

  const [totalUsers, setTotalUsers] = useState(0);
  const [dispatcherCount, setDispatcherCount] = useState(0);
  const [courierUserCount, setCourierUserCount] = useState(0);

  const [parcels, setParcels] = useState<ParcelStageCounts>({
    total: 0,
    created: 0,
    inTransitPipeline: 0,
    outForDelivery: 0,
    delivered: 0,
    failedAttempt: 0,
  });

  const load = useCallback(async () => {
    try {
      const [
        loadedHubs,
        loadedZones,
        loadedConfig,
        loadedCouriers,
        pendingMerchantsPage,
        approvedMerchantsPage,
        usersPage,
        dispatchersPage,
        couriersPage,
        totalParcels,
        createdParcels,
        pickedUpParcels,
        atHubParcels,
        inTransitParcels,
        outForDeliveryParcels,
        deliveredParcels,
        failedAttemptParcels,
      ] = await Promise.all([
        listHubs().catch(() => [] as Hub[]),
        listAdminZones().catch(() => [] as ZoneAdmin[]),
        getPricingConfig().catch(() => null),
        listDispatchCouriers().catch(() => [] as DispatchCourier[]),
        api
          .get<PageResponse<MerchantApplicationItem>>("/api/v1/merchants?status=PENDING&page=0&size=5")
          .catch(() => ({ items: [], page: 0, size: 5, totalItems: 0, totalPages: 0 })),
        api
          .get<PageResponse<MerchantApplicationItem>>("/api/v1/merchants?status=APPROVED&page=0&size=1")
          .catch(() => ({ items: [], page: 0, size: 1, totalItems: 0, totalPages: 0 })),
        api
          .get<PageResponse<UserResponse>>("/api/v1/users?page=0&size=1")
          .catch(() => ({ items: [], page: 0, size: 1, totalItems: 0, totalPages: 0 })),
        api
          .get<PageResponse<UserResponse>>("/api/v1/users?role=DISPATCHER&page=0&size=1")
          .catch(() => ({ items: [], page: 0, size: 1, totalItems: 0, totalPages: 0 })),
        api
          .get<PageResponse<UserResponse>>("/api/v1/users?role=COURIER&page=0&size=1")
          .catch(() => ({ items: [], page: 0, size: 1, totalItems: 0, totalPages: 0 })),
        countDispatchParcels().catch(() => 0),
        countDispatchParcels("CREATED").catch(() => 0),
        countDispatchParcels("PICKED_UP").catch(() => 0),
        countDispatchParcels("AT_HUB").catch(() => 0),
        countDispatchParcels("IN_TRANSIT").catch(() => 0),
        countDispatchParcels("OUT_FOR_DELIVERY").catch(() => 0),
        countDispatchParcels("DELIVERED").catch(() => 0),
        countDispatchParcels("DELIVERY_ATTEMPT_FAILED").catch(() => 0),
      ]);

      setHubs(loadedHubs);
      setZones(loadedZones);
      setPricingConfig(loadedConfig);
      setCouriers(loadedCouriers);

      setPendingMerchants(pendingMerchantsPage.items);
      setPendingMerchantCount(pendingMerchantsPage.totalItems);
      setApprovedMerchantCount(approvedMerchantsPage.totalItems);

      setTotalUsers(usersPage.totalItems);
      setDispatcherCount(dispatchersPage.totalItems);
      setCourierUserCount(couriersPage.totalItems);

      setParcels({
        total: totalParcels,
        created: createdParcels,
        inTransitPipeline: pickedUpParcels + atHubParcels + inTransitParcels,
        outForDelivery: outForDeliveryParcels,
        delivered: deliveredParcels,
        failedAttempt: failedAttemptParcels,
      });

      setLastSyncedAt(new Date());
      setError(null);
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initial = setTimeout(() => {
      void load();
    }, 0);
    const interval = setInterval(() => {
      void load();
    }, 30000);
    return () => {
      clearTimeout(initial);
      clearInterval(interval);
    };
  }, [load]);

  const activeHubs = useMemo(() => hubs.filter((h) => h.active), [hubs]);
  const centralHub = useMemo(() => activeHubs.find((h) => h.central) ?? null, [activeHubs]);
  const activeZonesCount = useMemo(() => zones.filter((z) => z.active).length, [zones]);

  const servedZonesCount = useMemo(() => {
    const activeHubIds = new Set(activeHubs.map((h) => h.id));
    const served = new Set<string>();
    for (const h of activeHubs) {
      if (h.zoneId) served.add(h.zoneId);
    }
    for (const z of zones) {
      if (z.active && z.hubId && activeHubIds.has(z.hubId)) {
        served.add(z.id);
      }
    }
    return served.size;
  }, [activeHubs, zones]);

  const coveredDistrictsCount = useMemo(() => {
    const districts = new Set<string>();
    for (const hub of activeHubs) {
      if (hub.city) districts.add(hub.city.trim().toLowerCase());
    }
    return districts.size;
  }, [activeHubs]);

  const today = useMemo(
    () =>
      new Date().toLocaleDateString("en-US", {
        weekday: "short",
        month: "short",
        day: "numeric",
        year: "numeric",
      }),
    []
  );

  const parcelStages = [
    { label: "Awaiting pickup", status: "CREATED", count: parcels.created, tone: "bg-amber-500" },
    {
      label: "Picked up / Hub transit",
      status: "IN_TRANSIT",
      count: parcels.inTransitPipeline,
      tone: "bg-sky-500",
    },
    {
      label: "Out for delivery",
      status: "OUT_FOR_DELIVERY",
      count: parcels.outForDelivery,
      tone: "bg-primary",
    },
    { label: "Delivered", status: "DELIVERED", count: parcels.delivered, tone: "bg-emerald-500" },
    {
      label: "Failed attempts",
      status: "ATTEMPT_FAILED",
      count: parcels.failedAttempt,
      tone: "bg-destructive",
    },
  ];

  return (
    <div className="space-y-6">
      {/* Top Operations Header Bar */}
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-card p-4 shadow-xs sm:flex-row sm:items-center sm:justify-between sm:p-5">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1.5 font-bold text-primary">
              <ShieldCheck className="size-3.5" aria-hidden="true" />
              System Administration
            </span>
            <span aria-hidden="true">&middot;</span>
            <span className="inline-flex items-center gap-1.5">
              <CalendarDays className="size-3.5" aria-hidden="true" />
              <time suppressHydrationWarning>{today}</time>
            </span>
            <span aria-hidden="true">&middot;</span>
            <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
              Control Tower Active
            </span>
          </div>
          <p className="text-sm text-muted-foreground">
            Platform-wide overview of merchant onboarding, hub coverage, user accounts, and live parcel operations.
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs text-muted-foreground shrink-0 self-start sm:self-center">
          <span className="relative flex size-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
            <span className="relative inline-flex rounded-full size-2 bg-emerald-500" />
          </span>
          <span className="font-mono text-[11px]" suppressHydrationWarning>
            {lastSyncedAt
              ? `Synced ${lastSyncedAt.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`
              : "Syncing..."}
          </span>
        </div>
      </div>

      {/* Hero Command Banner */}
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
            <Globe className="size-4" aria-hidden="true" /> SwiftDrop Operations Command
          </p>
          <h2 className="font-heading text-2xl font-extrabold leading-tight sm:text-3xl">
            Platform Health &amp; Network Overview
          </h2>
          <p className="text-sm opacity-90 sm:text-base leading-relaxed">
            Monitor island-wide hub readiness, approve incoming merchants, manage staff roles, and keep delivery tariffs aligned.
          </p>
        </div>
      </section>

      {error && (
        <Alert variant="destructive">
          <AlertTriangle aria-hidden="true" />
          <AlertTitle>Unable to load full overview</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* 5 Primary KPI Cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <Card className="rounded-xl border border-border bg-card shadow-2xs py-4">
          <CardContent className="flex h-full flex-col justify-between gap-3 px-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-xs font-medium text-muted-foreground">Pending merchants</p>
                <p className="mt-1 font-mono text-2xl font-bold text-foreground">
                  {loading ? "—" : pendingMerchantCount}
                </p>
              </div>
              <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
                <Store className="size-4" aria-hidden="true" />
              </span>
            </div>
            <p className="text-xs text-muted-foreground">
              {approvedMerchantCount} approved merchant{approvedMerchantCount === 1 ? "" : "s"} active
            </p>
          </CardContent>
        </Card>

        <Card className="rounded-xl border border-border bg-card shadow-2xs py-4">
          <CardContent className="flex h-full flex-col justify-between gap-3 px-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-xs font-medium text-muted-foreground">Active hubs</p>
                <p className="mt-1 font-mono text-2xl font-bold text-foreground">
                  {loading ? "—" : activeHubs.length}
                </p>
              </div>
              <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
                <Warehouse className="size-4" aria-hidden="true" />
              </span>
            </div>
            <p className="text-xs text-muted-foreground">
              {coveredDistrictsCount} / {TOTAL_SRI_LANKA_DISTRICTS} districts covered
            </p>
          </CardContent>
        </Card>

        <Card className="rounded-xl border border-border bg-card shadow-2xs py-4">
          <CardContent className="flex h-full flex-col justify-between gap-3 px-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-xs font-medium text-muted-foreground">Active zones</p>
                <p className="mt-1 font-mono text-2xl font-bold text-foreground">
                  {loading ? "—" : activeZonesCount}
                </p>
              </div>
              <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
                <MapPin className="size-4" aria-hidden="true" />
              </span>
            </div>
            <p className="text-xs text-muted-foreground">
              {servedZonesCount} served by hubs &middot; {zones.length} total
            </p>
          </CardContent>
        </Card>

        <Card className="rounded-xl border border-border bg-card shadow-2xs py-4">
          <CardContent className="flex h-full flex-col justify-between gap-3 px-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-xs font-medium text-muted-foreground">Registered users</p>
                <p className="mt-1 font-mono text-2xl font-bold text-foreground">
                  {loading ? "—" : totalUsers}
                </p>
              </div>
              <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
                <Users className="size-4" aria-hidden="true" />
              </span>
            </div>
            <p className="text-xs text-muted-foreground">
              {dispatcherCount} dispatcher{dispatcherCount === 1 ? "" : "s"} &middot; {courierUserCount} courier{courierUserCount === 1 ? "" : "s"}
            </p>
          </CardContent>
        </Card>

        <Card className="rounded-xl border border-border bg-card shadow-2xs py-4">
          <CardContent className="flex h-full flex-col justify-between gap-3 px-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-xs font-medium text-muted-foreground">Total parcels</p>
                <p className="mt-1 font-mono text-2xl font-bold text-foreground">
                  {loading ? "—" : parcels.total}
                </p>
              </div>
              <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
                <Package className="size-4" aria-hidden="true" />
              </span>
            </div>
            <p className="text-xs text-muted-foreground">
              {parcels.delivered} delivered &middot; {parcels.outForDelivery} out for delivery
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Section Header: Actionable Queues */}
      <div className="space-y-1 pt-1">
        <p className="text-xs font-semibold uppercase tracking-wider text-primary">
          Operational Queues &amp; Pipeline
        </p>
        <h2 className="font-heading text-xl font-bold tracking-tight text-foreground">
          Onboarding &amp; Live Shipment Flow
        </h2>
        <p className="text-xs text-muted-foreground">
          Pending merchant approvals requiring admin review alongside real-time parcel stage distribution.
        </p>
      </div>

      {/* 2-Column Operational Grid */}
      <div className="grid gap-5 lg:grid-cols-2">
        {/* Left: Pending Merchant Applications */}
        <Card className="rounded-xl border border-border bg-card shadow-2xs">
          <CardHeader className="border-b border-border/70 pb-4">
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-3">
                <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
                  <Clock className="size-4" aria-hidden="true" />
                </span>
                <div>
                  <CardTitle className="font-heading text-base font-semibold">
                    Pending Merchant Approvals
                  </CardTitle>
                  <CardDescription className="text-xs">
                    Applications awaiting admin verification before API &amp; portal access.
                  </CardDescription>
                </div>
              </div>
              <span className="shrink-0 whitespace-nowrap rounded-full bg-primary/10 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-primary">
                {pendingMerchantCount} PENDING
              </span>
            </div>
          </CardHeader>
          <CardContent className="pt-4">
            {loading ? (
              <div className="flex items-center justify-center gap-2 py-8 text-xs text-muted-foreground">
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                Loading merchant queue...
              </div>
            ) : pendingMerchants.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-border py-8 text-center">
                <CheckCircle2 className="size-6 text-emerald-500" aria-hidden="true" />
                <p className="text-sm font-semibold text-foreground">All caught up</p>
                <p className="text-xs text-muted-foreground">
                  No pending merchant applications in the review queue.
                </p>
              </div>
            ) : (
              <div className="space-y-2.5">
                {pendingMerchants.map((merchant) => (
                  <div
                    key={merchant.merchantId}
                    className="flex items-center justify-between gap-3 rounded-lg border border-border/80 bg-muted/20 px-3.5 py-2.5"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-foreground">
                        {merchant.businessName}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">
                        {merchant.businessType} &middot;{" "}
                        <span className="font-mono">{merchant.businessRegistrationNo}</span>
                      </p>
                    </div>
                    <Link
                      href="/admin/merchants"
                      className={cn(
                        buttonVariants({ size: "xs", variant: "outline" }),
                        "shrink-0 font-medium"
                      )}
                    >
                      Review
                    </Link>
                  </div>
                ))}
              </div>
            )}
            <div className="mt-4 flex justify-end border-t border-border/60 pt-3">
              <Link
                href="/admin/merchants"
                className="group inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-semibold text-primary transition-colors hover:bg-primary/10"
              >
                Open Merchant Console{" "}
                <ArrowRight
                  className="size-3.5 transition-transform group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </Link>
            </div>
          </CardContent>
        </Card>

        {/* Right: Parcel Pipeline & Network Readiness */}
        <Card className="flex flex-col rounded-xl border border-border bg-card shadow-2xs pb-0">
          <CardHeader className="border-b border-border/70 pb-4">
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-3">
                <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
                  <Truck className="size-4" aria-hidden="true" />
                </span>
                <div>
                  <CardTitle className="font-heading text-base font-semibold">
                    Parcel Pipeline &amp; Fleet Readiness
                  </CardTitle>
                  <CardDescription className="text-xs">
                    Live parcel distribution across pickup, hub transit, and final delivery.
                  </CardDescription>
                </div>
              </div>
              <span className="shrink-0 whitespace-nowrap rounded-full border border-primary/20 bg-accent px-2.5 py-0.5 font-mono text-[11px] font-semibold text-primary">
                {couriers.length} COURIERS
              </span>
            </div>
          </CardHeader>
          <CardContent className="flex-1 space-y-3 pt-4">
            {parcelStages.map((stage) => {
              const pct = parcels.total > 0 ? Math.round((stage.count / parcels.total) * 100) : 0;
              return (
                <div
                  key={stage.status}
                  className="space-y-1.5 rounded-lg border border-border/60 bg-muted/20 px-3 py-2"
                >
                  <div className="flex items-center justify-between gap-2 text-xs">
                    <span className="inline-flex items-center gap-2 font-medium text-foreground">
                      <span className={cn("size-2 shrink-0 rounded-full", stage.tone)} aria-hidden="true" />
                      {stage.label}
                    </span>
                    <span className="inline-flex items-center gap-1.5 font-mono text-xs">
                      <span className="font-semibold text-foreground">{stage.count}</span>
                      <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
                        {pct}%
                      </span>
                    </span>
                  </div>
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
                    <div
                      className={cn("h-full rounded-full transition-all", stage.tone)}
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </CardContent>
          <CardFooter className="flex flex-wrap items-center justify-between gap-3 border-t border-border/60 bg-muted/20 px-4 py-3 text-xs">
            <div className="flex min-w-0 items-center gap-2">
              <Building2 className="size-4 shrink-0 text-primary" aria-hidden="true" />
              <span className="font-medium text-foreground">Central Hub:</span>
              <span className="truncate font-mono text-muted-foreground">
                {centralHub ? `${centralHub.name} (${centralHub.code})` : "Not configured"}
              </span>
            </div>
            <Link
              href="/dispatch"
              className="group inline-flex shrink-0 items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-semibold text-primary transition-colors hover:bg-primary/10"
            >
              Dispatch Console{" "}
              <ArrowRight
                className="size-3.5 transition-transform group-hover:translate-x-0.5"
                aria-hidden="true"
              />
            </Link>
          </CardFooter>
        </Card>
      </div>

      {/* Active Tariff Snapshot */}
      <Card className="rounded-xl border border-border bg-card shadow-2xs">
        <CardHeader className="border-b border-border/70 pb-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
                <Coins className="size-4" aria-hidden="true" />
              </span>
              <div>
                <CardTitle className="font-heading text-base font-semibold">
                  Active Global Pricing Tariff
                </CardTitle>
                <CardDescription className="text-xs">
                  Live parameters applied to customer and merchant route fee calculations.
                </CardDescription>
              </div>
            </div>
            <Link
              href="/admin/pricing"
              className={cn(
                buttonVariants({ size: "sm", variant: "outline" }),
                "group h-8 gap-1.5 rounded-lg border-primary/25 bg-accent/40 px-3 text-xs font-semibold text-primary shadow-2xs transition-all hover:border-primary hover:bg-primary hover:text-primary-foreground"
              )}
            >
              <SlidersHorizontal className="size-3.5" aria-hidden="true" />
              Adjust Tariff
              <ArrowRight
                className="size-3.5 transition-transform group-hover:translate-x-0.5"
                aria-hidden="true"
              />
            </Link>
          </div>
        </CardHeader>
        <CardContent className="pt-4">
          {pricingConfig ? (
            <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
              <TariffStat
                label="Base fee"
                value={`${pricingConfig.currency} ${pricingConfig.standardBaseFee.toFixed(2)}`}
              />
              <TariffStat
                label="Per started kg"
                value={`${pricingConfig.currency} ${pricingConfig.perKgFee.toFixed(2)}`}
              />
              <TariffStat
                label="Distance allowance"
                value={`${pricingConfig.freeKmAllowance} km free`}
              />
              <TariffStat
                label="Rate / extra km"
                value={`${pricingConfig.currency} ${pricingConfig.ratePerKm.toFixed(2)}`}
              />
              <TariffStat
                label="Max fare cap"
                value={`${pricingConfig.currency} ${pricingConfig.maxCap.toFixed(2)}`}
              />
              <TariffStat
                label="Size surcharges"
                value={`${pricingConfig.smallSizeSurcharge} / ${pricingConfig.mediumSizeSurcharge} / ${pricingConfig.largeSizeSurcharge}`}
              />
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">
              Pricing configuration has not been loaded yet.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function TariffStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5">
      <p className="text-[11px] font-medium text-muted-foreground">{label}</p>
      <p className="mt-0.5 font-mono text-sm font-semibold text-foreground">{value}</p>
    </div>
  );
}
