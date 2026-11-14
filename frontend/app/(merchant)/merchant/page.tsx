"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  Package,
  RefreshCw,
  Store,
  Truck,
  Webhook,
  type LucideIcon,
} from "lucide-react";
import { cn } from "cn";
import { Button, buttonVariants } from "@/components/ui/button";
import { StatusBadge } from "@/components/delivery/status-badge";
import { STATUS_LABELS, formatDateTime, formatWeight } from "@/components/delivery/format";
import { SetupGuide, type SetupProgress } from "@/components/merchant/setup-guide";
import { formatApiError } from "@/lib/api";
import { PARCEL_STATUSES, type ParcelStatus } from "@/lib/delivery";
import {
  getMerchantDashboard,
  getMerchantProfile,
  listApiKeys,
  listWebhookEndpoints,
  type MerchantDashboard,
} from "@/lib/merchant";

/** Statuses that usually need the merchant's attention. */
const ATTENTION: ParcelStatus[] = ["DELIVERY_ATTEMPT_FAILED", "RETURNING", "FAILED"];

/** Bar colours per status, matching the status badges. */
const BAR_COLOURS: Record<ParcelStatus, string> = {
  CREATED: "bg-slate-400",
  PICKED_UP: "bg-sky-500",
  AT_HUB: "bg-teal-500",
  IN_TRANSIT: "bg-indigo-500",
  OUT_FOR_DELIVERY: "bg-amber-500",
  DELIVERY_ATTEMPT_FAILED: "bg-orange-500",
  DELIVERED: "bg-emerald-500",
  RETURNING: "bg-violet-400",
  RETURNED: "bg-violet-600",
  CANCELLED: "bg-zinc-400",
  FAILED: "bg-red-500",
};

