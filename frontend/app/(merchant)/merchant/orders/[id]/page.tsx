"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { formatApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { fetchMerchantOrderDetail, type MerchantOrderDetail } from "@/lib/merchant";
import { StatusBadge } from "@/components/delivery/status-badge";
import { StatusTimeline, type TimelineEntry } from "@/components/delivery/status-timeline";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "cn";
import {
  AlertCircle,
  ArrowLeft,
  MapPin,
  Package,
  Truck,
  UserCheck,
} from "lucide-react";

export default function MerchantOrderDetailPage() {
  const params = useParams();
  const id = Array.isArray(params.id) ? params.id[0] : params.id;
  const { token } = useAuth();

  const [order, setOrder] = useState<MerchantOrderDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;

    if (id && token) {
      fetchMerchantOrderDetail(id, token)
        .then((data) => {
          if (ignore) return;
          setOrder(data);
          setErrorMessage(null);
        })
        .catch((err: unknown) => {
          if (ignore) return;
          setErrorMessage(formatApiError(err));
        })
        .finally(() => {
          if (!ignore) setIsLoading(false);
        });
    }

    return () => {
      ignore = true;
    };
  }, [id, token]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="flex flex-col items-center gap-3 text-muted-foreground">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-xs font-medium">Loading shipment details & tracking trail...</p>
        </div>
      </div>
    );
  }

  if (errorMessage || !order) {
    return (
      <div className="max-w-2xl mx-auto space-y-6 py-6">
        <Card className="rounded-xl border-border bg-card p-8 text-center space-y-4">
          <div className="size-12 rounded-full bg-destructive/10 text-destructive flex items-center justify-center mx-auto">
            <AlertCircle className="size-6" />
          </div>
          <div className="space-y-1">
            <h2 className="text-lg font-bold text-foreground">Shipment Not Found</h2>
            <p className="text-xs text-muted-foreground">
              {errorMessage || "The requested parcel order does not exist or does not belong to your merchant account."}
            </p>
          </div>
          <div className="pt-2">
            <Link
              href="/merchant/orders"
              className={cn(buttonVariants({ variant: "outline", size: "sm" }), "gap-1.5")}
            >
              <ArrowLeft className="size-3.5" />
              Back to Shipments List
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  const timelineEntries: TimelineEntry[] = (order.history || []).map((h) => ({
    status: h.status,
    occurredAt: h.occurredAt,
    note: h.note,
    hubName: h.hubName,
    attempt: h.attempt,
  }));

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* Navigation Breadcrumb */}
      <div>
        <Link
          href="/merchant/orders"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="size-3.5" />
          Back to Shipments
        </Link>
      </div>

      {/* Main Header Card */}
      <Card className="rounded-xl border-border bg-card shadow-xs p-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2.5">
              <h1 className="text-2xl font-bold tracking-tight text-foreground font-mono">
                {order.trackingNumber}
              </h1>
              <StatusBadge status={order.status} />
            </div>
            <p className="text-xs text-muted-foreground">
              Internal Parcel ID: <span className="font-mono">{order.id}</span>
              {order.merchantOrderReference && (
                <> · Merchant Reference: <span className="font-semibold text-foreground font-mono">{order.merchantOrderReference}</span></>
              )}
            </p>
          </div>

          <div className="text-xs text-muted-foreground sm:text-right space-y-0.5">
            <div>
              Created:{" "}
              <span className="font-medium text-foreground">
                {new Date(order.createdAt).toLocaleString()}
              </span>
            </div>
            {order.updatedAt && (
              <div>
                Last Update:{" "}
                <span className="font-medium text-foreground">
                  {new Date(order.updatedAt).toLocaleString()}
                </span>
              </div>
            )}
          </div>
        </div>
      </Card>

      {/* Grid: Route & Package Details vs Timeline */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Left Column: Shipment Specs & Route */}
        <div className="space-y-6 md:col-span-1">
          {/* Route Overview */}
          <Card className="rounded-xl border-border bg-card shadow-xs">
            <CardHeader className="pb-3">
              <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <MapPin className="size-3.5 text-primary" />
                Transit Route
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4 pt-0 text-xs">
              <div className="relative pl-6 space-y-6 before:absolute before:left-2 before:top-2 before:bottom-2 before:w-0.5 before:bg-border">
                <div className="relative">
                  <span className="absolute -left-6 top-0.5 size-3 rounded-full bg-emerald-500 ring-4 ring-emerald-500/20" />
                  <p className="font-semibold text-foreground text-sm">Pickup Hub</p>
                  <p className="text-muted-foreground">{order.pickupCity || "Central Depot"}</p>
                </div>
                <div className="relative">
                  <span className="absolute -left-6 top-0.5 size-3 rounded-full bg-primary ring-4 ring-primary/20" />
                  <p className="font-semibold text-foreground text-sm">Destination Dropoff</p>
                  <p className="text-muted-foreground">{order.dropoffCity || "Customer City"}</p>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Package Attributes */}
          <Card className="rounded-xl border-border bg-card shadow-xs">
            <CardHeader className="pb-3">
              <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <Package className="size-3.5 text-primary" />
                Consignment Details
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 pt-0 text-xs divide-y divide-border/40">
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">Package Size</span>
                <span className="font-semibold text-foreground">{order.size || "Standard"}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">Parcel Weight</span>
                <span className="font-semibold text-foreground">
                  {order.weightGrams ? `${(order.weightGrams / 1000).toFixed(2)} kg (${order.weightGrams}g)` : "—"}
                </span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">Assigned Courier</span>
                <span className="font-mono text-foreground">
                  {order.courierAssigned || order.assignedCourierId ? (
                    <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400">
                      <UserCheck className="size-3" />
                      Assigned
                    </span>
                  ) : (
                    <span className="text-muted-foreground">Unassigned</span>
                  )}
                </span>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Right Column: Status Lifecycle Timeline */}
        <div className="md:col-span-2">
          <Card className="rounded-xl border-border bg-card shadow-xs h-full">
            <CardHeader className="border-b border-border/50 pb-4">
              <CardTitle className="text-sm font-semibold flex items-center gap-2">
                <Truck className="size-4 text-primary" />
                Custody & Fulfillment Timeline
              </CardTitle>
            </CardHeader>
            <CardContent className="p-6">
              {timelineEntries.length > 0 ? (
                <StatusTimeline entries={timelineEntries} />
              ) : (
                <div className="text-xs text-muted-foreground text-center py-6">
                  No tracking milestones logged for this parcel yet.
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
