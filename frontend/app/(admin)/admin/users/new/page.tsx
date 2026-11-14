"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  api,
  ApiError,
  formatApiError,
  type CreateStaffUserRequest,
  type HubResponse,
  type PageResponse,
  type UserResponse,
} from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ThemedSelect, type SelectOption } from "@/components/ui/select";
import { AlertCircle, ArrowLeft, Eye, EyeOff, Loader2, Radio, Truck } from "lucide-react";

const STAFF_ROLES = [
  {
    value: "COURIER" as const,
    label: "Courier / Driver",
    description: "Assigned delivery routes, barcode scanning, proof submission, and parcel handoffs.",
    icon: Truck,
  },
  {
    value: "DISPATCHER" as const,
    label: "Dispatcher",
    description: "Fleet tracking, parcel assignment, route monitoring, and exception handling.",
    icon: Radio,
  },
];

export default function NewStaffUserPage() {
  const router = useRouter();

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<"COURIER" | "DISPATCHER">("COURIER");
  const [hubId, setHubId] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const [hubs, setHubs] = useState<HubResponse[]>([]);
  const [hubsLoading, setHubsLoading] = useState(false);
  const [hubsError, setHubsError] = useState<string | null>(null);
  const [assignedDispatcherHubIds, setAssignedDispatcherHubIds] = useState<Set<string>>(new Set());

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    let ignore = false;
    async function loadHubsAndDispatchers() {
      setHubsLoading(true);
      setHubsError(null);
      try {
        const [hubList, dispatcherPage] = await Promise.all([
          api.get<HubResponse[]>("/api/v1/hubs"),
          api.get<PageResponse<UserResponse>>("/api/v1/users?role=DISPATCHER&size=100"),
        ]);
        if (ignore) return;
        const sortedHubs = [...hubList].sort((a, b) => {
          if (a.central !== b.central) return a.central ? -1 : 1;
          return a.name.localeCompare(b.name);
        });
        setHubs(sortedHubs);
        const assigned = new Set<string>();
        for (const d of dispatcherPage.items) {
          if (d.hubId) assigned.add(d.hubId);
        }
        setAssignedDispatcherHubIds(assigned);
      } catch (err: unknown) {
        if (!ignore) {
          setHubsError(formatApiError(err));
        }
      } finally {
        if (!ignore) {
          setHubsLoading(false);
        }
      }
    }
    loadHubsAndDispatchers();
    return () => {
      ignore = true;
    };
  }, []);

  const activeHubs = hubs.filter((h) => h.active);
  const assignedActiveHubCount = activeHubs.filter((h) => assignedDispatcherHubIds.has(h.id)).length;
  const isDispatcher = role === "DISPATCHER";

  const hubSelectOptions: SelectOption[] = hubs.map((hub) => {
    const hasDispatcher = isDispatcher && assignedDispatcherHubIds.has(hub.id);
    let badge: string | undefined;
    let badgeVariant: SelectOption["badgeVariant"] = "outline";
    if (!hub.active && hasDispatcher) {
      badge = "Inactive · Assigned";
      badgeVariant = "warning";
    } else if (!hub.active) {
      badge = "Inactive";
      badgeVariant = "warning";
    } else if (hasDispatcher) {
      badge = "Dispatcher assigned";
      badgeVariant = "outline";
    } else if (hub.central) {
      badge = "Main Hub";
      badgeVariant = "success";
    }
    return {
      value: hub.id,
      label: `${hub.name} (${hub.code})`,
      description: `${hub.city} · ${hub.central ? "Central Hub" : "Sub-hub"}`,
      badge,
      badgeVariant,
      disabled: !hub.active || hasDispatcher,
    };
  });

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (isSubmitting) return;

    setErrors({});
    setGeneralError(null);

    const nextErrors: Record<string, string> = {};
    const trimmedName = fullName.trim();
    const trimmedEmail = email.trim();

    if (!trimmedName) {
      nextErrors.fullName = "Full name is required.";
    } else if (trimmedName.length > 200) {
      nextErrors.fullName = "Full name must not exceed 200 characters.";
    }

    if (!trimmedEmail) {
      nextErrors.email = "Email address is required.";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail) || trimmedEmail.length > 320) {
      nextErrors.email = "Please enter a valid email address (maximum 320 characters).";
    }

    const passwordBytes = new TextEncoder().encode(password).length;
    if (!password) {
      nextErrors.password = "Password is required.";
    } else if (password.length < 8) {
      nextErrors.password = "Password must be at least 8 characters.";
    } else if (passwordBytes > 72) {
      nextErrors.password = "Password must be at most 72 bytes.";
    }

    if (role !== "COURIER" && role !== "DISPATCHER") {
      nextErrors.role = "Role must be COURIER or DISPATCHER.";
    }

    const selectedHub = hubs.find((h) => h.id === hubId);
    if (!hubId) {
      nextErrors.hubId =
        role === "DISPATCHER"
          ? "Please select an assigned hub for this dispatcher."
          : "Please select an assigned hub for this courier.";
    } else if (selectedHub && !selectedHub.active) {
      nextErrors.hubId =
        role === "DISPATCHER"
          ? "Cannot assign a dispatcher to an inactive hub."
          : "Cannot assign a courier to an inactive hub.";
    } else if (role === "DISPATCHER" && assignedDispatcherHubIds.has(hubId)) {
      nextErrors.hubId = "This hub already has an assigned dispatcher.";
    }

    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      return;
    }

    setIsSubmitting(true);

    const payload: CreateStaffUserRequest = {
      fullName: trimmedName,
      email: trimmedEmail,
      password,
      role,
      ...(hubId ? { hubId } : {}),
    };

    try {
      const createdUser = await api.post<UserResponse>("/api/v1/staff-users", payload);
      const params = new URLSearchParams({
        onboarded: createdUser.fullName,
        role: createdUser.roles.join(", "),
      });
      router.push(`/admin/users?${params.toString()}`);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setGeneralError(
            err.problem?.detail ||
              "A conflict occurred (email already exists or this hub already has a dispatcher)."
          );
        } else if (err.status === 400 && err.problem?.invalidFields) {
          const fieldMap: Record<string, string> = {};
          err.problem.invalidFields.forEach((f) => {
            fieldMap[f] = `Invalid ${f}. Please check the value.`;
          });
          setErrors(fieldMap);
          setGeneralError(err.problem.detail || "Validation failed for the submitted fields.");
        } else {
          setGeneralError(formatApiError(err));
        }
      } else {
        setGeneralError("A network error occurred. Please try again.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div className="space-y-1">
          <Link
            href="/admin/users"
            className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="size-3.5" />
            Back to User Management
          </Link>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Onboard Staff Member</h1>
          <p className="text-sm text-muted-foreground">
            Create credentials for couriers or hub dispatchers.
          </p>
        </div>
      </div>

      <Card className="rounded-xl border-border bg-card shadow-xs">
        <CardHeader className="border-b border-border/70 pb-4">
          <CardTitle className="text-base font-bold text-foreground">Staff Account Details</CardTitle>
          <CardDescription className="text-xs text-muted-foreground">
            Each hub can have one assigned dispatcher.
          </CardDescription>
        </CardHeader>

        <form onSubmit={handleSubmit} noValidate>
          <CardContent className="p-6 space-y-5">
            {generalError && (
              <div
                role="alert"
                aria-live="assertive"
                className="p-3 rounded-lg border border-destructive/20 bg-destructive/10 text-destructive text-xs font-medium flex items-start gap-2"
              >
                <AlertCircle className="size-4 shrink-0 mt-0.5" />
                <span>{generalError}</span>
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label htmlFor="staff-fullname">Full Name</Label>
                <Input
                  id="staff-fullname"
                  type="text"
                  placeholder="e.g. Ruwan Wickramasinghe"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  disabled={isSubmitting}
                  required
                  aria-required="true"
                  aria-invalid={Boolean(errors.fullName)}
                  aria-describedby={errors.fullName ? "staff-fullname-error" : undefined}
                  className={errors.fullName ? "border-destructive focus-visible:ring-destructive" : ""}
                />
                {errors.fullName && (
                  <p id="staff-fullname-error" className="text-[11px] text-destructive font-medium" role="alert">
                    {errors.fullName}
                  </p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="staff-email">Email Address</Label>
                <Input
                  id="staff-email"
                  type="email"
                  placeholder="e.g. ruwan@swiftdrop.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  disabled={isSubmitting}
                  required
                  aria-required="true"
                  aria-invalid={Boolean(errors.email)}
                  aria-describedby={errors.email ? "staff-email-error" : undefined}
                  className={errors.email ? "border-destructive focus-visible:ring-destructive" : ""}
                />
                {errors.email && (
                  <p id="staff-email-error" className="text-[11px] text-destructive font-medium" role="alert">
                    {errors.email}
                  </p>
                )}
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="staff-password">Password</Label>
              <div className="relative">
                <Input
                  id="staff-password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Minimum 8 characters"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={isSubmitting}
                  required
                  aria-required="true"
                  aria-invalid={Boolean(errors.password)}
                  aria-describedby={errors.password ? "staff-password-error" : "staff-password-hint"}
                  className={`pr-10 ${errors.password ? "border-destructive focus-visible:ring-destructive" : ""}`}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  disabled={isSubmitting}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
              {errors.password ? (
                <p id="staff-password-error" className="text-[11px] text-destructive font-medium" role="alert">
                  {errors.password}
                </p>
              ) : (
                <p id="staff-password-hint" className="text-[11px] text-muted-foreground">
                  Must be between 8 and 72 bytes.
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Label id="staff-role-label">Assigned Staff Role</Label>
              <div
                role="radiogroup"
                aria-labelledby="staff-role-label"
                aria-describedby={errors.role ? "staff-role-error" : undefined}
                className="grid grid-cols-1 sm:grid-cols-2 gap-3"
              >
                {STAFF_ROLES.map((r) => {
                  const isSelected = role === r.value;
                  const Icon = r.icon;
                  return (
                    <label
                      key={r.value}
                      htmlFor={`staff-role-${r.value}`}
                      className={`relative cursor-pointer rounded-xl border p-3.5 transition-all text-left flex flex-col justify-between has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-primary has-[:focus-visible]:ring-offset-2 ${
                        isSelected
                          ? "border-primary bg-primary/5 ring-1 ring-primary shadow-xs"
                          : "border-border bg-card hover:bg-muted/30"
                      } ${isSubmitting ? "opacity-50 pointer-events-none" : ""}`}
                    >
                      <input
                        type="radio"
                        id={`staff-role-${r.value}`}
                        name="staff-role"
                        value={r.value}
                        checked={isSelected}
                        onChange={() => {
                          if (!isSubmitting) {
                            setRole(r.value);
                            if (r.value === "DISPATCHER" && hubId && assignedDispatcherHubIds.has(hubId)) {
                              setHubId("");
                            }
                            setErrors((prev) => {
                              if (!prev.hubId) return prev;
                              const next = { ...prev };
                              delete next.hubId;
                              return next;
                            });
                          }
                        }}
                        disabled={isSubmitting}
                        className="sr-only"
                      />
                      <div className="flex items-center justify-between mb-2">
                        <span
                          className={`p-1.5 rounded-lg ${
                            isSelected ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"
                          }`}
                        >
                          <Icon className="size-4" />
                        </span>
                        <span
                          aria-hidden="true"
                          className={`size-4 rounded-full border flex items-center justify-center ${
                            isSelected ? "border-primary bg-primary text-white" : "border-muted-foreground/40"
                          }`}
                        >
                          {isSelected && <span className="size-1.5 rounded-full bg-white" />}
                        </span>
                      </div>
                      <span className="font-semibold text-xs text-foreground block">{r.label}</span>
                      <span className="text-[11px] text-muted-foreground mt-1 leading-relaxed">
                        {r.description}
                      </span>
                    </label>
                  );
                })}
              </div>
              {errors.role && (
                <p id="staff-role-error" className="text-[11px] text-destructive font-medium" role="alert">
                  {errors.role}
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between gap-2">
                <Label htmlFor="staff-hub">Assigned Hub</Label>
                {!hubsLoading && activeHubs.length > 0 && (
                  <span className="text-[11px] text-muted-foreground">
                    {isDispatcher
                      ? `${assignedActiveHubCount} / ${activeHubs.length} hubs assigned`
                      : `${activeHubs.length} active hubs available`}
                  </span>
                )}
              </div>
              {hubsError ? (
                <p className="text-[11px] text-destructive font-medium" role="alert">
                  Failed to load hubs: {hubsError}
                </p>
              ) : (
                <ThemedSelect
                  id="staff-hub"
                  value={hubId}
                  onChange={(val) => setHubId(val)}
                  options={hubSelectOptions}
                  placeholder={
                    hubsLoading
                      ? "Loading hubs..."
                      : isDispatcher
                        ? "Select a hub for this dispatcher"
                        : "Select a hub for this courier"
                  }
                  disabled={isSubmitting || hubsLoading}
                  searchable={hubs.length > 6}
                  searchPlaceholder="Search hubs by name, city, or code..."
                  placement="top"
                  className={errors.hubId ? "border-destructive focus-visible:ring-destructive" : ""}
                />
              )}
              {errors.hubId ? (
                <p id="staff-hub-error" className="text-[11px] text-destructive font-medium" role="alert">
                  {errors.hubId}
                </p>
              ) : !hubsLoading && !hubsError && activeHubs.length === 0 ? (
                <p id="staff-hub-hint" className="text-[11px] text-amber-600 dark:text-amber-400 font-medium">
                  No active hubs available. Activate or create hubs in Admin &rarr; Hubs (/admin/hubs) first.
                </p>
              ) : (
                <p id="staff-hub-hint" className="text-[11px] text-muted-foreground">
                  {isDispatcher
                    ? "Each active hub can have one dispatcher. Inactive or already-assigned hubs are disabled."
                    : "Each courier belongs to one active hub. Multiple couriers can be assigned to the same hub."}
                </p>
              )}
            </div>
          </CardContent>

          <div className="flex items-center justify-end gap-2 px-6 py-4 border-t border-border bg-muted/20 rounded-b-xl">
            <Link
              href="/admin/users"
              className={buttonVariants({ variant: "outline", size: "sm", className: "text-xs" })}
            >
              Cancel
            </Link>
            <Button
              type="submit"
              size="sm"
              disabled={isSubmitting}
              className="text-xs font-semibold shadow-xs min-w-28"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="size-3.5 animate-spin mr-1.5" />
                  Creating...
                </>
              ) : (
                "Create Staff"
              )}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}

