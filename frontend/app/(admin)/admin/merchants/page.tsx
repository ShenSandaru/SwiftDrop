"use client";

import React, { useEffect, useState, useCallback } from "react";
import { api, formatApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";

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

interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export default function AdminMerchantsPage() {
  const { token } = useAuth();

  const [merchants, setMerchants] = useState<MerchantStatusResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>("PENDING");
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Modal Review Dialog State
  const [selectedMerchant, setSelectedMerchant] = useState<MerchantStatusResponse | null>(null);
  const [isReviewOpen, setIsReviewOpen] = useState(false);
  const [isSubmittingDecision, setIsSubmittingDecision] = useState(false);
  const [decisionMode, setDecisionMode] = useState<"NONE" | "REJECT">("NONE");
  const [rejectionReason, setRejectionReason] = useState("");
  const [dialogError, setDialogError] = useState<string | null>(null);

  const fetchMerchants = useCallback(() => {
    if (!token) return;

    const query = statusFilter === "ALL" ? "" : `?status=${statusFilter}`;
    api
      .get<PageResponse<MerchantStatusResponse>>(`/api/v1/merchants${query}`, { token })
      .then((res) => {
        setMerchants(res.items || []);
        setErrorMessage(null);
      })
      .catch((err: unknown) => {
        setErrorMessage(formatApiError(err));
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, [token, statusFilter]);

  const handleRefresh = useCallback(() => {
    setIsLoading(true);
    fetchMerchants();
  }, [fetchMerchants]);

  useEffect(() => {
    fetchMerchants();
  }, [fetchMerchants]);

  async function handleDecision(decision: "APPROVE" | "REJECT") {
    if (!selectedMerchant || !token) return;

    if (decision === "REJECT" && !rejectionReason.trim()) {
      setDialogError("Please provide a reason for rejecting this application.");
      return;
    }

    setIsSubmittingDecision(true);
    setDialogError(null);

    try {
      await api.post<MerchantStatusResponse>(
        `/api/v1/merchants/${selectedMerchant.merchantId}/approval`,
        {
          decision,
          reason: decision === "REJECT" ? rejectionReason.trim() : undefined,
        },
        { token }
      );

      setIsReviewOpen(false);
      setSelectedMerchant(null);
      setDecisionMode("NONE");
      setRejectionReason("");
      fetchMerchants();
    } catch (err: unknown) {
      setDialogError(formatApiError(err));
    } finally {
      setIsSubmittingDecision(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">
            Merchant Applications Queue
          </h1>
          <p className="text-sm text-muted-foreground">
            Review business credentials and manage merchant platform access approval.
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={handleRefresh}
          disabled={isLoading}
          className="text-xs"
        >
          {isLoading ? "Refreshing..." : "Refresh Queue"}
        </Button>
      </div>

      {/* Filter Tabs */}
      <div className="flex flex-wrap gap-2 border-b border-border/60 pb-3">
        {[
          { id: "PENDING", label: "Pending Review" },
          { id: "APPROVED", label: "Approved Merchants" },
          { id: "REJECTED", label: "Rejected" },
          { id: "SUSPENDED", label: "Suspended" },
          { id: "ALL", label: "All Records" },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setStatusFilter(tab.id)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
              statusFilter === tab.id
                ? "bg-primary text-primary-foreground shadow-xs"
                : "bg-muted/40 text-muted-foreground hover:bg-muted hover:text-foreground"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {errorMessage && (
        <div className="rounded-lg border border-destructive/20 bg-destructive/10 p-4 text-xs text-destructive">
          {errorMessage}
        </div>
      )}

      {/* Applications Table / Cards */}
      <Card className="rounded-xl border-border bg-card shadow-xs overflow-hidden">
        {isLoading ? (
          <div className="p-12 text-center text-sm text-muted-foreground">
            Loading merchant applications...
          </div>
        ) : merchants.length === 0 ? (
          <div className="p-12 text-center space-y-2">
            <p className="text-base font-semibold text-foreground">No applications found</p>
            <p className="text-xs text-muted-foreground">
              There are currently no merchants matching status filter &quot;{statusFilter}&quot;.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-muted/40 border-b border-border/60 text-muted-foreground font-semibold uppercase tracking-wider">
                <tr>
                  <th className="py-3 px-4">Business</th>
                  <th className="py-3 px-4">Type</th>
                  <th className="py-3 px-4">Reg No</th>
                  <th className="py-3 px-4">Contact Phone</th>
                  <th className="py-3 px-4">Submitted</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/40">
                {merchants.map((m) => (
                  <tr key={m.merchantId} className="hover:bg-muted/20 transition-colors">
                    <td className="py-3 px-4 font-semibold text-foreground">
                      {m.businessName}
                    </td>
                    <td className="py-3 px-4 text-muted-foreground">
                      {m.businessType}
                    </td>
                    <td className="py-3 px-4 font-mono text-muted-foreground">
                      {m.businessRegistrationNo}
                    </td>
                    <td className="py-3 px-4 text-muted-foreground">
                      {m.contactPhone}
                    </td>
                    <td className="py-3 px-4 text-muted-foreground">
                      {new Date(m.submittedAt).toLocaleDateString()}
                    </td>
                    <td className="py-3 px-4">
                      {m.status === "PENDING" && (
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20">
                          Pending
                        </span>
                      )}
                      {m.status === "APPROVED" && (
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                          Approved
                        </span>
                      )}
                      {m.status === "REJECTED" && (
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20">
                          Rejected
                        </span>
                      )}
                      {m.status === "SUSPENDED" && (
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20">
                          Suspended
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <Button
                        size="sm"
                        variant="outline"
                        className="text-xs h-7 px-2.5 font-medium"
                        onClick={() => {
                          setSelectedMerchant(m);
                          setIsReviewOpen(true);
                          setDecisionMode("NONE");
                          setRejectionReason("");
                          setDialogError(null);
                        }}
                      >
                        Review
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Review Dialog Modal */}
      {isReviewOpen && selectedMerchant && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <Card className="w-full max-w-lg rounded-xl border-border bg-card shadow-lg max-h-[90vh] overflow-y-auto">
            <CardHeader className="space-y-1 border-b border-border/60 pb-3">
              <div className="flex items-center justify-between">
                <CardTitle className="text-lg font-bold text-foreground">
                  Review Merchant Application
                </CardTitle>
                <button
                  onClick={() => setIsReviewOpen(false)}
                  className="text-muted-foreground hover:text-foreground text-base"
                >
                  ✕
                </button>
              </div>
              <CardDescription className="text-xs">
                Examine business registration and decide platform approval.
              </CardDescription>
            </CardHeader>

            <CardContent className="p-4 space-y-4 text-xs">
              {dialogError && (
                <div className="rounded-lg border border-destructive/20 bg-destructive/10 p-2.5 text-destructive font-medium">
                  {dialogError}
                </div>
              )}

              <div className="grid grid-cols-2 gap-3">
                <div className="rounded-lg bg-muted/30 p-2.5">
                  <span className="text-[10px] text-muted-foreground block">Business Name</span>
                  <span className="font-semibold text-foreground text-sm">{selectedMerchant.businessName}</span>
                </div>
                <div className="rounded-lg bg-muted/30 p-2.5">
                  <span className="text-[10px] text-muted-foreground block">Category / Type</span>
                  <span className="font-medium text-foreground">{selectedMerchant.businessType}</span>
                </div>
                <div className="rounded-lg bg-muted/30 p-2.5">
                  <span className="text-[10px] text-muted-foreground block">Registration No</span>
                  <span className="font-mono font-medium text-foreground">{selectedMerchant.businessRegistrationNo}</span>
                </div>
                <div className="rounded-lg bg-muted/30 p-2.5">
                  <span className="text-[10px] text-muted-foreground block">Contact Phone</span>
                  <span className="font-medium text-foreground">{selectedMerchant.contactPhone}</span>
                </div>
                <div className="rounded-lg bg-muted/30 p-2.5 col-span-2">
                  <span className="text-[10px] text-muted-foreground block">Physical Address</span>
                  <span className="font-medium text-foreground">{selectedMerchant.businessAddress}</span>
                </div>
                {selectedMerchant.businessDescription && (
                  <div className="rounded-lg bg-muted/30 p-2.5 col-span-2">
                    <span className="text-[10px] text-muted-foreground block">Description</span>
                    <span className="text-muted-foreground">{selectedMerchant.businessDescription}</span>
                  </div>
                )}
                {selectedMerchant.websiteUrl && (
                  <div className="rounded-lg bg-muted/30 p-2.5 col-span-2">
                    <span className="text-[10px] text-muted-foreground block">Website</span>
                    <a
                      href={selectedMerchant.websiteUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-primary hover:underline"
                    >
                      {selectedMerchant.websiteUrl}
                    </a>
                  </div>
                )}
              </div>

              {/* Rejection Reason Form */}
              {decisionMode === "REJECT" && (
                <div className="space-y-2 border-t border-border/60 pt-3">
                  <Label htmlFor="rejection-reason" className="text-xs font-semibold text-destructive">
                    Reason for Rejection *
                  </Label>
                  <textarea
                    id="rejection-reason"
                    rows={3}
                    placeholder="Provide specific feedback on why the application is not approved (e.g. invalid registration document, unreachable contact)..."
                    value={rejectionReason}
                    onChange={(e) => setRejectionReason(e.target.value)}
                    className="w-full rounded-md border border-input bg-background p-2 text-xs text-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                    required
                  />
                  <div className="flex justify-end gap-2 pt-1">
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setDecisionMode("NONE")}
                      disabled={isSubmittingDecision}
                    >
                      Back
                    </Button>
                    <Button
                      size="sm"
                      variant="destructive"
                      onClick={() => handleDecision("REJECT")}
                      disabled={isSubmittingDecision}
                    >
                      {isSubmittingDecision ? "Rejecting..." : "Confirm Rejection"}
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>

            {decisionMode === "NONE" && (
              <div className="flex items-center justify-between p-4 border-t border-border/60 bg-muted/20">
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setIsReviewOpen(false)}
                  disabled={isSubmittingDecision}
                >
                  Close
                </Button>

                <div className="flex gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    className="text-destructive border-destructive/30 hover:bg-destructive/10"
                    onClick={() => setDecisionMode("REJECT")}
                    disabled={isSubmittingDecision}
                  >
                    Reject
                  </Button>

                  <Button
                    size="sm"
                    className="bg-emerald-600 hover:bg-emerald-700 text-white font-semibold"
                    onClick={() => handleDecision("APPROVE")}
                    disabled={isSubmittingDecision}
                  >
                    {isSubmittingDecision ? "Approving..." : "Approve Merchant"}
                  </Button>
                </div>
              </div>
            )}
          </Card>
        </div>
      )}
    </div>
  );
}
