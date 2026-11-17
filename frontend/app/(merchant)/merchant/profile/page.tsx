"use client";

import React, { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { formatApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import {
  fetchMerchantProfile,
  updateMerchantProfile,
  type MerchantProfile,
  type MerchantProfileUpdateRequest,
} from "@/lib/merchant";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "cn";
import {
  AlertCircle,
  CheckCircle2,
  Clock,
  RefreshCw,
  Send,
  ShieldAlert,
} from "lucide-react";

const PHONE_REGEX = /^(\+94|0)[0-9]{9}$/;

const BUSINESS_TYPES = [
  "Grocery",
  "Restaurant",
  "Pharmacy",
  "Fashion & Apparel",
  "Electronics",
  "Retail Store",
  "Wholesale & Distribution",
  "Other",
];

export default function MerchantProfilePage() {
  const { token, logout } = useAuth();

  const [profile, setProfile] = useState<MerchantProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Form Fields
  const [businessName, setBusinessName] = useState("");
  const [businessType, setBusinessType] = useState("Grocery");
  const [contactPhone, setContactPhone] = useState("");
  const [businessAddress, setBusinessAddress] = useState("");
  const [businessDescription, setBusinessDescription] = useState("");
  const [websiteUrl, setWebsiteUrl] = useState("");

  const handleRefresh = useCallback(async () => {
    if (!token) return;
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await fetchMerchantProfile(token);
      setProfile(data);
      setBusinessName(data.businessName || "");
      setBusinessType(data.businessType || "Grocery");
      setContactPhone(data.contactPhone || "");
      setBusinessAddress(data.businessAddress || "");
      setBusinessDescription(data.businessDescription || "");
      setWebsiteUrl(data.websiteUrl || "");
    } catch (err: unknown) {
      setErrorMessage(formatApiError(err));
    } finally {
      setIsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    let ignore = false;
    if (token) {
      fetchMerchantProfile(token)
        .then((data) => {
          if (ignore) return;
          setProfile(data);
          setBusinessName(data.businessName || "");
          setBusinessType(data.businessType || "Grocery");
          setContactPhone(data.contactPhone || "");
          setBusinessAddress(data.businessAddress || "");
          setBusinessDescription(data.businessDescription || "");
          setWebsiteUrl(data.websiteUrl || "");
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
  }, [token]);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!token || isSaving) return;

    setErrorMessage(null);
    setSuccessMessage(null);

    const trimmedName = businessName.trim();
    const trimmedPhone = contactPhone.trim().replace(/\s+/g, "");
    const trimmedAddress = businessAddress.trim();
    const trimmedDesc = businessDescription.trim();
    const trimmedWebsite = websiteUrl.trim();

    if (!trimmedName || trimmedName.length < 2 || trimmedName.length > 150) {
      setErrorMessage("Business name must be between 2 and 150 characters.");
      return;
    }

    if (!trimmedPhone || !PHONE_REGEX.test(trimmedPhone)) {
      setErrorMessage("Contact phone must be a valid Sri Lankan phone number (+94... or 07...).");
      return;
    }

    if (!trimmedAddress || trimmedAddress.length < 5 || trimmedAddress.length > 500) {
      setErrorMessage("Business address must be between 5 and 500 characters.");
      return;
    }

    if (trimmedDesc.length > 500) {
      setErrorMessage("Business description must be at most 500 characters.");
      return;
    }

    if (trimmedWebsite && !/^https?:\/\/.+/.test(trimmedWebsite)) {
      setErrorMessage("Website URL must start with http:// or https://");
      return;
    }

    setIsSaving(true);

    const payload: MerchantProfileUpdateRequest = {
      businessName: trimmedName,
      businessType,
      contactPhone: trimmedPhone,
      businessAddress: trimmedAddress,
      businessDescription: trimmedDesc || undefined,
      websiteUrl: trimmedWebsite || undefined,
    };

    try {
      const updated = await updateMerchantProfile(payload, token);
      setProfile(updated);
      setSuccessMessage(
        updated.status === "PENDING"
          ? "Profile updated and your merchant application has been resubmitted for admin review."
          : "Business profile details updated successfully."
      );
    } catch (err: unknown) {
      setErrorMessage(formatApiError(err));
    } finally {
      setIsSaving(false);
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[40vh]">
        <div className="flex flex-col items-center gap-3 text-muted-foreground">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-xs">Loading merchant profile...</p>
        </div>
      </div>
    );
  }

  const status = profile?.status;
  const isPending = status === "PENDING";
  const isApproved = status === "APPROVED";
  const isRejected = status === "REJECTED";
  const isSuspended = status === "SUSPENDED";

  return (
    <div className="max-w-3xl mx-auto space-y-6 pb-12">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">
            Merchant Business Profile
          </h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            Manage your registered business credentials, contact info, and view review feedback.
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
          <Button variant="ghost" size="sm" onClick={logout} className="text-xs text-muted-foreground">
            Sign out
          </Button>
        </div>
      </div>

      {/* Rejection / Resubmission Alert */}
      {isRejected && (
        <Card className="rounded-xl border-amber-500/30 bg-amber-500/10 p-5 space-y-3">
          <div className="flex items-start gap-3">
            <ShieldAlert className="size-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
            <div className="space-y-1">
              <h2 className="text-sm font-bold text-amber-800 dark:text-amber-300">
                Application Review Decision: Additional Changes Required
              </h2>
              <p className="text-xs text-amber-900/80 dark:text-amber-300/80 leading-relaxed">
                Your application could not be approved based on the submitted details. You may update your information
                below and resubmit for administrator reconsideration.
              </p>
              {profile?.decisionReason && (
                <div className="mt-2 rounded-lg bg-background/80 border border-amber-500/30 p-3 text-xs">
                  <span className="font-semibold text-foreground">Reviewer Reason: </span>
                  <span className="text-muted-foreground">{profile.decisionReason}</span>
                </div>
              )}
            </div>
          </div>
        </Card>
      )}

      {/* Suspended Alert */}
      {isSuspended && (
        <Card className="rounded-xl border-destructive/30 bg-destructive/10 p-5 space-y-3">
          <div className="flex items-start gap-3">
            <AlertCircle className="size-5 text-destructive shrink-0 mt-0.5" />
            <div className="space-y-1">
              <h2 className="text-sm font-bold text-destructive">Account Suspended</h2>
              <p className="text-xs text-muted-foreground leading-relaxed">
                Your merchant operations and automated intake have been temporarily halted by system administrators.
              </p>
              {profile?.decisionReason && (
                <div className="mt-2 rounded-lg bg-background/80 border border-destructive/30 p-3 text-xs">
                  <span className="font-semibold text-foreground">Notice: </span>
                  <span className="text-muted-foreground">{profile.decisionReason}</span>
                </div>
              )}
            </div>
          </div>
        </Card>
      )}

      {/* Status Messages */}
      {errorMessage && (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive flex items-center gap-2">
          <AlertCircle className="size-4 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      {successMessage && (
        <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/5 p-4 text-sm text-emerald-600 dark:text-emerald-400 flex items-center gap-2">
          <CheckCircle2 className="size-4 shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Profile Form */}
      <form onSubmit={handleSubmit}>
        <Card className="rounded-xl border-border bg-card shadow-xs">
          <CardHeader className="border-b border-border/50 pb-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div>
                <CardTitle className="text-base font-bold">Business Information</CardTitle>
                <CardDescription className="text-xs">
                  Official registered details and primary operational address.
                </CardDescription>
              </div>

              {/* Status Badge */}
              <div>
                {isApproved && (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                    <CheckCircle2 className="size-3.5" />
                    Approved
                  </span>
                )}
                {isPending && (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20">
                    <Clock className="size-3.5" />
                    Pending Review
                  </span>
                )}
                {isRejected && (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20">
                    <AlertCircle className="size-3.5" />
                    Action Required
                  </span>
                )}
                {isSuspended && (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-destructive/10 text-destructive border border-destructive/20">
                    <ShieldAlert className="size-3.5" />
                    Suspended
                  </span>
                )}
              </div>
            </div>
          </CardHeader>

          <CardContent className="space-y-4 pt-6">
            {/* Read-Only Legal Attributes */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 p-4 rounded-lg bg-muted/40 border border-border/60 text-xs">
              <div>
                <span className="text-muted-foreground block text-[11px] font-semibold uppercase tracking-wider">
                  Registration Number (BRN)
                </span>
                <span className="font-mono font-semibold text-foreground text-sm">
                  {profile?.businessRegistrationNo || "—"}
                </span>
                <span className="text-[11px] text-muted-foreground block mt-0.5">
                  Legal entity registration cannot be changed online.
                </span>
              </div>

              <div>
                <span className="text-muted-foreground block text-[11px] font-semibold uppercase tracking-wider">
                  Initial Submission
                </span>
                <span className="font-medium text-foreground">
                  {profile?.submittedAt ? new Date(profile.submittedAt).toLocaleString() : "—"}
                </span>
                {profile?.decidedAt && (
                  <span className="text-[11px] text-muted-foreground block mt-0.5">
                    Last Decision: {new Date(profile.decidedAt).toLocaleString()}
                  </span>
                )}
              </div>
            </div>

            {/* Editable Fields */}
            <div className="space-y-2">
              <Label htmlFor="businessName">Business Name *</Label>
              <Input
                id="businessName"
                value={businessName}
                onChange={(e) => setBusinessName(e.target.value)}
                placeholder="e.g. Lanka Goods Mart"
                required
                maxLength={150}
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="businessType">Business Category *</Label>
                <select
                  id="businessType"
                  value={businessType}
                  onChange={(e) => setBusinessType(e.target.value)}
                  className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-xs transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                >
                  {BUSINESS_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="contactPhone">Contact Phone (+94 or 07...) *</Label>
                <Input
                  id="contactPhone"
                  value={contactPhone}
                  onChange={(e) => setContactPhone(e.target.value)}
                  placeholder="+94771234567"
                  required
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="businessAddress">Operating Address *</Label>
              <Input
                id="businessAddress"
                value={businessAddress}
                onChange={(e) => setBusinessAddress(e.target.value)}
                placeholder="No. 123, Galle Road, Colombo 03"
                required
                maxLength={500}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="websiteUrl">Website URL (Optional)</Label>
              <Input
                id="websiteUrl"
                type="url"
                value={websiteUrl}
                onChange={(e) => setWebsiteUrl(e.target.value)}
                placeholder="https://example.com"
                maxLength={2048}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="businessDescription">Business Description (Optional)</Label>
              <textarea
                id="businessDescription"
                rows={3}
                value={businessDescription}
                onChange={(e) => setBusinessDescription(e.target.value)}
                placeholder="Brief summary of your product offerings and typical fulfillment volumes..."
                className="w-full rounded-md border border-input bg-background p-3 text-sm shadow-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                maxLength={500}
              />
              <span className="text-[11px] text-muted-foreground block text-right">
                {businessDescription.length}/500
              </span>
            </div>
          </CardContent>

          <div className="p-6 pt-0 flex flex-col sm:flex-row items-center justify-between gap-4 border-t border-border/50">
            <Link
              href="/merchant/status"
              className={cn(buttonVariants({ variant: "ghost", size: "sm" }))}
            >
              View Application Timeline &rarr;
            </Link>

            <Button
              type="submit"
              disabled={isSaving}
              className={cn(
                "w-full sm:w-auto font-semibold gap-2",
                isRejected && "bg-amber-600 hover:bg-amber-700 text-white"
              )}
            >
              {isSaving ? (
                <>
                  <RefreshCw className="size-4 animate-spin" />
                  Saving Changes...
                </>
              ) : isRejected ? (
                <>
                  <Send className="size-4" />
                  Resubmit Application
                </>
              ) : (
                "Save Profile Changes"
              )}
            </Button>
          </div>
        </Card>
      </form>
    </div>
  );
}