export default function MerchantDashboardPage() {
  const [dashboard, setDashboard] = useState<MerchantDashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [businessName, setBusinessName] = useState<string | null>(null);
  const [setup, setSetup] = useState<{ hasApiKey: boolean; hasWebhook: boolean } | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let cancelled = false;
    getMerchantDashboard()
      .then((data) => {
        if (!cancelled) {
          setDashboard(data);
          setError(null);
        }
      })
      .catch((failure) => !cancelled && setError(formatApiError(failure)));
    return () => {
      cancelled = true;
    };
  }, [reload]);

  useEffect(() => {
    let cancelled = false;
    getMerchantProfile()
      .then((profile) => !cancelled && setBusinessName(profile.businessName))
      .catch(() => undefined);
    // The guide's ticks are a convenience: a failed lookup simply leaves the step unticked
    Promise.allSettled([listApiKeys(), listWebhookEndpoints()]).then(([keys, endpoints]) => {
      if (cancelled) return;
      setSetup({
        hasApiKey: keys.status === "fulfilled" && keys.value.some((key) => !key.revokedAt),
        hasWebhook: endpoints.status === "fulfilled" && endpoints.value.some((endpoint) => endpoint.enabled !== false),
      });
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const progress: SetupProgress | null =
    setup && dashboard ? { ...setup, hasOrders: dashboard.totalOrders > 0 } : null;
  const attention = dashboard
    ? ATTENTION.reduce((sum, status) => sum + (dashboard.countsByStatus[status] ?? 0), 0)
    : 0;

  return (
    <div className="space-y-8">
      {/* Hero */}
      <section className="relative overflow-hidden rounded-3xl bg-primary px-6 py-8 text-primary-foreground sm:px-10">
        <svg
          className="pointer-events-none absolute -right-10 -top-6 h-56 w-80 opacity-20"
          viewBox="0 0 320 220"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="M10 190 C 80 120, 120 200, 180 120 S 260 40, 310 20"
            stroke="currentColor"
            strokeWidth="3"
            strokeDasharray="10 10"
            strokeLinecap="round"
          />
          <circle cx="10" cy="190" r="7" fill="currentColor" />
          <circle cx="180" cy="120" r="5" fill="currentColor" />
          <circle cx="310" cy="20" r="9" fill="currentColor" />
        </svg>
        <div className="relative flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
          <div className="max-w-xl space-y-2">
            <p className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.2em] opacity-80">
              <Store className="size-4" aria-hidden="true" /> Merchant dashboard
            </p>
            <h2 className="text-2xl font-extrabold leading-tight sm:text-3xl">
              {businessName ? `Welcome back, ${businessName}` : "Welcome back"}
            </h2>
            <p className="text-sm opacity-90">
              Every order your store sends to SwiftDrop, and where it is right now.
            </p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Link
              href="/merchant/orders"
              className={cn(buttonVariants({ size: "lg" }), "bg-primary-foreground text-primary hover:bg-primary-foreground/90")}
            >
              <Package aria-hidden="true" /> View orders
            </Link>
            <Link
              href="/merchant/deliveries"
              className={cn(
                buttonVariants({ size: "lg", variant: "outline" }),
                "border-primary-foreground/40 bg-transparent text-primary-foreground hover:bg-primary-foreground/10 hover:text-primary-foreground",
              )}
            >
              <Webhook aria-hidden="true" /> Webhook log
            </Link>
          </div>
        </div>
      </section>

      {error && (
        <div
          role="alert"
          className="flex flex-col gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive sm:flex-row sm:items-center sm:justify-between"
        >
          <span>{error}</span>
          <Button variant="outline" size="sm" onClick={() => setReload((value) => value + 1)}>
            <RefreshCw aria-hidden="true" /> Try again
          </Button>
        </div>
      )}

      {/* Stat cards */}
      <section className="grid grid-cols-2 gap-3 lg:grid-cols-4" aria-label="Order totals">
        <StatCard icon={Package} label="Total orders" value={dashboard?.totalOrders} tone="primary" />
        <StatCard icon={Truck} label="On the way" value={dashboard?.activeOrders} tone="indigo" />
        <StatCard icon={CheckCircle2} label="Delivered" value={dashboard?.deliveredOrders} tone="emerald" />
        <StatCard
          icon={AlertTriangle}
          label="Need attention"
          value={dashboard ? attention : undefined}
          tone="amber"
          hint="Failed attempts, returns and failures"
        />
      </section>

      <div className="grid gap-6 lg:grid-cols-5">
        {/* Status breakdown */}
        <section className="space-y-4 rounded-3xl border border-border bg-card p-5 lg:col-span-2" aria-labelledby="breakdown-title">
          <h2 id="breakdown-title" className="text-sm font-bold uppercase tracking-wider text-muted-foreground">
            Orders by status
          </h2>
          {!dashboard && !error && <Skeleton rows={5} height="h-6" />}
          {dashboard && dashboard.totalOrders === 0 && (
            <p className="rounded-2xl border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
              No orders yet. They appear here as soon as your store sends the first one.
            </p>
          )}
          {dashboard && dashboard.totalOrders > 0 && (
            <>
              <div className="flex h-3 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                {PARCEL_STATUSES.map((status) => {
                  const count = dashboard.countsByStatus[status] ?? 0;
                  return count > 0 ? (
                    <div
                      key={status}
                      className={BAR_COLOURS[status]}
                      style={{ width: `${(count / dashboard.totalOrders) * 100}%` }}
                    />
                  ) : null;
                })}
              </div>
              <ul className="space-y-1.5">
                {PARCEL_STATUSES.filter((status) => (dashboard.countsByStatus[status] ?? 0) > 0).map((status) => (
                  <li key={status}>
                    <Link
                      href={`/merchant/orders?status=${status}`}
                      className="flex items-center justify-between rounded-lg px-2 py-1.5 text-sm hover:bg-muted"
                    >
                      <span className="flex items-center gap-2 text-foreground">
                        <span className={cn("size-2.5 rounded-full", BAR_COLOURS[status])} aria-hidden="true" />
                        {STATUS_LABELS[status]}
                      </span>
                      <span className="font-bold tabular-nums text-foreground">{dashboard.countsByStatus[status]}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>

        {/* Recent activity */}
        <section className="space-y-4 rounded-3xl border border-border bg-card p-5 lg:col-span-3" aria-labelledby="recent-title">
          <div className="flex items-center justify-between">
            <h2 id="recent-title" className="text-sm font-bold uppercase tracking-wider text-muted-foreground">
              Recent orders
            </h2>
            <Link href="/merchant/orders" className="inline-flex items-center gap-1 text-sm font-semibold text-primary hover:underline">
              See all <ArrowRight className="size-3.5" aria-hidden="true" />
            </Link>
          </div>
          {!dashboard && !error && <Skeleton rows={4} height="h-14" />}
          {dashboard && dashboard.recentOrders.length === 0 && (
            <p className="rounded-2xl border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
              Nothing yet. Follow the guide below to send your first order.
            </p>
          )}
          {dashboard && dashboard.recentOrders.length > 0 && (
            <ul className="divide-y divide-border">
              {dashboard.recentOrders.map((order) => (
                <li key={order.id}>
                  <Link
                    href={`/merchant/orders/${order.id}`}
                    className="flex flex-col gap-2 rounded-xl px-2 py-3 hover:bg-muted sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-bold text-foreground">
                        {order.merchantOrderReference ?? "No reference"}
                      </p>
                      <p className="font-mono text-xs tracking-wider text-muted-foreground">
                        {order.trackingNumber} · {formatWeight(order.weightGrams)} · {formatDateTime(order.createdAt)}
                      </p>
                    </div>
                    <StatusBadge status={order.status} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <SetupGuide key={progress ? "ready" : "loading"} progress={progress} />
    </div>
  );
}

const TONES = {
  primary: "bg-primary/10 text-primary",
  indigo: "bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300",
  emerald: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300",
  amber: "bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300",
} as const;

function StatCard({
  icon: Icon,
  label,
  value,
  tone,
  hint,
}: {
  icon: LucideIcon;
  label: string;
  value: number | undefined;
  tone: keyof typeof TONES;
  hint?: string;
}) {
  return (
    <div className="space-y-3 rounded-2xl border border-border bg-card p-4" title={hint}>
      <span className={cn("inline-flex size-9 items-center justify-center rounded-xl", TONES[tone])}>
        <Icon className="size-4.5" aria-hidden="true" />
      </span>
      <div>
        {value === undefined ? (
          <div className="h-8 w-14 animate-pulse rounded-md bg-muted" />
        ) : (
          <p className="text-3xl font-extrabold tabular-nums text-foreground">{value}</p>
        )}
        <p className="text-xs font-semibold text-muted-foreground">{label}</p>
      </div>
    </div>
  );
}

function Skeleton({ rows, height }: { rows: number; height: string }) {
  return (
    <div className="space-y-2" aria-hidden="true">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className={cn("animate-pulse rounded-xl bg-muted", height)} />
      ))}
    </div>
  );
}
