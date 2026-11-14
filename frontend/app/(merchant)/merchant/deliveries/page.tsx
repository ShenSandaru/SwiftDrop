"use client";

import React, { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { formatApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import {
  fetchMerchantWebhookDeliveries,
  type WebhookDeliverySummary,
} from "@/lib/merchant";
import { Card } from "@/components/ui/card";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "cn";
import {
  Activity,
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  RefreshCw,
  XCircle,
} from "lucide-react";

const PAGE_SIZE = 15;

export default function MerchantDeliveriesPage() {
  const { token } = useAuth();

  const [deliveries, setDeliveries] = useState<WebhookDeliverySummary[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalItems, setTotalItems] = useState(0);

  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadDeliveries = useCallback(
    async (page: number) => {
      if (!token) return;
      setIsLoading(true);
      setErrorMessage(null);

      try {
        const res = await fetchMerchantWebhookDeliveries(
          { page, size: PAGE_SIZE },
          token
        );
        setDeliveries(res.items || []);
        setCurrentPage(res.page || 0);
        setTotalPages(res.totalPages || 1);
        setTotalItems(res.totalItems || 0);
      } catch (err: unknown) {
        setErrorMessage(formatApiError(err));
      } finally {
        setIsLoading(false);
      }
    },
    [token]
  );

  useEffect(() => {
    let ignore = false;
    if (!token) return;

    fetchMerchantWebhookDeliveries({ page: currentPage, size: PAGE_SIZE }, token)
      .then((res) => {
        if (ignore) return;
        setDeliveries(res.items || []);
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
  }, [token, currentPage]);

  return (
    <div className="space-y-6 pb-12">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">
            Webhook Delivery Audit Log
          </h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            Inspect HTTP delivery attempts, server response codes, and retry schedules.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => loadDeliveries(currentPage)}
            disabled={isLoading}
            className="text-xs"
          >
            <RefreshCw className={cn("size-3.5 mr-1.5", isLoading && "animate-spin")} />
            Refresh
          </Button>

          <Link
            href="/merchant/webhooks"
            className={cn(buttonVariants({ variant: "outline", size: "sm" }), "text-xs gap-1")}
          >
            Manage Endpoints
            <ArrowRight className="size-3" />
          </Link>
        </div>
      </div>

      {/* Error State */}
      {errorMessage && (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <AlertCircle className="size-4 shrink-0" />
            <span>{errorMessage}</span>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => loadDeliveries(currentPage)}
            className="text-xs"
          >
            Retry
          </Button>
        </div>
      )}

      {/* Loading Skeleton */}
      {isLoading && (
        <div className="flex items-center justify-center min-h-[300px]">
          <div className="flex flex-col items-center gap-2 text-muted-foreground">
            <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            <p className="text-xs">Fetching delivery audit records...</p>
          </div>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && deliveries.length === 0 && (
        <Card className="rounded-xl border-dashed border-border bg-card p-12 text-center">
          <div className="size-12 rounded-full bg-muted/50 text-muted-foreground flex items-center justify-center mx-auto mb-3">
            <Activity className="size-6" />
          </div>
          <h2 className="text-base font-semibold text-foreground">No Delivery Attempts Recorded</h2>
          <p className="text-xs text-muted-foreground max-w-sm mx-auto mt-1 mb-4">
            Webhook delivery events will be recorded here whenever shipments trigger notifications to your configured endpoints.
          </p>
          <Link
            href="/merchant/webhooks"
            className={cn(buttonVariants({ size: "sm" }), "text-xs font-semibold gap-1.5")}
          >
            Configure Webhook Endpoints
          </Link>
        </Card>
      )}

      {/* Deliveries Table */}
      {!isLoading && deliveries.length > 0 && (
        <Card className="rounded-xl border-border bg-card shadow-xs overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-muted/40 border-b border-border/60 text-muted-foreground font-semibold">
                <tr>
                  <th className="py-3 px-4">Event Triggered</th>
                  <th className="py-3 px-4">Destination Endpoint</th>
                  <th className="py-3 px-4">Event Type</th>
                  <th className="py-3 px-4">HTTP Status</th>
                  <th className="py-3 px-4">Delivery Status</th>
                  <th className="py-3 px-4">Attempts</th>
                  <th className="py-3 px-4">Next Retry</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/40">
                {deliveries.map((d) => {
                  const statusCode = d.responseStatus;
                  const is2xx = statusCode && statusCode >= 200 && statusCode < 300;
                  const isSuccess = d.deliveryStatus === "SUCCEEDED" || is2xx;

                  return (
                    <tr key={d.id} className="hover:bg-muted/20 transition-colors">
                      <td className="py-3 px-4 text-muted-foreground whitespace-nowrap">
                        {new Date(d.createdAt).toLocaleString(undefined, {
                          month: "short",
                          day: "numeric",
                          hour: "2-digit",
                          minute: "2-digit",
                          second: "2-digit",
                        })}
                      </td>

                      <td className="py-3 px-4 font-mono max-w-[240px] truncate" title={d.endpointUrl}>
                        <span className="text-foreground">{d.endpointUrl}</span>
                      </td>

                      <td className="py-3 px-4">
                        <span className="px-2 py-0.5 rounded bg-muted/60 text-foreground font-semibold text-[10px]">
                          {d.eventType}
                        </span>
                      </td>

                      <td className="py-3 px-4">
                        {statusCode ? (
                          <span
                            className={cn(
                              "inline-flex items-center gap-1 px-2 py-0.5 rounded-full font-mono font-semibold text-[11px]",
                              is2xx
                                ? "bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-500/20"
                                : "bg-destructive/10 text-destructive border border-destructive/20"
                            )}
                          >
                            {is2xx ? (
                              <CheckCircle2 className="size-3" />
                            ) : (
                              <XCircle className="size-3" />
                            )}
                            HTTP {statusCode}
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-muted text-muted-foreground text-[11px] font-mono">
                            No Response
                          </span>
                        )}
                      </td>

                      <td className="py-3 px-4">
                        {isSuccess ? (
                          <span className="inline-flex items-center gap-1 font-semibold text-emerald-600 dark:text-emerald-400">
                            <CheckCircle2 className="size-3" />
                            SUCCESS
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 font-semibold text-destructive">
                            <XCircle className="size-3" />
                            {d.deliveryStatus}
                          </span>
                        )}
                      </td>

                      <td className="py-3 px-4 font-mono text-muted-foreground">
                        {d.attemptCount} {d.attemptCount === 1 ? "try" : "tries"}
                      </td>

                      <td className="py-3 px-4 text-muted-foreground whitespace-nowrap">
                        {d.nextRetryAt ? (
                          <span className="inline-flex items-center gap-1 text-amber-600 dark:text-amber-400 font-medium">
                            <Clock className="size-3" />
                            {new Date(d.nextRetryAt).toLocaleTimeString()}
                          </span>
                        ) : (
                          <span className="text-muted-foreground/50">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Pagination Controls */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-4 border-t border-border/50 text-xs text-muted-foreground">
            <div>
              Showing <span className="font-semibold text-foreground">{deliveries.length}</span> of{" "}
              <span className="font-semibold text-foreground">{totalItems}</span> deliveries
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
