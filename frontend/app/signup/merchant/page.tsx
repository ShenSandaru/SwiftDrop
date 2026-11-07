"use client";

import React, { useState } from "react";
import Link from "next/link";
import { api, ApiError, formatApiError } from "@/lib/api";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "cn";

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_REGEX = /^(\+94|0)[0-9]{9}$/;
const REG_NO_REGEX = /^[A-Za-z0-9/-]{3,50}$/;
const MAX_PASSWORD_BYTES = 72;

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

interface MerchantApplicationResponse {
  merchantId: string;
  status: string;
}

export default function MerchantSignupPage() {
  // Account Details
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  // Business Details
  const [businessName, setBusinessName] = useState("");
  const [businessType, setBusinessType] = useState("Grocery");
  const [businessRegistrationNo, setBusinessRegistrationNo] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [businessAddress, setBusinessAddress] = useState("");
  const [businessDescription, setBusinessDescription] = useState("");
  const [websiteUrl, setWebsiteUrl] = useState("");

  // State
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isConflict, setIsConflict] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (isLoading) return;

    setErrorMessage(null);
    setIsConflict(false);

    // Trim and normalize values
    const trimmedName = fullName.trim();
    const trimmedEmail = email.trim().toLowerCase();
    const trimmedBusinessName = businessName.trim();
    const trimmedRegNo = businessRegistrationNo.trim();
    const trimmedPhone = contactPhone.trim().replace(/\s+/g, "");
    const trimmedAddress = businessAddress.trim();
    const trimmedDesc = businessDescription.trim();
    const trimmedWebsite = websiteUrl.trim();

    // 1. Account Details Validation
    if (!trimmedName || trimmedName.length < 2 || trimmedName.length > 100) {
      setErrorMessage("Full name must be between 2 and 100 characters.");
      return;
    }

    if (!trimmedEmail || !EMAIL_REGEX.test(trimmedEmail) || trimmedEmail.length > 254) {
      setErrorMessage("Please enter a valid business email address (max 254 characters).");
      return;
    }

    if (!password || password.length < 8) {
      setErrorMessage("Password must be at least 8 characters long.");
      return;
    }

    const passwordBytes = new TextEncoder().encode(password).length;
    if (passwordBytes > MAX_PASSWORD_BYTES) {
      setErrorMessage(`Password exceeds the maximum allowed length of ${MAX_PASSWORD_BYTES} bytes.`);
      return;
    }

    if (password !== confirmPassword) {
      setErrorMessage("Passwords do not match. Please re-enter your password.");
      return;
    }

    // 2. Business Details Validation
    if (!trimmedBusinessName || trimmedBusinessName.length < 2 || trimmedBusinessName.length > 150) {
      setErrorMessage("Business name must be between 2 and 150 characters.");
      return;
    }

    if (!trimmedRegNo || !REG_NO_REGEX.test(trimmedRegNo)) {
      setErrorMessage("Business registration number must be 3-50 alphanumeric characters, hyphens or slashes.");
      return;
    }

    if (!trimmedPhone || !PHONE_REGEX.test(trimmedPhone)) {
      setErrorMessage("Contact phone must be a valid Sri Lankan phone number (+94... or 07...).");
      return;
    }

    if (!trimmedAddress || trimmedAddress.length < 5 || trimmedAddress.length > 300) {
      setErrorMessage("Business address must be between 5 and 300 characters.");
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

    setIsLoading(true);

    try {
      await api.post<MerchantApplicationResponse>("/api/v1/merchants", {
        fullName: trimmedName,
        email: trimmedEmail,
        password,
        businessName: trimmedBusinessName,
        businessType,
        businessRegistrationNo: trimmedRegNo,
        contactPhone: trimmedPhone,
        businessAddress: trimmedAddress,
        businessDescription: trimmedDesc || undefined,
        websiteUrl: trimmedWebsite || undefined,
      });

      setIsSubmitted(true);
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 409) {
        setIsConflict(true);
        setErrorMessage(
          err.problem?.detail || "We couldn't complete this registration. An account or registration number may already exist."
        );
      } else {
        setErrorMessage(formatApiError(err));
      }
    } finally {
      setIsLoading(false);
    }
  }

  // Success Confirmation Screen
  if (isSubmitted) {
    return (
      <main className="container mx-auto flex-1 flex items-center justify-center px-4 py-12 min-h-[calc(100vh-3.5rem)]">
        <Card className="w-full max-w-lg rounded-xl bg-card border-border shadow-xs text-center p-6 space-y-6">
          <div className="size-16 rounded-full bg-emerald-500/10 text-emerald-500 flex items-center justify-center text-3xl mx-auto">
            ✓
          </div>
          <div className="space-y-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Application Submitted
            </h1>
            <p className="text-sm text-muted-foreground leading-relaxed">
              Your merchant application for <span className="font-semibold text-foreground">{businessName}</span> has been received.
              Our operations team reviews all applications to ensure system compliance.
            </p>
          </div>

          <div className="rounded-lg border border-border/60 bg-muted/30 p-4 text-xs text-muted-foreground text-left space-y-2">
            <div className="flex items-center gap-2 font-semibold text-foreground">
              <span>📋</span> What happens next?
            </div>
            <p>1. An administrator reviews your business details and registration credentials.</p>
            <p>2. You can sign in with your email and password at any time to check your approval status.</p>
            <p>3. Once approved, the merchant dashboard, orders portal, and webhook APIs will unlock automatically.</p>
          </div>

          <div className="pt-2">
            <Link
              href="/login"
              className={cn(buttonVariants({ size: "default" }), "w-full font-semibold")}
            >
              Sign In to Check Status &rarr;
            </Link>
          </div>
        </Card>
      </main>
    );
  }

  return (
    <main className="container mx-auto flex-1 flex flex-col items-center justify-center px-4 py-10 sm:py-14 min-h-[calc(100vh-3.5rem)]">
      <div className="w-full max-w-3xl space-y-6">
        {/* Navigation & Step Indicator Header */}
        <div className="flex items-center justify-between">
          <Link
            href="/signup"
            className="text-xs font-medium text-muted-foreground hover:text-foreground inline-flex items-center gap-1.5 transition-colors"
          >
            &larr; Back to account selection
          </Link>
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-primary/10 text-xs font-semibold uppercase tracking-wider text-primary">
            <span className="size-1.5 rounded-full bg-primary" />
            Merchant Application
          </div>
        </div>

        <Card className="w-full rounded-2xl bg-card border-border shadow-sm overflow-hidden">
          <CardHeader className="p-6 sm:p-8 border-b border-border/60 bg-muted/15 space-y-2">
            <CardTitle className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
              Apply for a Merchant Account
            </CardTitle>
            <CardDescription className="text-sm sm:text-base text-muted-foreground leading-relaxed">
              Register your business with SwiftDrop for automated delivery management, bulk dispatching, and webhook notifications.
            </CardDescription>
          </CardHeader>

          <form onSubmit={handleSubmit} noValidate>
            <CardContent className="p-6 sm:p-8 space-y-8">
              {errorMessage && (
                <div
                  role="alert"
                  className="rounded-xl border border-destructive/20 bg-destructive/10 p-4 text-xs text-destructive font-medium space-y-1.5"
                >
                  <p>{errorMessage}</p>
                  {isConflict && (
                    <p>
                      <Link href="/login" className="font-semibold underline underline-offset-4 hover:text-foreground">
                        Already registered? Click here to sign in &rarr;
                      </Link>
                    </p>
                  )}
                </div>
              )}

              {/* SECTION 1: Account Credentials */}
              <div className="rounded-xl border border-border/70 bg-card p-5 sm:p-6 space-y-6 shadow-2xs">
                <div className="flex items-center gap-3 pb-3 border-b border-border/60">
                  <div className="size-7 rounded-lg bg-primary/10 text-primary text-xs font-bold flex items-center justify-center shrink-0">
                    1
                  </div>
                  <div>
                    <h2 className="text-sm font-bold text-foreground tracking-tight">Account Credentials</h2>
                    <p className="text-xs text-muted-foreground mt-0.5">Primary user login for the merchant management portal</p>
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-5">
                  <div className="space-y-2">
                    <Label htmlFor="merchant-name" className="text-xs font-semibold text-foreground">
                      Contact Person Full Name *
                    </Label>
                    <Input
                      id="merchant-name"
                      type="text"
                      placeholder="Jane Doe"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      disabled={isLoading}
                      maxLength={100}
                      required
                      className="h-10 px-3 bg-background"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="merchant-email" className="text-xs font-semibold text-foreground">
                      Business Email Address *
                    </Label>
                    <Input
                      id="merchant-email"
                      type="email"
                      placeholder="contact@store.com"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      disabled={isLoading}
                      maxLength={254}
                      required
                      className="h-10 px-3 bg-background"
                    />
                  </div>

                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="merchant-password" className="text-xs font-semibold text-foreground">
                        Password *
                      </Label>
                      <span className="text-[11px] text-muted-foreground">8–72 characters</span>
                    </div>
                    <Input
                      id="merchant-password"
                      type="password"
                      placeholder="Create a strong password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      disabled={isLoading}
                      required
                      className="h-10 px-3 bg-background"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="merchant-confirm-password" className="text-xs font-semibold text-foreground">
                      Confirm Password *
                    </Label>
                    <Input
                      id="merchant-confirm-password"
                      type="password"
                      placeholder="Re-enter your password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      disabled={isLoading}
                      required
                      className="h-10 px-3 bg-background"
                    />
                  </div>
                </div>
              </div>

              {/* SECTION 2: Business Profile */}
              <div className="rounded-xl border border-border/70 bg-card p-5 sm:p-6 space-y-6 shadow-2xs">
                <div className="flex items-center gap-3 pb-3 border-b border-border/60">
                  <div className="size-7 rounded-lg bg-primary/10 text-primary text-xs font-bold flex items-center justify-center shrink-0">
                    2
                  </div>
                  <div>
                    <h2 className="text-sm font-bold text-foreground tracking-tight">Business Profile & Verification</h2>
                    <p className="text-xs text-muted-foreground mt-0.5">Official company registration details verified during administrative review</p>
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-5">
                  <div className="space-y-2">
                    <Label htmlFor="business-name" className="text-xs font-semibold text-foreground">
                      Business Name *
                    </Label>
                    <Input
                      id="business-name"
                      type="text"
                      placeholder="e.g. ABC Supermarket"
                      value={businessName}
                      onChange={(e) => setBusinessName(e.target.value)}
                      disabled={isLoading}
                      maxLength={150}
                      required
                      className="h-10 px-3 bg-background"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="business-type" className="text-xs font-semibold text-foreground">
                      Business Type *
                    </Label>
                    <select
                      id="business-type"
                      value={businessType}
                      onChange={(e) => setBusinessType(e.target.value)}
                      disabled={isLoading}
                      className="flex h-10 w-full rounded-lg border border-input bg-background px-3 py-1 text-sm shadow-xs transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {BUSINESS_TYPES.map((type) => (
                        <option key={type} value={type}>
                          {type}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="business-reg" className="text-xs font-semibold text-foreground">
                      Business Registration No *
                    </Label>
                    <Input
                      id="business-reg"
                      type="text"
                      placeholder="e.g. BR-123456"
                      value={businessRegistrationNo}
                      onChange={(e) => setBusinessRegistrationNo(e.target.value)}
                      disabled={isLoading}
                      maxLength={50}
                      required
                      className="h-10 px-3 bg-background font-mono"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="business-phone" className="text-xs font-semibold text-foreground">
                      Contact Phone *
                    </Label>
                    <Input
                      id="business-phone"
                      type="tel"
                      placeholder="e.g. +94 77 123 4567 or 0771234567"
                      value={contactPhone}
                      onChange={(e) => setContactPhone(e.target.value)}
                      disabled={isLoading}
                      required
                      className="h-10 px-3 bg-background"
                    />
                  </div>

                  <div className="sm:col-span-2 space-y-2">
                    <Label htmlFor="business-address" className="text-xs font-semibold text-foreground">
                      Business Physical Address *
                    </Label>
                    <Input
                      id="business-address"
                      type="text"
                      placeholder="e.g. No 45, Galle Road, Colombo 03"
                      value={businessAddress}
                      onChange={(e) => setBusinessAddress(e.target.value)}
                      disabled={isLoading}
                      maxLength={300}
                      required
                      className="h-10 px-3 bg-background"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="business-desc" className="text-xs font-semibold text-foreground">
                      Business Description (Optional)
                    </Label>
                    <Input
                      id="business-desc"
                      type="text"
                      placeholder="e.g. Fresh grocery and household items"
                      value={businessDescription}
                      onChange={(e) => setBusinessDescription(e.target.value)}
                      disabled={isLoading}
                      maxLength={500}
                      className="h-10 px-3 bg-background"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="business-website" className="text-xs font-semibold text-foreground">
                      Website URL (Optional)
                    </Label>
                    <Input
                      id="business-website"
                      type="url"
                      placeholder="https://example.com"
                      value={websiteUrl}
                      onChange={(e) => setWebsiteUrl(e.target.value)}
                      disabled={isLoading}
                      maxLength={2048}
                      className="h-10 px-3 bg-background"
                    />
                  </div>
                </div>
              </div>

              {/* Informational Callout */}
              <div className="rounded-xl border border-border/80 bg-muted/30 p-4 text-xs text-muted-foreground flex items-start gap-3">
                <span className="text-primary text-base font-bold shrink-0">ℹ</span>
                <div className="space-y-1">
                  <p className="font-semibold text-foreground">Application Verification Process</p>
                  <p className="leading-relaxed">
                    Submitted merchant applications are reviewed by system administrators against business registration databases.
                    Your user account is created upon submission, allowing you to log in and inspect live approval status at any time.
                  </p>
                </div>
              </div>

              <Button
                type="submit"
                disabled={isLoading}
                className="w-full h-11 text-sm font-semibold shadow-xs rounded-lg"
              >
                {isLoading ? "Submitting application..." : "Submit Merchant Application"}
              </Button>
            </CardContent>

            <CardFooter className="flex flex-col gap-2 p-6 bg-muted/10 border-t border-border/60 text-center text-xs text-muted-foreground">
              <p>
                Already applied or have an account?{" "}
                <Link href="/login" className="font-semibold text-primary underline-offset-4 hover:underline">
                  Sign in
                </Link>
              </p>
            </CardFooter>
          </form>
        </Card>
      </div>
    </main>
  );
}
