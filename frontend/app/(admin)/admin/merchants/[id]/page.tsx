"use client";

import React, { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { formatApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import {
  fetchAdminMerchantDetail,
  submitAdminApprovalDecision,
  type AdminMerchantDetail,
} from "@/lib/merchant";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button, buttonVariants } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { cn } from "cn";
import {
  AlertCircle,
  ArrowLeft,
  Building,
  CheckCircle2,
  Clock,
  ExternalLink,
  History,
  RefreshCw,
  Shield,
  ShieldAlert,
  XCircle,
} from "lucide-react";

export default function AdminMerchantDetailPage() {
  const params = useParams();
  const id = Array.isArray(params.id) ? params.id[0] : params.id;
  const { token } = useAuth();

  const [merchant, setMerchant] = useState<AdminMerchantDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Decision Modal State
  const [decisionType, setDecisionType] = useState<"APPROVE" | "REJECT" | "SUSPEND" | null>(null);
  const [decisionReason, setDecisionReason] = useState("");
  const [isSubmittingDecision, setIsSubmittingDecision] = useState(false);
  const [decisionError, setDecisionError] = useState<string | null>(null);

  const loadDetail = useCallback(async () => {
    if (!id || !token) return;
    setIsLoading(true);
    setErrorMessage(null);

    try {
      const data = await fetchAdminMerchantDetail(id, token);
      setMerchant(data);
    } catch (err: unknown) {
      setErrorMessage(formatApiError(err));
    } finally {
      setIsLoading(false);
    }
  }, [id, token]);

  useEffect(() => {
    let ignore = false;
    if (!id || !token) return;

    fetchAdminMerchantDetail(id, token)
      .then((data) => {
        if (ignore) return;
        setMerchant(data);
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
  }, [id, token]);

  async function handleDecisionSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!id || !decisionType || !token || isSubmittingDecision) return;

    if ((decisionType === "REJECT" || decisionType === "SUSPEND") && !decisionReason.trim()) {
      setDecisionError(`A reason is required to ${decisionType.toLowerCase()} this merchant.`);
      return;
    }

    setIsSubmittingDecision(true);
    setDecisionError(null);

    try {
      await submitAdminApprovalDecision(
        id,
        {
          decision: decisionType,
          reason: decisionReason.trim() || undefined,
        },
        token
      );

      setDecisionType(null);
      setDecisionReason("");
      await loadDetail();
    } catch (err: unknown) {
      setDecisionError(formatApiError(err));
    } finally {
      setIsSubmittingDecision(false);
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="flex flex-col items-center gap-3 text-muted-foreground">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-xs font-medium">Loading merchant review profile & audit log...</p>
        </div>
      </div>
    );
  }

  if (errorMessage || !merchant) {
    return (
      <div className="max-w-2xl mx-auto space-y-6 py-6">
        <Card className="rounded-xl border-border bg-card p-8 text-center space-y-4">
          <div className="size-12 rounded-full bg-destructive/10 text-destructive flex items-center justify-center mx-auto">
            <AlertCircle className="size-6" />
          </div>
          <div className="space-y-1">
            <h2 className="text-lg font-bold text-foreground">Merchant Profile Not Found</h2>
            <p className="text-xs text-muted-foreground">
              {errorMessage || "The requested merchant application could not be loaded."}
            </p>
          </div>
          <div className="pt-2">
            <Link
              href="/admin/merchants"
              className={cn(buttonVariants({ variant: "outline", size: "sm" }), "gap-1.5")}
            >
              <ArrowLeft className="size-3.5" />
              Back to Merchant Queue
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  const isPending = merchant.status === "PENDING";
  const isApproved = merchant.status === "APPROVED";
  const isRejected = merchant.status === "REJECTED";
  const isSuspended = merchant.status === "SUSPENDED";

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* Navigation Breadcrumb */}
      <div className="flex items-center justify-between">
        <Link
          href="/admin/merchants"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="size-3.5" />
          Back to Applications Queue
        </Link>

        <Button
          variant="outline"
          size="xs"
          onClick={loadDetail}
          className="text-xs"
        >
          <RefreshCw className="size-3 mr-1.5" />
          Refresh
        </Button>
      </div>

      {/* Header Banner */}
      <Card className="rounded-xl border-border bg-card shadow-xs p-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2.5 flex-wrap">
              <h1 className="text-2xl font-bold tracking-tight text-foreground">
                {merchant.businessName}
              </h1>
              {isPending && (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-500/20">
                  <Clock className="size-3" />
                  Pending Review
                </span>
              )}
              {isApproved && (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-500/20">
                  <CheckCircle2 className="size-3" />
                  Active / Approved
                </span>
              )}
              {isRejected && (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-700 dark:text-rose-400 border border-rose-500/20">
                  <XCircle className="size-3" />
                  Application Rejected
                </span>
              )}
              {isSuspended && (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-destructive/10 text-destructive border border-destructive/20">
                  <ShieldAlert className="size-3" />
                  Operations Suspended
                </span>
              )}
            </div>

            <p className="text-xs text-muted-foreground">
              Registration No: <span className="font-mono font-semibold text-foreground">{merchant.businessRegistrationNo}</span>
              {" · "}Category: <span className="font-medium text-foreground">{merchant.businessType}</span>
            </p>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center gap-2 shrink-0">
            {isPending && (
              <>
                <Button
                  size="sm"
                  onClick={() => {
                    setDecisionType("APPROVE");
                    setDecisionReason("");
                    setDecisionError(null);
                  }}
                  className="bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-xs gap-1.5"
                >
                  <CheckCircle2 className="size-4" />
                  Approve Application
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setDecisionType("REJECT");
                    setDecisionReason("");
                    setDecisionError(null);
                  }}
                  className="text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/50 text-xs gap-1.5"
                >
                  <XCircle className="size-4" />
                  Reject Application
                </Button>
              </>
            )}

            {isApproved && (
              <Button
                variant="destructive"
                size="sm"
                onClick={() => {
                  setDecisionType("SUSPEND");
                  setDecisionReason("");
                  setDecisionError(null);
                }}
                className="text-xs gap-1.5"
              >
                <ShieldAlert className="size-4" />
                Suspend Account
              </Button>
            )}

            {(isSuspended || isRejected) && (
              <Button
                size="sm"
                onClick={() => {
                  setDecisionType("APPROVE");
                  setDecisionReason("");
                  setDecisionError(null);
                }}
                className="bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-xs gap-1.5"
              >
                <CheckCircle2 className="size-4" />
                Reinstate / Approve
              </Button>
            )}
          </div>
        </div>
      </Card>

      {/* Main Grid: Business Information vs Status Audit Trail */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Left: Organization Credentials */}
        <div className="space-y-6">
          <Card className="rounded-xl border-border bg-card shadow-xs">
            <CardHeader className="pb-3 border-b border-border/50">
              <CardTitle className="text-sm font-semibold flex items-center gap-2">
                <Building className="size-4 text-primary" />
                Business Credentials
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 pt-4 text-xs divide-y divide-border/40">
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">Registration No</span>
                <span className="font-mono font-semibold text-foreground">
                  {merchant.businessRegistrationNo}
                </span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">Category</span>
                <span className="font-medium text-foreground">{merchant.businessType}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">Contact Phone</span>
                <span className="font-mono text-foreground">{merchant.contactPhone}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">Address</span>
                <span className="text-foreground text-right max-w-[200px]">
                  {merchant.businessAddress}
                </span>
              </div>
              {merchant.websiteUrl && (
                <div className="flex justify-between py-1.5">
                  <span className="text-muted-foreground">Website</span>
                  <a
                    href={merchant.websiteUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-primary hover:underline inline-flex items-center gap-1"
                  >
                    Visit Website
                    <ExternalLink className="size-3" />
                  </a>
                </div>
              )}
              {merchant.businessDescription && (
                <div className="py-2 space-y-1">
                  <span className="text-muted-foreground block">Description</span>
                  <p className="text-foreground leading-relaxed italic bg-muted/20 p-2.5 rounded-lg border border-border/40">
                    &ldquo;{merchant.businessDescription}&rdquo;
                  </p>
                </div>
              )}
            </CardContent>
          </Card>

          {/* System & Identity Metadata */}
          <Card className="rounded-xl border-border bg-card shadow-xs">
            <CardHeader className="pb-3 border-b border-border/50">
              <CardTitle className="text-sm font-semibold flex items-center gap-2">
                <Shield className="size-4 text-primary" />
                System & Audit Metadata
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 pt-4 text-xs divide-y divide-border/40">
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">Merchant ID</span>
                <span className="font-mono text-[11px] text-foreground">{merchant.id}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">User ID</span>
                <span className="font-mono text-[11px] text-foreground">{merchant.userId}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">Application Submitted</span>
                <span className="text-foreground">{new Date(merchant.createdAt).toLocaleString()}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">Optimistic Lock Version</span>
                <span className="font-mono text-foreground font-semibold">v{merchant.version}</span>
              </div>
              {merchant.decidedAt && (
                <div className="flex justify-between py-1.5">
                  <span className="text-muted-foreground">Last Decision Time</span>
                  <span className="text-foreground">{new Date(merchant.decidedAt).toLocaleString()}</span>
                </div>
              )}
              {merchant.statusReason && (
                <div className="py-2 space-y-1">
                  <span className="text-muted-foreground block">Active Reason Note</span>
                  <p className="text-rose-600 dark:text-rose-400 font-medium bg-rose-500/10 p-2.5 rounded-lg border border-rose-500/20">
                    {merchant.statusReason}
                  </p>
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Right: Status Audit Trail */}
        <Card className="rounded-xl border-border bg-card shadow-xs h-full">
          <CardHeader className="pb-3 border-b border-border/50">
            <CardTitle className="text-sm font-semibold flex items-center gap-2">
              <History className="size-4 text-primary" />
              Status Audit History Trail
            </CardTitle>
            <CardDescription className="text-xs">
              Immutable ledger of all review decisions, transitions, and rationale notes.
            </CardDescription>
          </CardHeader>
          <CardContent className="p-6">
            {merchant.statusHistory && merchant.statusHistory.length > 0 ? (
              <ol className="relative border-l border-border/60 ml-2 space-y-6">
                {merchant.statusHistory.map((item, index) => (
                  <li key={item.id || index} className="ml-5">
                    <span className="absolute -left-2.5 mt-1 size-5 rounded-full border-2 border-background bg-primary text-primary-foreground flex items-center justify-center text-[10px]">
                      {merchant.statusHistory.length - index}
                    </span>
                    <div className="space-y-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-semibold text-xs text-foreground">
                          {item.fromStatus ? `${item.fromStatus} → ` : "Initial "}
                          <span className="text-primary">{item.toStatus}</span>
                        </span>
                        <span className="text-[11px] text-muted-foreground">
                          {new Date(item.createdAt).toLocaleString()}
                        </span>
                      </div>

                      {item.actorUserId && (
                        <p className="text-[11px] text-muted-foreground font-mono">
                          Reviewer ID: {item.actorUserId}
                        </p>
                      )}

                      {item.reason && (
                        <p className="text-xs text-foreground/90 bg-muted/40 p-2.5 rounded-md border border-border/40 mt-1">
                          &ldquo;{item.reason}&rdquo;
                        </p>
                      )}
                    </div>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="text-xs text-muted-foreground text-center py-8">
                No prior status transition history recorded.
              </p>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Decision Modal */}
      {decisionType && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <Card className="w-full max-w-md rounded-xl bg-card border-border shadow-xl p-6 space-y-4">
            <div>
              <h2 className="text-base font-bold text-foreground">
                Confirm Decision: {decisionType}
              </h2>
              <p className="text-xs text-muted-foreground mt-0.5">
                Target Business: <span className="font-semibold text-foreground">{merchant.businessName}</span>
              </p>
            </div>

            <form onSubmit={handleDecisionSubmit} className="space-y-4">
              {decisionError && (
                <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-xs text-destructive">
                  {decisionError}
                </div>
              )}

              {decisionType === "APPROVE" ? (
                <p className="text-xs text-muted-foreground leading-relaxed">
                  Approving this merchant grants immediate access to order fulfillment, parcel tracking, and webhook delivery pipelines.
                </p>
              ) : (
                <div className="space-y-1.5">
                  <Label htmlFor="detailReason">
                    Reason for {decisionType === "REJECT" ? "Rejection" : "Suspension"} *
                  </Label>
                  <textarea
                    id="detailReason"
                    rows={3}
                    value={decisionReason}
                    onChange={(e) => setDecisionReason(e.target.value)}
                    placeholder={
                      decisionType === "REJECT"
                        ? "State the reason why registration documents could not be approved..."
                        : "State the operational reason for suspending this merchant..."
                    }
                    className="w-full rounded-md border border-input bg-background p-2.5 text-xs shadow-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                    required
                    maxLength={500}
                  />
                  <p className="text-[11px] text-muted-foreground">
                    Required. This feedback will be displayed to the merchant.
                  </p>
                </div>
              )}

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/50">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setDecisionType(null)}
                  disabled={isSubmittingDecision}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  size="sm"
                  disabled={isSubmittingDecision}
                  className={cn(
                    "font-semibold",
                    decisionType === "APPROVE" && "bg-emerald-600 hover:bg-emerald-700 text-white",
                    decisionType === "REJECT" && "bg-rose-600 hover:bg-rose-700 text-white",
                    decisionType === "SUSPEND" && "bg-destructive hover:bg-destructive/90 text-white"
                  )}
                >
                  {isSubmittingDecision ? "Submitting..." : `Confirm ${decisionType}`}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}
    </div>
  );
}
