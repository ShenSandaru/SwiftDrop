"use client";

import React, { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import {
  api,
  formatApiError,
  type UserResponse,
  type UserAddressResponse,
  type CreateAddressRequest,
} from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Shield,
  Truck,
  User,
  Mail,
  KeyRound,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Eye,
  EyeOff,
  ArrowLeft,
  Calendar,
  BadgeCheck,
  Phone,
  MapPin,
  Plus,
  Trash2,
  Star,
  Building,
} from "lucide-react";

export default function ProfilePage() {
  const { user, token, isAuthenticated, isLoading, refreshUser, hasRole } = useAuth();

  const isCustomer = hasRole("CUSTOMER");

  // Profile edit state
  const [fullName, setFullName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [profileSaving, setProfileSaving] = useState(false);
  const [profileSuccess, setProfileSuccess] = useState<string | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);

  // Password change state
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordSuccess, setPasswordSuccess] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  // Address book state (Customer-only)
  const [addresses, setAddresses] = useState<UserAddressResponse[]>([]);
  const [addressesLoading, setAddressesLoading] = useState(false);
  const [addressError, setAddressError] = useState<string | null>(null);
  const [addressSuccess, setAddressSuccess] = useState<string | null>(null);
  const [showAddressForm, setShowAddressForm] = useState(false);
  const [addressSaving, setAddressSaving] = useState(false);

  // New address form fields
  const [addrLabel, setAddrLabel] = useState("");
  const [addrRecipient, setAddrRecipient] = useState("");
  const [addrPhone, setAddrPhone] = useState("");
  const [addrStreet, setAddrStreet] = useState("");
  const [addrCity, setAddrCity] = useState("");
  const [addrPostal, setAddrPostal] = useState("");
  const [addrIsDefault, setAddrIsDefault] = useState(false);

  useEffect(() => {
    if (user?.fullName) {
      queueMicrotask(() => {
        setFullName(user.fullName);
        setPhoneNumber(user.phoneNumber || "");
      });
    }
  }, [user?.fullName, user?.phoneNumber]);

  const loadAddresses = useCallback(async () => {
    if (!token || !isCustomer) return;
    setAddressesLoading(true);
    setAddressError(null);
    try {
      const data = await api.get<UserAddressResponse[]>("/api/v1/users/me/addresses", {
        token,
      });
      setAddresses(data);
    } catch (err: unknown) {
      setAddressError(formatApiError(err));
    } finally {
      setAddressesLoading(false);
    }
  }, [token, isCustomer]);

  useEffect(() => {
    if (isAuthenticated && token && isCustomer) {
      queueMicrotask(() => {
        void loadAddresses();
      });
    }
  }, [isAuthenticated, token, isCustomer, loadAddresses]);

  if (isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center p-12">
        <Loader2 className="size-8 animate-spin text-primary" aria-label="Loading profile" />
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return (
      <div className="container mx-auto max-w-lg px-4 py-16 text-center">
        <Card>
          <CardHeader>
            <CardTitle>Sign in required</CardTitle>
            <CardDescription>Please sign in to view and manage your profile.</CardDescription>
          </CardHeader>
          <CardContent>
            <Link href="/login?redirect=/profile" className={buttonVariants({ className: "w-full" })}>
              Sign In
            </Link>
          </CardContent>
        </Card>
      </div>
    );
  }

  const dashboardHref = hasRole("DISPATCHER_ADMIN")
    ? "/admin"
    : hasRole("COURIER")
    ? "/courier"
    : hasRole("CUSTOMER")
    ? "/customer"
    : "/";

  const profileBadgeLabel = hasRole("DISPATCHER_ADMIN")
    ? "Dispatcher & Admin Profile"
    : hasRole("COURIER")
    ? "Courier Fleet Profile"
    : hasRole("CUSTOMER")
    ? "Customer Account Profile"
    : "User Profile";

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setProfileError(null);
    setProfileSuccess(null);

    const trimmed = fullName.trim();
    if (!trimmed) {
      setProfileError("Full name cannot be blank.");
      return;
    }

    setProfileSaving(true);
    try {
      await api.patch<UserResponse>(
        "/api/v1/users/me",
        {
          fullName: trimmed,
          phoneNumber: phoneNumber.trim() || undefined,
        },
        { token: token || undefined }
      );
      await refreshUser();
      setProfileSuccess("Personal profile updated successfully.");
    } catch (err: unknown) {
      setProfileError(formatApiError(err));
    } finally {
      setProfileSaving(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordError(null);
    setPasswordSuccess(null);

    if (!currentPassword) {
      setPasswordError("Please enter your current password.");
      return;
    }

    if (newPassword.length < 8) {
      setPasswordError("New password must be at least 8 characters long.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setPasswordError("New password and confirmation do not match.");
      return;
    }

    setPasswordSaving(true);
    try {
      await api.post(
        "/api/v1/users/me/password",
        {
          currentPassword,
          newPassword,
        },
        { token: token || undefined }
      );
      setPasswordSuccess("Password changed successfully.");
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err: unknown) {
      setPasswordError(formatApiError(err));
    } finally {
      setPasswordSaving(false);
    }
  };

  const handleAddAddress = async (e: React.FormEvent) => {
    e.preventDefault();
    setAddressError(null);
    setAddressSuccess(null);

    if (!addrLabel.trim() || !addrRecipient.trim() || !addrPhone.trim() || !addrStreet.trim() || !addrCity.trim()) {
      setAddressError("Please fill in all required address fields.");
      return;
    }

    setAddressSaving(true);
    try {
      const payload: CreateAddressRequest = {
        label: addrLabel.trim(),
        recipientName: addrRecipient.trim(),
        phoneNumber: addrPhone.trim(),
        streetAddress: addrStreet.trim(),
        city: addrCity.trim(),
        postalCode: addrPostal.trim() || undefined,
        isDefault: addrIsDefault,
      };

      await api.post("/api/v1/users/me/addresses", payload, { token: token || undefined });
      setAddressSuccess("New delivery address saved successfully.");
      setShowAddressForm(false);
      // Reset form fields
      setAddrLabel("");
      setAddrRecipient("");
      setAddrPhone("");
      setAddrStreet("");
      setAddrCity("");
      setAddrPostal("");
      setAddrIsDefault(false);
      await loadAddresses();
    } catch (err: unknown) {
      setAddressError(formatApiError(err));
    } finally {
      setAddressSaving(false);
    }
  };

  const handleDeleteAddress = async (addressId: string) => {
    setAddressError(null);
    setAddressSuccess(null);
    try {
      await api.delete(`/api/v1/users/me/addresses/${addressId}`, { token: token || undefined });
      setAddressSuccess("Address removed.");
      await loadAddresses();
    } catch (err: unknown) {
      setAddressError(formatApiError(err));
    }
  };

  const handleSetDefaultAddress = async (addressId: string) => {
    setAddressError(null);
    setAddressSuccess(null);
    try {
      await api.patch(`/api/v1/users/me/addresses/${addressId}/default`, undefined, { token: token || undefined });
      setAddressSuccess("Default address updated.");
      await loadAddresses();
    } catch (err: unknown) {
      setAddressError(formatApiError(err));
    }
  };

  const initial = (user.fullName || user.email || "U").trim().charAt(0).toUpperCase();

  return (
    <div className="container mx-auto max-w-4xl px-4 py-8 space-y-8">
      {/* Top Breadcrumb / Return Navigation */}
      <div className="flex items-center justify-between">
        <Link
          href={dashboardHref}
          className="inline-flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="size-4" />
          Back to Dashboard
        </Link>
        <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-accent text-primary border border-primary/20 uppercase tracking-wider">
          {profileBadgeLabel}
        </span>
      </div>

      {/* Header Profile Identity Card */}
      <Card className="overflow-hidden border-border bg-gradient-to-r from-card via-card to-accent/20">
        <CardContent className="p-6 sm:p-8">
          <div className="flex flex-col sm:flex-row items-center sm:items-start gap-6 text-center sm:text-left">
            <div className="size-20 sm:size-24 rounded-2xl bg-primary text-primary-foreground flex items-center justify-center text-3xl sm:text-4xl font-extrabold shadow-md shrink-0">
              {initial}
            </div>

            <div className="space-y-2 flex-1 min-w-0">
              <div className="flex flex-wrap items-center justify-center sm:justify-start gap-2">
                <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight truncate">
                  {user.fullName || "Unnamed User"}
                </h1>
                {user.enabled && (
                  <span className="inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                    <BadgeCheck className="size-3.5" /> Active Account
                  </span>
                )}
              </div>

              <div className="flex flex-wrap items-center justify-center sm:justify-start gap-4 text-sm text-muted-foreground">
                <p className="flex items-center gap-1.5 truncate">
                  <Mail className="size-4 shrink-0" />
                  {user.email}
                </p>
                {user.phoneNumber && (
                  <p className="flex items-center gap-1.5 truncate">
                    <Phone className="size-4 shrink-0" />
                    {user.phoneNumber}
                  </p>
                )}
              </div>

              <div className="flex flex-wrap items-center justify-center sm:justify-start gap-2 pt-2">
                {user.roles.map((r) => {
                  if (r === "CUSTOMER") {
                    return (
                      <span
                        key={r}
                        className="inline-flex items-center gap-1.5 rounded-md bg-teal-500/10 text-teal-700 dark:text-teal-300 border border-teal-500/20 px-2.5 py-1 text-xs font-semibold"
                      >
                        <User className="size-3.5" /> Customer Account
                      </span>
                    );
                  }
                  if (r === "DISPATCHER_ADMIN") {
                    return (
                      <span
                        key={r}
                        className="inline-flex items-center gap-1.5 rounded-md bg-purple-500/10 text-purple-700 dark:text-purple-300 border border-purple-500/20 px-2.5 py-1 text-xs font-semibold"
                      >
                        <Shield className="size-3.5" /> Dispatcher & Admin
                      </span>
                    );
                  }
                  if (r === "COURIER") {
                    return (
                      <span
                        key={r}
                        className="inline-flex items-center gap-1.5 rounded-md bg-blue-500/10 text-blue-700 dark:text-blue-300 border border-blue-500/20 px-2.5 py-1 text-xs font-semibold"
                      >
                        <Truck className="size-3.5" /> Courier Fleet
                      </span>
                    );
                  }
                  return (
                    <span
                      key={r}
                      className="inline-flex items-center gap-1 rounded-md bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground"
                    >
                      {r}
                    </span>
                  );
                })}

                {user.createdAt && (
                  <span className="inline-flex items-center gap-1 text-xs text-muted-foreground ml-auto">
                    <Calendar className="size-3.5" />
                    Member since {new Date(user.createdAt).toLocaleDateString(undefined, { month: "short", year: "numeric" })}
                  </span>
                )}
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Main Settings Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* Card 1: Personal Details Form */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <User className="size-5 text-primary" />
              <CardTitle className="text-lg">Personal Details</CardTitle>
            </div>
            <CardDescription>Update your display name and contact phone number.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleProfileSubmit} className="space-y-4">
              {profileSuccess && (
                <div className="flex items-center gap-2 p-3 text-sm rounded-lg bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border border-emerald-500/20">
                  <CheckCircle2 className="size-4 shrink-0" />
                  <span>{profileSuccess}</span>
                </div>
              )}

              {profileError && (
                <div className="flex items-center gap-2 p-3 text-sm rounded-lg bg-destructive/10 text-destructive border border-destructive/20">
                  <AlertCircle className="size-4 shrink-0" />
                  <span>{profileError}</span>
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="email">Email Address</Label>
                <div className="relative">
                  <Input
                    id="email"
                    type="email"
                    value={user.email}
                    disabled
                    className="bg-muted cursor-not-allowed pl-9"
                  />
                  <Mail className="size-4 absolute left-3 top-3 text-muted-foreground" />
                </div>
                <p className="text-[11px] text-muted-foreground">
                  Email address is linked to your authentication login.
                </p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="fullName">Full Name</Label>
                <div className="relative">
                  <Input
                    id="fullName"
                    type="text"
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    required
                    maxLength={200}
                    placeholder="e.g. John Doe"
                    className="pl-9"
                  />
                  <User className="size-4 absolute left-3 top-3 text-muted-foreground" />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="phoneNumber">Phone Number</Label>
                <div className="relative">
                  <Input
                    id="phoneNumber"
                    type="tel"
                    value={phoneNumber}
                    onChange={(e) => setPhoneNumber(e.target.value)}
                    maxLength={30}
                    placeholder="e.g. +94 77 123 4567"
                    className="pl-9"
                  />
                  <Phone className="size-4 absolute left-3 top-3 text-muted-foreground" />
                </div>
                <p className="text-[11px] text-muted-foreground">
                  Used by couriers and dispatchers for delivery coordination.
                </p>
              </div>

              <Button type="submit" disabled={profileSaving} className="w-full">
                {profileSaving ? (
                  <>
                    <Loader2 className="mr-2 size-4 animate-spin" /> Saving...
                  </>
                ) : (
                  "Save Personal Details"
                )}
              </Button>
            </form>
          </CardContent>
        </Card>

        {/* Card 2: Security & Password Form */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <KeyRound className="size-5 text-primary" />
              <CardTitle className="text-lg">Security & Password</CardTitle>
            </div>
            <CardDescription>Change your password to keep your account protected.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handlePasswordSubmit} className="space-y-4">
              {passwordSuccess && (
                <div className="flex items-center gap-2 p-3 text-sm rounded-lg bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border border-emerald-500/20">
                  <CheckCircle2 className="size-4 shrink-0" />
                  <span>{passwordSuccess}</span>
                </div>
              )}

              {passwordError && (
                <div className="flex items-center gap-2 p-3 text-sm rounded-lg bg-destructive/10 text-destructive border border-destructive/20">
                  <AlertCircle className="size-4 shrink-0" />
                  <span>{passwordError}</span>
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="currentPassword">Current Password</Label>
                <div className="relative">
                  <Input
                    id="currentPassword"
                    type={showCurrent ? "text" : "password"}
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    required
                    placeholder="Enter current password"
                    className="pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowCurrent(!showCurrent)}
                    className="absolute right-3 top-2.5 text-muted-foreground hover:text-foreground"
                    aria-label={showCurrent ? "Hide password" : "Show password"}
                  >
                    {showCurrent ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                  </button>
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="newPassword">New Password</Label>
                <div className="relative">
                  <Input
                    id="newPassword"
                    type={showNew ? "text" : "password"}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    required
                    minLength={8}
                    maxLength={72}
                    placeholder="At least 8 characters"
                    className="pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowNew(!showNew)}
                    className="absolute right-3 top-2.5 text-muted-foreground hover:text-foreground"
                    aria-label={showNew ? "Hide password" : "Show password"}
                  >
                    {showNew ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                  </button>
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="confirmPassword">Confirm New Password</Label>
                <div className="relative">
                  <Input
                    id="confirmPassword"
                    type={showConfirm ? "text" : "password"}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                    minLength={8}
                    maxLength={72}
                    placeholder="Re-enter new password"
                    className="pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirm(!showConfirm)}
                    className="absolute right-3 top-2.5 text-muted-foreground hover:text-foreground"
                    aria-label={showConfirm ? "Hide password" : "Show password"}
                  >
                    {showConfirm ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                  </button>
                </div>
              </div>

              <Button type="submit" disabled={passwordSaving} variant="outline" className="w-full">
                {passwordSaving ? (
                  <>
                    <Loader2 className="mr-2 size-4 animate-spin" /> Updating Password...
                  </>
                ) : (
                  "Update Password"
                )}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>

      {/* Card 3: Saved Delivery Addresses (Address Book - Customer Only) */}
      {isCustomer && (
        <Card>
          <CardHeader className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <MapPin className="size-5 text-primary" />
                <CardTitle className="text-lg">Saved Delivery Addresses</CardTitle>
              </div>
              <CardDescription>
                Manage your saved locations for quick and easy parcel pickups or deliveries.
              </CardDescription>
            </div>
            <Button
              onClick={() => {
                setAddressError(null);
                setAddressSuccess(null);
                setShowAddressForm(!showAddressForm);
              }}
              size="sm"
              variant={showAddressForm ? "secondary" : "default"}
              className="shrink-0"
            >
              {showAddressForm ? "Cancel" : <><Plus className="mr-1.5 size-4" /> Add New Address</>}
            </Button>
          </CardHeader>
          <CardContent className="space-y-6">
          {addressSuccess && (
            <div className="flex items-center gap-2 p-3 text-sm rounded-lg bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border border-emerald-500/20">
              <CheckCircle2 className="size-4 shrink-0" />
              <span>{addressSuccess}</span>
            </div>
          )}

          {addressError && (
            <div className="flex items-center gap-2 p-3 text-sm rounded-lg bg-destructive/10 text-destructive border border-destructive/20">
              <AlertCircle className="size-4 shrink-0" />
              <span>{addressError}</span>
            </div>
          )}

          {/* Add Address Form Collapsible */}
          {showAddressForm && (
            <form onSubmit={handleAddAddress} className="p-4 sm:p-5 rounded-xl border border-primary/20 bg-accent/10 space-y-4">
              <div className="flex items-center gap-2 font-semibold text-sm">
                <Building className="size-4 text-primary" />
                <span>New Address Details</span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="addrLabel" className="text-xs">Address Label *</Label>
                  <Input
                    id="addrLabel"
                    placeholder="e.g. Home, Office, Central Hub"
                    value={addrLabel}
                    onChange={(e) => setAddrLabel(e.target.value)}
                    required
                    maxLength={50}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="addrRecipient" className="text-xs">Recipient / Contact Name *</Label>
                  <Input
                    id="addrRecipient"
                    placeholder="e.g. Alice Smith"
                    value={addrRecipient}
                    onChange={(e) => setAddrRecipient(e.target.value)}
                    required
                    maxLength={200}
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="addrPhone" className="text-xs">Contact Phone *</Label>
                  <Input
                    id="addrPhone"
                    placeholder="e.g. +94 77 123 4567"
                    value={addrPhone}
                    onChange={(e) => setAddrPhone(e.target.value)}
                    required
                    maxLength={30}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="addrStreet" className="text-xs">Street Address *</Label>
                  <Input
                    id="addrStreet"
                    placeholder="e.g. 123 Main Street, Suite 4B"
                    value={addrStreet}
                    onChange={(e) => setAddrStreet(e.target.value)}
                    required
                    maxLength={255}
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="addrCity" className="text-xs">City / Town *</Label>
                  <Input
                    id="addrCity"
                    placeholder="e.g. Colombo, Kandy"
                    value={addrCity}
                    onChange={(e) => setAddrCity(e.target.value)}
                    required
                    maxLength={100}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="addrPostal" className="text-xs">Postal Code (Optional)</Label>
                  <Input
                    id="addrPostal"
                    placeholder="e.g. 00300"
                    value={addrPostal}
                    onChange={(e) => setAddrPostal(e.target.value)}
                    maxLength={20}
                  />
                </div>
              </div>

              <div className="flex items-center gap-2 pt-1">
                <input
                  id="addrDefault"
                  type="checkbox"
                  checked={addrIsDefault}
                  onChange={(e) => setAddrIsDefault(e.target.checked)}
                  className="rounded border-border text-primary focus:ring-primary size-4"
                />
                <Label htmlFor="addrDefault" className="text-xs cursor-pointer">
                  Set as default delivery address
                </Label>
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <Button type="button" variant="outline" size="sm" onClick={() => setShowAddressForm(false)}>
                  Cancel
                </Button>
                <Button type="submit" size="sm" disabled={addressSaving}>
                  {addressSaving ? <><Loader2 className="mr-1.5 size-3.5 animate-spin" /> Saving...</> : "Save Address"}
                </Button>
              </div>
            </form>
          )}

          {/* Addresses List */}
          {addressesLoading ? (
            <div className="flex justify-center py-8">
              <Loader2 className="size-6 animate-spin text-muted-foreground" />
            </div>
          ) : addresses.length === 0 ? (
            <div className="text-center py-8 border border-dashed rounded-xl border-border space-y-2">
              <MapPin className="size-8 text-muted-foreground mx-auto opacity-40" />
              <p className="text-sm font-medium text-muted-foreground">No saved addresses yet.</p>
              <p className="text-xs text-muted-foreground max-w-sm mx-auto">
                Save your frequently used pickup or drop-off locations to speed up booking deliveries.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {addresses.map((addr) => (
                <div
                  key={addr.id}
                  className={`p-4 rounded-xl border transition-all ${
                    addr.isDefault
                      ? "border-primary/50 bg-primary/5 shadow-xs"
                      : "border-border bg-card hover:border-border/80"
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="space-y-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-sm truncate">{addr.label}</span>
                        {addr.isDefault && (
                          <span className="inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                            <Star className="size-3 fill-emerald-500" /> Default
                          </span>
                        )}
                      </div>
                      <p className="text-xs font-medium text-foreground">{addr.recipientName}</p>
                      <p className="text-xs text-muted-foreground">{addr.phoneNumber}</p>
                      <p className="text-xs text-muted-foreground pt-1">
                        {addr.streetAddress}, {addr.city} {addr.postalCode ? `(${addr.postalCode})` : ""}
                      </p>
                    </div>

                    <div className="flex items-center gap-1 shrink-0">
                      {!addr.isDefault && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleSetDefaultAddress(addr.id)}
                          title="Set as Default"
                          className="h-8 px-2 text-xs text-muted-foreground hover:text-foreground"
                        >
                          Set Default
                        </Button>
                      )}
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleDeleteAddress(addr.id)}
                        title="Delete Address"
                        className="h-8 w-8 text-muted-foreground hover:text-destructive"
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
      )}

      {/* Customer Quick Links */}
      {hasRole("CUSTOMER") && (
        <Card className="border-border">
          <CardHeader>
            <CardTitle className="text-lg">Customer Deliveries</CardTitle>
            <CardDescription>Quick access to manage and track your parcels</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-3">
            <Link href="/customer/new" className={buttonVariants({ variant: "default", size: "sm" })}>
              Book New Delivery
            </Link>
            <Link href="/customer" className={buttonVariants({ variant: "outline", size: "sm" })}>
              Active Deliveries
            </Link>
            <Link href="/customer/history" className={buttonVariants({ variant: "outline", size: "sm" })}>
              Delivery History
            </Link>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
