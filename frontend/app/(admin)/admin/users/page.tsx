"use client";

import React, { useState, useEffect, useRef } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import {
  api,
  ApiError,
  formatApiError,
  type UserResponse,
  type PageResponse,
  type UpdateUserStatusRequest,
  type HubResponse,
} from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Plus,
  RefreshCw,
  Filter,
  ChevronLeft,
  ChevronRight,
  AlertCircle,
  CheckCircle2,
  X,
  Mail,
  User,
  Shield,
  Radio,
  Truck,
  Store,
  UserCheck,
  UserX,
  Loader2,
  Search,
  Warehouse,
} from "lucide-react";

const PAGE_SIZE = 10;
const ROLE_FILTERS = ["ALL", "CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER", "ADMIN"] as const;
type RoleFilter = (typeof ROLE_FILTERS)[number];

interface AccessibleModalProps {
  isOpen: boolean;
  onClose: () => void;
  titleId: string;
  descriptionId?: string;
  isSubmitting?: boolean;
  triggerRef?: React.RefObject<HTMLElement | null>;
  children: React.ReactNode;
  maxWidthClass?: string;
}

/**
 * Accessible modal wrapper providing focus trapping, Escape key support,
 * initial focus dispatch, and focus restoration to the triggering element.
 */
function AccessibleModal({
  isOpen,
  onClose,
  titleId,
  descriptionId,
  isSubmitting = false,
  triggerRef,
  children,
  maxWidthClass = "max-w-lg",
}: AccessibleModalProps) {
  const modalContainerRef = useRef<HTMLDivElement | null>(null);
  const previousActiveElementRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!isOpen) return;

    previousActiveElementRef.current = document.activeElement as HTMLElement | null;
    const triggerElement = triggerRef?.current;
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    // Dispatch initial focus into first interactive element
    const timer = setTimeout(() => {
      if (!modalContainerRef.current) return;
      const focusable = modalContainerRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      );
      if (focusable.length > 0) {
        focusable[0].focus();
      } else {
        modalContainerRef.current.focus();
      }
    }, 50);

    return () => {
      clearTimeout(timer);
      document.body.style.overflow = originalOverflow;
      const elementToFocus = triggerElement ?? previousActiveElementRef.current;
      elementToFocus?.focus?.();
    };
  }, [isOpen, triggerRef]);

  if (!isOpen) return null;

  function handleKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
    if (e.key === "Escape") {
      if (!isSubmitting) {
        e.preventDefault();
        onClose();
      }
      return;
    }

    if (e.key === "Tab") {
      if (!modalContainerRef.current) return;
      const focusable = modalContainerRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      );
      if (focusable.length === 0) {
        e.preventDefault();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];

      if (e.shiftKey) {
        if (document.activeElement === first || document.activeElement === modalContainerRef.current) {
          e.preventDefault();
          last.focus();
        }
      } else {
        if (document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
  }

  function handleBackdropClick(e: React.MouseEvent<HTMLDivElement>) {
    if (e.target === e.currentTarget && !isSubmitting) {
      onClose();
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      tabIndex={-1}
      onKeyDown={handleKeyDown}
      onClick={handleBackdropClick}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-background/80 backdrop-blur-xs animate-in fade-in-0"
    >
      <div
        ref={modalContainerRef}
        className={`w-full ${maxWidthClass} bg-card border border-border rounded-xl shadow-lg overflow-hidden animate-in zoom-in-95`}
      >
        {children}
      </div>
    </div>
  );
}

export default function AdminUsersPage() {
  const { user: currentAdmin } = useAuth();
  const currentAdminId = currentAdmin?.id;

  // Directory State
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [roleFilter, setRoleFilter] = useState<RoleFilter>("ALL");
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  // Trigger ref for focus restoration
  const statusTriggerRef = useRef<HTMLElement | null>(null);

  // Notifications
  const [notification, setNotification] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);

  // Stale request race-condition protection
  const fetchIdRef = useRef(0);

  // Load Users Effect (asynchronous state resolution prevents cascading renders)
  useEffect(() => {
    let ignore = false;
    const currentFetchId = ++fetchIdRef.current;

    async function fetchUsers() {
      try {
        const params = new URLSearchParams();
        params.set("page", String(page));
        params.set("size", String(PAGE_SIZE));
        if (roleFilter && roleFilter !== "ALL") {
          params.set("role", roleFilter);
        }

        const response = await api.get<PageResponse<UserResponse>>(`/api/v1/users?${params.toString()}`);

        if (!ignore && currentFetchId === fetchIdRef.current) {
          setUsers(response.items || []);
          setPage(response.page);
          setTotalItems(response.totalItems);
          setTotalPages(response.totalPages);
          setFetchError(null);
          setIsLoading(false);
        } else if (!ignore) {
          setIsLoading(false);
        }
      } catch (err: unknown) {
        if (!ignore && currentFetchId === fetchIdRef.current) {
          setFetchError(formatApiError(err));
          setIsLoading(false);
        }
      }
    }

    fetchUsers();

    return () => {
      ignore = true;
    };
  }, [page, roleFilter, refreshKey]);

  function handleFilterChange(newRole: RoleFilter) {
    if (newRole === roleFilter) return;
    setIsLoading(true);
    setFetchError(null);
    setRoleFilter(newRole);
    setPage(0);
  }

  function handleRefresh() {
    setIsLoading(true);
    setFetchError(null);
    setRefreshKey((k) => k + 1);
  }

  function handlePreviousPage() {
    if (page <= 0 || isLoading) return;
    setIsLoading(true);
    setFetchError(null);
    setPage((p) => Math.max(0, p - 1));
  }

  function handleNextPage() {
    if (page >= totalPages - 1 || isLoading) return;
    setIsLoading(true);
    setFetchError(null);
    setPage((p) => p + 1);
  }

  const [hubs, setHubs] = useState<HubResponse[]>([]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const onboarded = params.get("onboarded");
    const role = params.get("role");
    if (!onboarded) return;

    let cancelled = false;
    window.history.replaceState({}, "", "/admin/users");
    queueMicrotask(() => {
      if (!cancelled) {
        setNotification({
          type: "success",
          message: `Staff account for ${onboarded} was successfully created${role ? ` as ${role}` : ""}.`,
        });
      }
    });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let ignore = false;
    async function loadHubs() {
      try {
        const hubList = await api.get<HubResponse[]>("/api/v1/hubs");
        if (!ignore) setHubs(hubList);
      } catch {
        // Hub names are supplementary in the directory table
      }
    }
    loadHubs();
    return () => {
      ignore = true;
    };
  }, [refreshKey]);

  // Account Status Toggle State & Modal
  const [statusConfirmTarget, setStatusConfirmTarget] = useState<{
    user: UserResponse;
    newEnabled: boolean;
  } | null>(null);
  const [isStatusSubmitting, setIsStatusSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  function handleOpenStatusModal(
    targetUser: UserResponse,
    newEnabled: boolean,
    triggerElement?: HTMLElement | null
  ) {
    if (targetUser.id === currentAdminId && !newEnabled) {
      setNotification({
        type: "error",
        message: "You cannot disable your own administrator account.",
      });
      return;
    }
    statusTriggerRef.current = triggerElement ?? null;
    setActionError(null);
    setStatusConfirmTarget({ user: targetUser, newEnabled });
  }

  function handleCloseStatusModal() {
    if (isStatusSubmitting) return;
    setStatusConfirmTarget(null);
    setActionError(null);
  }

  async function handleConfirmStatusChange() {
    if (!statusConfirmTarget || isStatusSubmitting) return;

    const { user: targetUser, newEnabled } = statusConfirmTarget;

    // Self-disable guard
    if (targetUser.id === currentAdminId && !newEnabled) {
      setActionError("An administrator cannot disable their own account.");
      return;
    }

    setIsStatusSubmitting(true);
    setActionError(null);

    const requestBody: UpdateUserStatusRequest = {
      enabled: newEnabled,
    };

    try {
      const updatedUser = await api.patch<UserResponse>(
        `/api/v1/users/${targetUser.id}`,
        requestBody
      );

      // Invalidate any in-flight GET requests that started before this PATCH finished
      fetchIdRef.current++;

      // Update in-place in local users state
      setUsers((prev) => prev.map((u) => (u.id === updatedUser.id ? updatedUser : u)));

      setNotification({
        type: "success",
        message: `Account for ${targetUser.fullName} (${targetUser.email}) was successfully ${
          newEnabled ? "enabled" : "disabled"
        }.`,
      });

      handleCloseStatusModal();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setActionError("An administrator cannot disable their own account.");
        } else if (err.status === 404) {
          setActionError("User was not found. The account may have been removed.");
        } else {
          setActionError(formatApiError(err));
        }
      } else {
        setActionError("An unexpected error occurred while updating account status.");
      }
    } finally {
      setIsStatusSubmitting(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* Top Header Card */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-card border border-border p-6 rounded-xl shadow-xs">
        <div>
          <div className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-primary mb-1">
            <span className="size-2 rounded-full bg-primary" />
            Admin Operations
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">User & Staff Management</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Onboard logistics couriers, manage dispatch staff, and administer user access privileges.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={isLoading}
            className="gap-1.5 text-xs font-medium"
            title="Refresh user list"
          >
            <RefreshCw className={`size-3.5 ${isLoading ? "animate-spin" : ""}`} />
            Refresh
          </Button>
          <Link
            href="/admin/users/new"
            className={buttonVariants({ size: "sm", className: "gap-1.5 font-semibold text-xs shadow-xs" })}
          >
            <Plus className="size-4" />
            Onboard Staff
          </Link>
        </div>
      </div>

      {/* Global Notifications */}
      {notification && (
        <div
          role={notification.type === "error" ? "alert" : "status"}
          aria-live="polite"
          className={`flex items-start justify-between gap-3 p-4 rounded-xl border text-sm font-medium ${
            notification.type === "success"
              ? "bg-emerald-500/10 border-emerald-500/20 text-emerald-800 dark:text-emerald-300"
              : "bg-destructive/10 border-destructive/20 text-destructive"
          }`}
        >
          <div className="flex items-center gap-2">
            {notification.type === "success" ? (
              <CheckCircle2 className="size-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
            ) : (
              <AlertCircle className="size-4 shrink-0 text-destructive" />
            )}
            <span>{notification.message}</span>
          </div>
          <button
            type="button"
            onClick={() => setNotification(null)}
            className="text-muted-foreground hover:text-foreground rounded p-0.5"
            aria-label="Dismiss notification"
          >
            <X className="size-4" />
          </button>
        </div>
      )}

      {/* Directory Table Card */}
      <Card className="rounded-xl border-border bg-card shadow-xs overflow-hidden">
        <CardHeader className="border-b border-border/70 pb-4">
          <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
            <div>
              <CardTitle className="text-lg font-bold text-foreground flex items-center gap-2">
                User Directory
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-muted text-muted-foreground border border-border">
                  {totalItems} total
                </span>
              </CardTitle>
              <CardDescription className="text-xs text-muted-foreground mt-0.5">
                Browse customer, merchant, and staff profiles.
              </CardDescription>
            </div>

            {/* Filter Pills */}
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 md:pb-0 text-xs">
              <span className="text-muted-foreground font-medium flex items-center gap-1 mr-1 shrink-0">
                <Filter className="size-3.5" />
                Filter:
              </span>
              {ROLE_FILTERS.map((r) => {
                const isActive = roleFilter === r;
                return (
                  <button
                    key={r}
                    type="button"
                    onClick={() => handleFilterChange(r)}
                    aria-pressed={isActive}
                    className={`px-2.5 py-1 rounded-lg font-medium transition-colors shrink-0 ${
                      isActive
                        ? "bg-primary text-primary-foreground shadow-xs font-semibold"
                        : "bg-muted text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                    }`}
                  >
                    {r === "ALL" ? "All Users" : r.replace("_", " ")}
                  </button>
                );
              })}
            </div>
          </div>
        </CardHeader>

        <CardContent className="p-0">
          {/* Error Banner */}
          {fetchError && !isLoading && (
            <div className="p-6 text-center" role="alert" aria-live="assertive">
              <div className="inline-flex size-10 items-center justify-center rounded-full bg-destructive/10 text-destructive mb-3">
                <AlertCircle className="size-5" />
              </div>
              <h3 className="text-sm font-semibold text-foreground">Failed to load users</h3>
              <p className="text-xs text-muted-foreground max-w-md mx-auto mt-1 mb-4">{fetchError}</p>
              <Button size="sm" variant="outline" onClick={handleRefresh} className="text-xs">
                Try again
              </Button>
            </div>
          )}

          {/* Loading Skeleton */}
          {isLoading && (
            <div className="divide-y divide-border/60 p-4 space-y-3" role="status" aria-live="polite">
              <span className="sr-only">Loading user directory...</span>
              {[...Array(5)].map((_, i) => (
                <div key={i} className="flex items-center justify-between py-3 animate-pulse">
                  <div className="flex items-center gap-3">
                    <div className="size-9 rounded-full bg-muted" />
                    <div className="space-y-1.5">
                      <div className="h-3.5 w-32 bg-muted rounded" />
                      <div className="h-2.5 w-48 bg-muted rounded" />
                    </div>
                  </div>
                  <div className="flex items-center gap-4">
                    <div className="h-5 w-20 bg-muted rounded-full" />
                    <div className="h-5 w-16 bg-muted rounded-full" />
                    <div className="h-8 w-20 bg-muted rounded" />
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Empty State */}
          {!isLoading && !fetchError && users.length === 0 && (
            <div className="py-12 px-4 text-center">
              <div className="inline-flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground mb-3">
                <Search className="size-6" />
              </div>
              <h3 className="text-base font-semibold text-foreground">No accounts found</h3>
              <p className="text-xs text-muted-foreground max-w-sm mx-auto mt-1 mb-4">
                {roleFilter !== "ALL"
                  ? `There are currently no accounts with the role "${roleFilter.replace("_", " ")}".`
                  : "No registered accounts exist in the directory."}
              </p>
              {roleFilter !== "ALL" && (
                <Button size="sm" variant="outline" onClick={() => handleFilterChange("ALL")} className="text-xs">
                  View all accounts
                </Button>
              )}
            </div>
          )}

          {/* Users Table */}
          {!isLoading && !fetchError && users.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-sm">
                <caption className="sr-only">
                  SwiftDrop user directory listing registered account holders, assigned roles, and current authorization status
                </caption>
                <thead>
                  <tr className="border-b border-border bg-muted/40 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    <th scope="col" className="py-3 px-4">
                      User Details
                    </th>
                    <th scope="col" className="py-3 px-4">
                      Roles
                    </th>
                    <th scope="col" className="py-3 px-4">
                      Account Status
                    </th>
                    <th scope="col" className="py-3 px-4 text-right">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {users.map((item) => {
                    const isSelf = item.id === currentAdminId;
                    const isEnabled = item.enabled;

                    return (
                      <tr
                        key={item.id}
                        className={`transition-colors hover:bg-muted/30 ${
                          isSelf ? "bg-primary/5" : ""
                        }`}
                      >
                        {/* User Column */}
                        <td className="py-3.5 px-4">
                          <div className="flex items-center gap-3">
                            <div className="size-9 rounded-full bg-accent text-accent-foreground font-semibold text-xs flex items-center justify-center shrink-0 border border-primary/20">
                              {item.fullName
                                ? item.fullName
                                    .split(" ")
                                    .map((n) => n[0])
                                    .join("")
                                    .substring(0, 2)
                                    .toUpperCase()
                                : "U"}
                            </div>
                            <div className="min-w-0">
                              <div className="flex items-center gap-2 font-medium text-foreground">
                                <span className="truncate">{item.fullName}</span>
                                {isSelf && (
                                  <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-primary text-primary-foreground">
                                    You
                                  </span>
                                )}
                              </div>
                              <div className="text-xs text-muted-foreground truncate flex items-center gap-1 mt-0.5">
                                <Mail className="size-3 text-muted-foreground shrink-0" />
                                {item.email}
                              </div>
                            </div>
                          </div>
                        </td>

                        {/* Roles Column */}
                        <td className="py-3.5 px-4">
                          <div className="flex flex-wrap gap-1.5">
                            {item.roles && item.roles.length > 0 ? (
                              item.roles.map((role) => {
                                let badgeStyle =
                                  "bg-muted text-muted-foreground border-border";
                                let Icon = User;

                                if (role === "ADMIN") {
                                  badgeStyle =
                                    "bg-primary/10 text-primary border-primary/20";
                                  Icon = Shield;
                                } else if (role === "DISPATCHER") {
                                  badgeStyle =
                                    "bg-purple-500/10 text-purple-700 dark:text-purple-400 border-purple-500/20";
                                  Icon = Radio;
                                } else if (role === "COURIER") {
                                  badgeStyle =
                                    "bg-blue-500/10 text-blue-700 dark:text-blue-400 border-blue-500/20";
                                  Icon = Truck;
                                } else if (role === "MERCHANT") {
                                  badgeStyle =
                                    "bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/20";
                                  Icon = Store;
                                }

                                return (
                                  <span
                                    key={role}
                                    className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold border ${badgeStyle}`}
                                  >
                                    <Icon className="size-3 shrink-0" />
                                    {role.replace("_", " ")}
                                  </span>
                                );
                              })
                            ) : (
                              <span className="text-xs text-muted-foreground">No roles assigned</span>
                            )}
                            {item.hubId && (() => {
                              const hub = hubs.find((h) => h.id === item.hubId);
                              return (
                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-muted/60 text-muted-foreground border border-border">
                                  <Warehouse className="size-3 shrink-0" />
                                  {hub ? `${hub.name} (${hub.code})` : "Hub assigned"}
                                </span>
                              );
                            })()}
                          </div>
                        </td>

                        {/* Status Column */}
                        <td className="py-3.5 px-4">
                          {isEnabled ? (
                            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-500/20">
                              <span className="size-1.5 rounded-full bg-emerald-500" />
                              Active
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-zinc-500/10 text-zinc-600 dark:text-zinc-400 border border-zinc-500/20">
                              <span className="size-1.5 rounded-full bg-zinc-400" />
                              Disabled
                            </span>
                          )}
                        </td>

                        {/* Actions Column */}
                        <td className="py-3.5 px-4 text-right">
                          {isSelf ? (
                            <span
                              className="text-xs font-medium text-muted-foreground px-2 py-1 italic"
                              title="You cannot disable your own administrator account."
                            >
                              Current Account
                            </span>
                          ) : isEnabled ? (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={(e) => handleOpenStatusModal(item, false, e.currentTarget)}
                              className="text-xs text-destructive hover:bg-destructive/10 hover:text-destructive border-destructive/20 font-medium"
                            >
                              <UserX className="size-3.5 mr-1" />
                              Disable
                            </Button>
                          ) : (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={(e) => handleOpenStatusModal(item, true, e.currentTarget)}
                              className="text-xs text-emerald-700 dark:text-emerald-400 hover:bg-emerald-500/10 border-emerald-500/20 font-medium"
                            >
                              <UserCheck className="size-3.5 mr-1" />
                              Enable
                            </Button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination Bar */}
          {!isLoading && !fetchError && totalItems > 0 && (
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 px-4 py-3.5 border-t border-border bg-muted/20 text-xs text-muted-foreground">
              <div>
                Showing <span className="font-semibold text-foreground">{page * PAGE_SIZE + 1}</span> to{" "}
                <span className="font-semibold text-foreground">
                  {Math.min((page + 1) * PAGE_SIZE, totalItems)}
                </span>{" "}
                of <span className="font-semibold text-foreground">{totalItems}</span> users
              </div>
              <div className="flex items-center gap-2">
                <span className="mr-2">
                  Page <span className="font-semibold text-foreground">{page + 1}</span> of{" "}
                  <span className="font-semibold text-foreground">{totalPages || 1}</span>
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handlePreviousPage}
                  disabled={page === 0 || isLoading}
                  className="h-8 px-2.5 text-xs"
                  aria-label="Previous page"
                >
                  <ChevronLeft className="size-3.5 mr-1" />
                  Prev
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleNextPage}
                  disabled={page >= totalPages - 1 || isLoading}
                  className="h-8 px-2.5 text-xs"
                  aria-label="Next page"
                >
                  Next
                  <ChevronRight className="size-3.5 ml-1" />
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Account Status Confirmation Modal */}
      {statusConfirmTarget && (
        <AccessibleModal
          isOpen={Boolean(statusConfirmTarget)}
          onClose={handleCloseStatusModal}
          titleId="status-confirm-modal-title"
          descriptionId="status-confirm-modal-desc"
          isSubmitting={isStatusSubmitting}
          triggerRef={statusTriggerRef}
          maxWidthClass="max-w-md"
        >
          <div className="p-5">
            <div
              className={`inline-flex size-11 items-center justify-center rounded-full mb-3 ${
                statusConfirmTarget.newEnabled
                  ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                  : "bg-destructive/10 text-destructive"
              }`}
            >
              {statusConfirmTarget.newEnabled ? (
                <UserCheck className="size-6" />
              ) : (
                <UserX className="size-6" />
              )}
            </div>

            <h2 id="status-confirm-modal-title" className="text-lg font-bold text-foreground">
              {statusConfirmTarget.newEnabled ? "Enable User Account" : "Disable User Account"}
            </h2>

            <p id="status-confirm-modal-desc" className="text-xs text-muted-foreground mt-1.5 leading-relaxed">
              {statusConfirmTarget.newEnabled ? (
                <>
                  Are you sure you want to enable access for{" "}
                  <strong className="text-foreground">{statusConfirmTarget.user.fullName}</strong> (
                  {statusConfirmTarget.user.email})? This user will be allowed to log in and use
                  SwiftDrop services.
                </>
              ) : (
                <>
                  Are you sure you want to disable access for{" "}
                  <strong className="text-foreground">{statusConfirmTarget.user.fullName}</strong> (
                  {statusConfirmTarget.user.email})? This user will be blocked from authenticating or
                  initiating transactions.
                </>
              )}
            </p>

            {actionError && (
              <div
                role="alert"
                aria-live="assertive"
                className="mt-3 p-3 rounded-lg border border-destructive/20 bg-destructive/10 text-destructive text-xs font-medium flex items-start gap-2"
              >
                <AlertCircle className="size-4 shrink-0 mt-0.5" />
                <span>{actionError}</span>
              </div>
            )}
          </div>

          <div className="flex items-center justify-end gap-2 p-4 border-t border-border bg-muted/20">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleCloseStatusModal}
              disabled={isStatusSubmitting}
              className="text-xs"
            >
              Cancel
            </Button>
            <Button
              type="button"
              size="sm"
              variant={statusConfirmTarget.newEnabled ? "default" : "destructive"}
              onClick={handleConfirmStatusChange}
              disabled={isStatusSubmitting}
              className="text-xs font-semibold shadow-xs min-w-28"
            >
              {isStatusSubmitting ? (
                <>
                  <Loader2 className="size-3.5 animate-spin mr-1.5" />
                  Updating...
                </>
              ) : statusConfirmTarget.newEnabled ? (
                "Confirm Enable"
              ) : (
                "Confirm Disable"
              )}
            </Button>
          </div>
        </AccessibleModal>
      )}
    </div>
  );
}
