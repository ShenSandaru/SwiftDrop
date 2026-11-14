"use client";

import React, { useEffect, useState, useCallback, useMemo } from "react";
import Link from "next/link";
import { formatApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { fetchMerchantOrders, type MerchantOrderSummary } from "@/lib/merchant";
import { PARCEL_STATUSES } from "@/lib/delivery";
import { StatusBadge } from "@/components/delivery/status-badge";
import { Card } from "@/components/ui/card";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "cn";
import {
  AlertCircle,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  Package,
  RefreshCw,
  Search,
} from "lucide-react";

const FILTER_TABS = ["ALL", ...PARCEL_STATUSES] as const;
type FilterTab = (typeof FILTER_TABS)[number];

const PAGE_SIZE = 15;

export default function MerchantOrdersPage() {
  const { token } = useAuth();

  const [orders, setOrders] = useState<MerchantOrderSummary[]>([]);
  const [statusFilter, setStatusFilter] = useState<FilterTab>("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalItems, setTotalItems] = useState(0);

  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleRefresh = useCallback(async () => {
    if (!token) return;
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const res = await fetchMerchantOrders(
        {
          status: statusFilter === "ALL" ? undefined : statusFilter,
          page: currentPage,
          size: PAGE_SIZE,
        },
        token
      );
      setOrders(res.items || []);
      setCurrentPage(res.page || 0);
      setTotalPages(res.totalPages || 1);
      setTotalItems(res.totalItems || 0);
    } catch (err: unknown) {
      setErrorMessage(formatApiError(err));
    } finally {
      setIsLoading(false);
    }
  }, [token, currentPage, statusFilter]);

  useEffect(() => {
    let ignore = false;
    if (!token) return;

    fetchMerchantOrders(
      {
        status: statusFilter === "ALL" ? undefined : statusFilter,
        page: currentPage,
        size: PAGE_SIZE,
      },
      token
    )
      .then((res) => {
        if (ignore) return;
        setOrders(res.items || []);
        setCurrentPage(res.page || 0);
        setTotalPages(res.totalPages || 1);
        setTotalItems(res.totalItems || 0);
        setErrorMessage(null);
      })
      .catch((err: unknown) => {
        if (ignore) return;
        setErrorMessage(formatApiError(err));
      })
      .finally(() => {
        if (!ignore) setIsLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [token, currentPage, statusFilter]);

  function handleTabChange(tab: FilterTab) {
    setStatusFilter(tab);
    setCurrentPage(0);
  }

  // Client-side quick filter on tracking number / order reference
  const filteredOrders = useMemo(() => {
    if (!searchQuery.trim()) return orders;
    const q = searchQuery.toLowerCase().trim();
    return orders.filter(
      (o) =>
        o.trackingNumber.toLowerCase().includes(q) ||
        (o.merchantOrderReference && o.merchantOrderReference.toLowerCase().includes(q))
    );
  }, [orders, searchQuery]);

  return (
    <div className="space-y-6 pb-12">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">
            Merchant Shipments & Orders
          </h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            Monitor consignment statuses, customer shipments, and fulfillment milestones.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={isLoading}
            className="text-xs"
          >
            <RefreshCw className={cn("size-3.5 mr-1.5", isLoading && "animate-spin")} />
            Refresh
          </Button>
        </div>
      </div>

      {/* Filter Tabs & Search Bar */}
      <Card className="rounded-xl border-border bg-card p-4 space-y-4">
        {/* Status Tabs */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-thin">
          {FILTER_TABS.map((tab) => {
            const isActive = statusFilter === tab;
            return (
              <button
                key={tab}
                type="button"
                onClick={() => handleTabChange(tab)}
                className={cn(
                  "px-3 py-1.5 text-xs font-semibold rounded-lg whitespace-nowrap transition-colors",
                  isActive
                    ? "bg-primary text-primary-foreground shadow-xs"
                    : "text-muted-foreground hover:bg-muted/60 hover:text-foreground"
                )}
              >
                {tab === "ALL" ? "All Shipments" : tab.replace(/_/g, " ")}
              </button>
            );
          })}
        </div>

        {/* Search */}
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by tracking number or merchant order reference..."
            className="pl-9 text-xs"
          />
        </div>
      </Card>

      {/* Error Banner */}
      {errorMessage && (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <AlertCircle className="size-4 shrink-0" />
            <span>{errorMessage}</span>
          </div>
          <Button variant="outline" size="sm" onClick={handleRefresh} className="text-xs">
            Try Again
          </Button>
        </div>
      )}

      {/* Loading Skeleton */}
      {isLoading && (
        <div className="flex items-center justify-center min-h-[300px]">
          <div className="flex flex-col items-center gap-2 text-muted-foreground">
            <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            <p className="text-xs">Loading shipment records...</p>
          </div>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && filteredOrders.length === 0 && (
        <Card className="rounded-xl border-dashed border-border bg-card p-12 text-center">
          <div className="size-12 rounded-full bg-muted/50 text-muted-foreground flex items-center justify-center mx-auto mb-3">
            <Package className="size-6" />
          </div>
          <h2 className="text-base font-semibold text-foreground">No Shipments Found</h2>
          <p className="text-xs text-muted-foreground max-w-sm mx-auto mt-1 mb-4">
            {statusFilter !== "ALL"
              ? `No parcels are currently in ${statusFilter.replace(/_/g, " ")} status.`
              : "Your merchant account has no parcel consignments dispatched yet."}
          </p>
          {searchQuery && (
            <Button variant="outline" size="sm" onClick={() => setSearchQuery("")}>
              Clear Search Query
            </Button>
          )}
        </Card>
      )}

      {/* Order Table */}
      {!isLoading && filteredOrders.length > 0 && (
        <Card className="rounded-xl border-border bg-card shadow-xs overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-muted/40 border-b border-border/60 text-muted-foreground font-semibold">
                <tr>
                  <th className="py-3 px-4">Tracking Code</th>
                  <th className="py-3 px-4">Your Order Ref</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4">Size & Weight</th>
                  <th className="py-3 px-4">Booked Date</th>
                  <th className="py-3 px-4 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/40">
                {filteredOrders.map((order) => (
                  <tr key={order.id} className="hover:bg-muted/20 transition-colors">
                    <td className="py-3 px-4 font-mono font-semibold text-foreground">
                      <Link
                        href={`/merchant/orders/${order.id}`}
                        className="hover:underline text-primary"
                      >
                        {order.trackingNumber}
                      </Link>
                    </td>
                    <td className="py-3 px-4 text-muted-foreground">
                      {order.merchantOrderReference ? (
                        <span className="font-mono text-foreground">{order.merchantOrderReference}</span>
                      ) : (
                        <span className="text-muted-foreground/60">—</span>
                      )}
                    </td>
                    <td className="py-3 px-4">
                      <StatusBadge status={order.status} />
                    </td>
                    <td className="py-3 px-4 text-muted-foreground">
                      {order.size ? <span className="font-medium text-foreground">{order.size}</span> : "Standard"}
                      {" · "}
                      {order.weightGrams ? `${(order.weightGrams / 1000).toFixed(1)} kg` : "—"}
                    </td>
                    <td className="py-3 px-4 text-muted-foreground">
                      {new Date(order.createdAt).toLocaleDateString(undefined, {
                        month: "short",
                        day: "numeric",
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <Link
                        href={`/merchant/orders/${order.id}`}
                        className={cn(buttonVariants({ variant: "ghost", size: "xs" }), "gap-1 font-semibold")}
                      >
                        Details
                        <ArrowRight className="size-3" />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination Controls */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-4 border-t border-border/50 text-xs text-muted-foreground">
            <div>
              Showing <span className="font-semibold text-foreground">{filteredOrders.length}</span> of{" "}
              <span className="font-semibold text-foreground">{totalItems}</span> orders
            </div>

            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="xs"
                disabled={currentPage === 0 || isLoading}
                onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
              >
                <ChevronLeft className="size-3 mr-1" />
                Previous
              </Button>

              <span className="px-2 py-1 rounded bg-muted/30 text-foreground font-semibold">
                Page {currentPage + 1} of {Math.max(1, totalPages)}
              </span>

              <Button
                variant="outline"
                size="xs"
                disabled={currentPage >= totalPages - 1 || isLoading}
                onClick={() => setCurrentPage((p) => p + 1)}
              >
                Next
                <ChevronRight className="size-3 ml-1" />
              </Button>
            </div>
          </div>
        </Card>
      )}
    </div>
  );
}
