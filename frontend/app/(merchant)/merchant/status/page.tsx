"use client";

import React, { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { api, formatApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Card, CardContent, CardFooter } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

interface MerchantStatusResponse {
  merchantId: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED";
  businessName: string;
  businessType: string;
  businessRegistrationNo: string;
  contactPhone: string;
  businessAddress: string;
  businessDescription?: string;
  websiteUrl?: string;
  submittedAt: string;
  decidedAt?: string;
  decisionReason?: string;
}

export default function MerchantStatusPage() {
  const router = useRouter();
  const { token, logout, user } = useAuth();

  const [data, setData] = useState<MerchantStatusResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fetchStatus = useCallback(() => {
    if (!token) return;

    api
      .get<MerchantStatusResponse>("/api/v1/merchants/me", { token })
      .then((res) => {
        setData(res);
        setErrorMessage(null);
        if (res.status === "APPROVED") {
          // Auto-redirect to merchant portal upon approval
          setTimeout(() => {
            router.push("/merchant");
          }, 1500);
        }
      })
      .catch((err: unknown) => {
        setErrorMessage(formatApiError(err));
      })
      .finally(() => {
        setIsLoading(false);
        setIsRefreshing(false);
      });
  }, [token, router]);

  const handleRefresh = useCallback(() => {
    setIsRefreshing(true);
    fetchStatus();
  }, [fetchStatus]);

  // Initial load
  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  // Polling every 30 seconds
  useEffect(() => {
    const interval = setInterval(() => {
      fetchStatus();
    }, 30000);

    return () => clearInterval(interval);
  }, [fetchStatus]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="flex flex-col items-center gap-3 text-muted-foreground">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-sm">Checking merchant approval status...</p>
        </div>
      </div>
    );
  }

  if (errorMessage && !data) {
    return (
      <Card className="max-w-lg mx-auto rounded-xl border-border bg-card p-6 text-center space-y-4">
        <div className="text-destructive font-semibold">Status Check Error</div>
        <p className="text-sm text-muted-foreground">{errorMessage}</p>
        <div className="flex justify-center gap-3 pt-2">
          <Button onClick={handleRefresh}>Retry</Button>
          <Button variant="outline" onClick={logout}>Sign Out</Button>
        </div>
      </Card>
    );
  }

  const status = data?.status || "PENDING";
  const formattedDate = data?.submittedAt
    ? new Date(data.submittedAt).toLocaleDateString(undefined, {
        year: "numeric",
        month: "long",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "Recently";

  return (
    <div className="max-w-2xl mx-auto space-y-6 py-6">
      <Card className="rounded-xl border-border bg-card shadow-xs overflow-hidden">
        {/* Top Status Header */}
        <div className="p-6 border-b border-border/60">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <span className="text-xs uppercase tracking-wider text-muted-foreground font-semibold">
                Merchant Application
              </span>
              <h1 className="text-2xl font-bold tracking-tight text-foreground">
                {data?.businessName || "Your Business"}
              </h1>
              <p className="text-xs text-muted-foreground mt-0.5">
                Reg No: {data?.businessRegistrationNo} · Type: {data?.businessType}
              </p>
            </div>

            <div>
              {status === "PENDING" && (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20">
                  <span className="size-2 rounded-full bg-amber-500 animate-pulse" />
                  Under Review
                </span>
              )}
              {status === "APPROVED" && (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                  <span className="size-2 rounded-full bg-emerald-500" />
                  Approved
                </span>
              )}
              {status === "REJECTED" && (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20">
                  <span className="size-2 rounded-full bg-rose-500" />
                  Not Approved
                </span>
              )}
              {status === "SUSPENDED" && (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20">
                  <span className="size-2 rounded-full bg-rose-500" />
                  Account Suspended
                </span>
              )}
            </div>
          </div>
        </div>

        <CardContent className="p-6 space-y-6">
          {/* Status Message Section */}
          {status === "PENDING" && (
            <div className="space-y-4">
              <div className="rounded-xl border border-amber-500/20 bg-amber-500/5 p-4 space-y-2">
                <div className="flex items-center gap-2 font-semibold text-amber-700 dark:text-amber-300 text-sm">
                  <span>⏳</span> Verification in Progress
                </div>
                <p className="text-xs text-muted-foreground leading-relaxed">
                  Your merchant application was submitted on <span className="font-medium text-foreground">{formattedDate}</span>.
                  An administrator is reviewing your business registration credentials. You will automatically be granted
                  access to the merchant portal and order creation APIs once approved.
                </p>
              </div>

              <div className="rounded-lg border border-border/60 bg-muted/20 p-4 text-xs text-muted-foreground space-y-2">
                <p className="font-semibold text-foreground">While your application is pending:</p>
                <ul className="list-disc pl-5 space-y-1">
                  <li>This page checks automatically for status changes every 30 seconds.</li>
                  <li>Order intake APIs and webhook configurations are temporarily locked until review is complete.</li>
                  <li>You can safely sign out and sign back in at any time.</li>
                </ul>
              </div>
            </div>
          )}

          {status === "APPROVED" && (
            <div className="space-y-4">
              <div className="rounded-xl border border-emerald-500/20 bg-emerald-500/5 p-5 text-center space-y-2">
                <div className="size-12 rounded-full bg-emerald-500/10 text-emerald-500 flex items-center justify-center text-2xl mx-auto">
                  ✓
                </div>
                <h2 className="text-lg font-bold text-emerald-700 dark:text-emerald-300">
                  Congratulations! Your merchant account is approved.
                </h2>
                <p className="text-xs text-muted-foreground">
                  Redirecting you to the Merchant Logistics Dashboard...
                </p>
              </div>

              <Button
                onClick={() => router.push("/merchant")}
                className="w-full font-semibold"
              >
                Go to Merchant Portal &rarr;
              </Button>
            </div>
          )}

          {status === "REJECTED" && (
            <div className="space-y-4">
              <div className="rounded-xl border border-rose-500/20 bg-rose-500/5 p-4 space-y-2">
                <div className="flex items-center gap-2 font-semibold text-rose-700 dark:text-rose-300 text-sm">
                  <span>⚠</span> Application Not Approved
                </div>
                <p className="text-xs text-muted-foreground">
                  The reviewing administrator was unable to approve your application.
                </p>
                {data?.decisionReason && (
                  <div className="mt-2 rounded-lg bg-background/80 border border-rose-500/20 p-3 text-xs">
                    <span className="font-semibold text-foreground">Reason provided: </span>
                    <span className="text-muted-foreground">{data.decisionReason}</span>
                  </div>
                )}
              </div>

              <div className="rounded-lg border border-border/60 p-4 text-xs text-muted-foreground space-y-2">
                <p className="font-semibold text-foreground">Need to update your details?</p>
                <p>
                  You can update your business registration documents and submit a revised application for review.
                </p>
                <Button
                  variant="outline"
                  className="w-full mt-2 font-semibold"
                  onClick={() => router.push("/merchant/profile")}
                >
                  Edit & Resubmit Application
                </Button>
              </div>
            </div>
          )}

          {status === "SUSPENDED" && (
            <div className="space-y-4">
              <div className="rounded-xl border border-rose-500/20 bg-rose-500/5 p-4 space-y-2">
                <div className="flex items-center gap-2 font-semibold text-rose-700 dark:text-rose-300 text-sm">
                  <span>⛔</span> Merchant Operations Suspended
                </div>
                <p className="text-xs text-muted-foreground">
                  Your merchant operations have been temporarily suspended by system administration.
                </p>
                {data?.decisionReason && (
                  <div className="mt-2 rounded-lg bg-background/80 border border-rose-500/20 p-3 text-xs">
                    <span className="font-semibold text-foreground">Administrative reason: </span>
                    <span className="text-muted-foreground">{data.decisionReason}</span>
                  </div>
                )}
                <p className="text-xs text-muted-foreground pt-1">
                  For account reinstatement inquiries, please contact our support desk at{" "}
                  <span className="font-medium text-foreground">support@swiftdrop.lk</span>.
                </p>
              </div>
            </div>
          )}

          {/* Details Summary Card */}
          <div className="border-t border-border/60 pt-4">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">
              Submitted Business Profile
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="rounded-lg bg-muted/30 p-2.5">
                <span className="text-muted-foreground block text-[10px]">Contact Person</span>
                <span className="font-medium text-foreground">{user?.fullName || "—"}</span>
              </div>
              <div className="rounded-lg bg-muted/30 p-2.5">
                <span className="text-muted-foreground block text-[10px]">Contact Phone</span>
                <span className="font-medium text-foreground">{data?.contactPhone || "—"}</span>
              </div>
              <div className="rounded-lg bg-muted/30 p-2.5 sm:col-span-2">
                <span className="text-muted-foreground block text-[10px]">Physical Address</span>
                <span className="font-medium text-foreground">{data?.businessAddress || "—"}</span>
              </div>
              {data?.websiteUrl && (
                <div className="rounded-lg bg-muted/30 p-2.5 sm:col-span-2">
                  <span className="text-muted-foreground block text-[10px]">Website</span>
                  <a
                    href={data.websiteUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="font-medium text-primary hover:underline"
                  >
                    {data.websiteUrl}
                  </a>
                </div>
              )}
            </div>
          </div>
        </CardContent>

        <CardFooter className="flex items-center justify-between p-4 bg-muted/20 border-t border-border/60">
          <Button
            variant="ghost"
            size="sm"
            onClick={handleRefresh}
            disabled={isRefreshing}
            className="text-xs gap-1.5"
          >
            <span className={isRefreshing ? "animate-spin" : ""}>🔄</span>
            {isRefreshing ? "Checking..." : "Refresh Status"}
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={logout}
            className="text-xs text-muted-foreground hover:text-foreground"
          >
            Sign Out
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
