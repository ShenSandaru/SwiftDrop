"use client";

import { useState } from "react";
import Link from "next/link";
import { AlertCircle, CheckCircle2, Phone, ShieldCheck, User as UserIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api, formatApiError, type UserResponse } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

// E.164 phone format: starts with +, followed by 8 to 15 digits
const E164_PHONE_REGEX = /^\+[1-9][0-9]{7,14}$/;

interface ProfileFormProps {
  user: UserResponse;
  refreshUser: () => Promise<void>;
}

function ProfileForm({ user, refreshUser }: ProfileFormProps) {
  const [fullName, setFullName] = useState(user.fullName || "");
  const [phoneNumber, setPhoneNumber] = useState(user.phoneNumber || "");
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (isSaving) return;

    setErrorMessage(null);
    setSuccessMessage(null);

    const trimmedName = fullName.trim();
    const trimmedPhone = phoneNumber.trim();

    if (!trimmedName) {
      setErrorMessage("Please enter your full name.");
      return;
    }
    if (trimmedName.length > 200) {
      setErrorMessage("Full name must be at most 200 characters.");
      return;
    }

    if (!trimmedPhone) {
      setErrorMessage("Please enter your contact phone number.");
      return;
    }
    if (!E164_PHONE_REGEX.test(trimmedPhone)) {
      setErrorMessage(
        "Phone number must be in international E.164 format (e.g. +94771234567 or +14155552671)."
      );
      return;
    }

    setIsSaving(true);

    try {
      await api.patch<UserResponse>("/api/v1/users/me", {
        fullName: trimmedName,
        phoneNumber: trimmedPhone,
      });

      await refreshUser();
      setSuccessMessage("Your profile has been successfully updated.");
    } catch (err: unknown) {
      setErrorMessage(formatApiError(err));
    } finally {
      setIsSaving(false);
    }
  }

  const isCompleted = user.profileCompleted;

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">Customer Profile</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Manage your personal details and contact information for parcel dispatches and notifications.
        </p>
      </div>

      {!isCompleted && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-2xl border border-amber-300 bg-amber-50 p-4 text-amber-900 dark:border-amber-800 dark:bg-amber-950/50 dark:text-amber-100"
        >
          <AlertCircle className="mt-0.5 size-5 shrink-0 text-amber-600 dark:text-amber-400" />
          <div className="space-y-1 text-sm">
            <p className="font-semibold">Complete your profile for streamlined deliveries</p>
            <p className="text-xs opacity-90">
              SwiftDrop uses your full name and contact phone number to coordinate parcel pickups and recipient handovers across the island.
            </p>
          </div>
        </div>
      )}

      {successMessage && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-2xl border border-primary/20 bg-primary/10 p-4 text-primary"
        >
          <CheckCircle2 className="mt-0.5 size-5 shrink-0" />
          <div className="space-y-1 text-sm">
            <p className="font-semibold">{successMessage}</p>
            <p className="text-xs opacity-90">
              You are ready to book island deliveries.{" "}
              <Link href="/customer/new" className="font-semibold underline underline-offset-4 hover:opacity-80">
                Book a parcel now &rarr;
              </Link>
            </p>
          </div>
        </div>
      )}

      <div className="rounded-2xl border border-border bg-card p-6 shadow-xs sm:p-8">
        <div className="flex items-center justify-between border-b border-border pb-4">
          <div className="flex items-center gap-2">
            <UserIcon className="size-5 text-primary" />
            <h2 className="text-base font-semibold text-foreground">Contact Details</h2>
          </div>
          {isCompleted ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">
              <ShieldCheck className="size-3.5" />
              Verified &amp; Complete
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-3 py-1 text-xs font-medium text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">
              Incomplete Profile
            </span>
          )}
        </div>

        <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-5">
          {errorMessage && (
            <div
              role="alert"
              className="rounded-xl border border-destructive/20 bg-destructive/10 p-3 text-xs font-medium text-destructive"
            >
              {errorMessage}
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="profile-email">Email address</Label>
            <Input
              id="profile-email"
              type="email"
              value={user.email || ""}
              disabled
              className="bg-muted text-muted-foreground cursor-not-allowed"
            />
            <p className="text-[11px] text-muted-foreground">
              Your registered SwiftDrop account email address cannot be changed.
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="profile-name">Full name</Label>
            <Input
              id="profile-name"
              type="text"
              autoComplete="name"
              placeholder="e.g. Kasun Perera"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              disabled={isSaving}
              maxLength={200}
              required
            />
            <p className="text-[11px] text-muted-foreground">
              Your primary contact name used for courier receipts and sender manifests.
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="profile-phone" className="inline-flex items-center gap-1.5">
              <Phone className="size-3.5 text-muted-foreground" />
              Phone number
            </Label>
            <Input
              id="profile-phone"
              type="tel"
              autoComplete="tel"
              placeholder="+94771234567"
              value={phoneNumber}
              onChange={(e) => setPhoneNumber(e.target.value)}
              disabled={isSaving}
              maxLength={20}
              required
            />
            <p className="text-[11px] text-muted-foreground">
              International E.164 format with country code (e.g. <span className="font-mono text-foreground">+94771234567</span>). Couriers use this to coordinate pickup and dropoff.
            </p>
          </div>

          <div className="flex flex-col gap-3 pt-3 sm:flex-row sm:items-center sm:justify-end">
            <Button
              type="submit"
              disabled={isSaving}
              className="font-semibold"
            >
              {isSaving ? "Saving..." : isCompleted ? "Update Profile" : "Complete Profile"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function CustomerProfilePage() {
  const { user, refreshUser, isLoading } = useAuth();

  if (isLoading || !user) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <div className="h-8 w-48 animate-pulse rounded bg-muted" />
        <div className="h-64 animate-pulse rounded-2xl bg-card border border-border" />
      </div>
    );
  }

  return <ProfileForm key={user.id} user={user} refreshUser={refreshUser} />;
}
