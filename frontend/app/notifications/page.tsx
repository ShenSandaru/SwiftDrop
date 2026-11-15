"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import {
  Bell,
  CheckCheck,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Package,
  UserCheck,
  AlertCircle,
  RefreshCw,
  Filter,
} from "lucide-react";
import { cn } from "cn";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useAuth } from "@/lib/auth-context";
import { useNotifications } from "@/lib/notification-context";
import {
  getNotifications,
  type NotificationResponse,
  type PageResponse,
} from "@/lib/notifications";
import { formatApiError } from "@/lib/api";

const PAGE_SIZE = 10;

function formatNotificationTime(dateString: string): { relative: string; absolute: string } {
  const date = new Date(dateString);
  const now = new Date();
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

  let relative = "just now";
  if (diffInSeconds >= 60) {
    const diffInMinutes = Math.floor(diffInSeconds / 60);
    if (diffInMinutes < 60) {
      relative = `${diffInMinutes}m ago`;
    } else {
      const diffInHours = Math.floor(diffInMinutes / 60);
      if (diffInHours < 24) {
        relative = `${diffInHours}h ago`;
      } else {
        const diffInDays = Math.floor(diffInHours / 24);
        if (diffInDays < 7) {
          relative = `${diffInDays}d ago`;
        } else {
          relative = date.toLocaleDateString(undefined, {
            month: "short",
            day: "numeric",
          });
        }
      }
    }
  }

  const absolute = date.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });

  return { relative, absolute };
}

export default function NotificationsPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: isAuthLoading, hasRole } = useAuth();
  const { markAsRead, markAllAsRead, refresh: refreshGlobalContext } = useNotifications();

  const [activeTab, setActiveTab] = useState<"all" | "unread">("all");
  const [currentPage, setCurrentPage] = useState(0);
  const [pageData, setPageData] = useState<PageResponse<NotificationResponse> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isMarkingAll, setIsMarkingAll] = useState(false);
  const [markingIds, setMarkingIds] = useState<Set<string>>(new Set());

  // Protect route: redirect unauthenticated users to /login
  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.replace(`/login?redirect=${encodeURIComponent("/notifications")}`);
    }
  }, [isAuthLoading, isAuthenticated, router]);

  const fetchPage = useCallback(
    async (page: number, unreadOnly: boolean) => {
      setIsLoading(true);
      setError(null);
      try {
        const response = await getNotifications({
          page,
          size: PAGE_SIZE,
          unreadOnly: unreadOnly ? true : undefined,
        });
        setPageData(response);
      } catch (err) {
        setError(formatApiError(err));
      } finally {
        setIsLoading(false);
      }
    },
    [],
  );

  // Load feed when authenticated, activeTab, or currentPage changes
  useEffect(() => {
    if (!isAuthenticated || isAuthLoading) return;
    const isUnread = activeTab === "unread";
    let cancelled = false;

    queueMicrotask(() => {
      if (!cancelled) {
        void fetchPage(currentPage, isUnread);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, isAuthLoading, activeTab, currentPage, fetchPage]);

  // Tab switch handler: resets to page 0
  const handleTabChange = (tab: "all" | "unread") => {
    if (tab === activeTab) return;
    setActiveTab(tab);
    setCurrentPage(0);
  };

  // Mark all notifications as read
  const handleMarkAllAsRead = async () => {
    if (isMarkingAll) return;
    setIsMarkingAll(true);
    try {
      await markAllAsRead();
      // Optimistically update current page view
      setPageData((prev: PageResponse<NotificationResponse> | null) => {
        if (!prev) return prev;
        const updatedItems = prev.items.map((item: NotificationResponse) => ({
          ...item,
          isRead: true,
          readAt: item.readAt || new Date().toISOString(),
        }));
        return {
          ...prev,
          items: activeTab === "unread" ? [] : updatedItems,
          totalItems: activeTab === "unread" ? 0 : prev.totalItems,
          totalPages: activeTab === "unread" ? 0 : prev.totalPages,
        };
      });
      // Background re-fetch to ensure exact server alignment
      void fetchPage(currentPage, activeTab === "unread");
    } catch (err) {
      setError(formatApiError(err));
    } finally {
      setIsMarkingAll(false);
    }
  };

  // Mark single notification as read
  const handleMarkOneAsRead = async (item: NotificationResponse, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    if (item.isRead || markingIds.has(item.id)) return;

    setMarkingIds((prev) => new Set(prev).add(item.id));
    // Optimistic UI update
    setPageData((prev: PageResponse<NotificationResponse> | null) => {
      if (!prev) return prev;
      return {
        ...prev,
        items: prev.items.map((it: NotificationResponse) =>
          it.id === item.id
            ? { ...it, isRead: true, readAt: new Date().toISOString() }
            : it,
        ),
      };
    });

    try {
      await markAsRead(item.id);
      if (activeTab === "unread") {
        // In unread tab, re-fetch to update count & pagination cleanly
        void fetchPage(currentPage, true);
      }
    } catch (err) {
      // Revert optimistic update on failure
      setPageData((prev: PageResponse<NotificationResponse> | null) => {
        if (!prev) return prev;
        return {
          ...prev,
          items: prev.items.map((it: NotificationResponse) =>
            it.id === item.id ? { ...it, isRead: false, readAt: null } : it,
          ),
        };
      });
      setError(formatApiError(err));
    } finally {
      setMarkingIds((prev) => {
        const next = new Set(prev);
        next.delete(item.id);
        return next;
      });
    }
  };

  // Handle notification item click: mark read and navigate
  const handleItemClick = (item: NotificationResponse) => {
    if (!item.isRead) {
      void handleMarkOneAsRead(item);
    }

    // Role-aware navigation matching notification bell logic
    if (item.parcelId) {
      if (hasRole("CUSTOMER")) {
        router.push(`/customer/parcels/${item.parcelId}`);
      } else if (hasRole("COURIER")) {
        router.push(`/courier/parcels/${item.parcelId}`);
      } else if (hasRole("ADMIN") || hasRole("DISPATCHER")) {
        router.push("/dispatch");
      } else if (item.trackingNumber) {
        router.push(`/track?number=${encodeURIComponent(item.trackingNumber)}`);
      }
    } else {
      if (hasRole("MERCHANT")) {
        router.push("/merchant/status");
      } else if (hasRole("CUSTOMER")) {
        router.push("/customer/profile");
      }
    }
  };

  if (isAuthLoading) {
    return (
      <main className="container mx-auto flex min-h-[50vh] max-w-4xl items-center justify-center px-4 py-12">
        <div className="flex flex-col items-center gap-3 text-center" role="status" aria-live="polite">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-sm text-muted-foreground animate-pulse">Loading notifications...</p>
        </div>
      </main>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  const items = pageData?.items ?? [];
  const totalItems = pageData?.totalItems ?? 0;
  const totalPages = pageData?.totalPages ?? 0;
  const hasUnreadOnCurrentPage = items.some((item: NotificationResponse) => !item.isRead);

  return (
    <main className="container mx-auto max-w-4xl px-4 py-8 space-y-6">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Notifications
            </h1>
            {totalItems > 0 && (
              <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-semibold text-muted-foreground">
                {totalItems}
              </span>
            )}
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Stay informed about your parcel deliveries, pickups, and account updates.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              void fetchPage(currentPage, activeTab === "unread");
              void refreshGlobalContext();
            }}
            disabled={isLoading}
            className="gap-1.5 text-xs"
          >
            <RefreshCw className={cn("size-3.5", isLoading && "animate-spin")} />
            Refresh
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={handleMarkAllAsRead}
            disabled={isMarkingAll || isLoading || (!hasUnreadOnCurrentPage && activeTab === "unread")}
            className="gap-1.5 text-xs"
          >
            {isMarkingAll ? (
              <Loader2 className="size-3.5 animate-spin" />
            ) : (
              <CheckCheck className="size-3.5 text-primary" />
            )}
            Mark all as read
          </Button>
        </div>
      </div>

      {/* Tabs / Filter Controls */}
      <div className="flex items-center justify-between border-b border-border pb-3">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => handleTabChange("all")}
            className={cn(
              "px-3.5 py-1.5 text-xs font-medium rounded-lg transition-colors",
              activeTab === "all"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:bg-muted hover:text-foreground",
            )}
          >
            All Notifications
          </button>
          <button
            type="button"
            onClick={() => handleTabChange("unread")}
            className={cn(
              "px-3.5 py-1.5 text-xs font-medium rounded-lg transition-colors flex items-center gap-1.5",
              activeTab === "unread"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:bg-muted hover:text-foreground",
            )}
          >
            <Filter className="size-3" />
            Unread Only
          </button>
        </div>

        {totalPages > 0 && (
          <p className="text-xs text-muted-foreground">
            Page {currentPage + 1} of {totalPages}
          </p>
        )}
      </div>

      {/* Error Banner with Retry */}
      {error && (
        <div
          role="alert"
          className="flex items-center justify-between gap-3 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-xs text-destructive"
        >
          <div className="flex items-center gap-2">
            <AlertCircle className="size-4 shrink-0" />
            <span>{error}</span>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void fetchPage(currentPage, activeTab === "unread")}
            className="h-7 text-xs border-destructive/30 hover:bg-destructive/10"
          >
            Retry
          </Button>
        </div>
      )}

      {/* Content Area */}
      <Card className="border-border shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3 text-muted-foreground">
            <Loader2 className="size-8 animate-spin text-primary" />
            <p className="text-xs">Loading notifications...</p>
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-muted/60 text-muted-foreground mb-3">
              <Bell className="size-6" />
            </div>
            <h3 className="text-sm font-semibold text-foreground">
              {activeTab === "unread" ? "No unread notifications" : "No notifications yet"}
            </h3>
            <p className="text-xs text-muted-foreground mt-1 max-w-sm">
              {activeTab === "unread"
                ? "You have caught up with all your updates. Check back later or view all notifications."
                : "You do not have any notifications right now. System alerts and delivery tracking events will appear here."}
            </p>
            {activeTab === "unread" && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => handleTabChange("all")}
                className="mt-4 text-xs"
              >
                View all notifications
              </Button>
            )}
          </div>
        ) : (
          <div className="divide-y divide-border">
            {items.map((item: NotificationResponse) => {
              const { relative, absolute } = formatNotificationTime(item.createdAt);
              const isMarking = markingIds.has(item.id);

              return (
                <div
                  key={item.id}
                  onClick={() => handleItemClick(item)}
                  className={cn(
                    "group flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 transition-colors hover:bg-muted/50 cursor-pointer",
                    !item.isRead ? "bg-accent/40" : "bg-card opacity-90",
                  )}
                >
                  <div className="flex items-start gap-3.5 min-w-0 flex-1">
                    {/* Notification Icon */}
                    <div
                      className={cn(
                        "mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full",
                        !item.isRead
                          ? "bg-primary/15 text-primary"
                          : "bg-muted text-muted-foreground",
                      )}
                    >
                      {item.parcelId ? (
                        <Package className="size-4" aria-hidden="true" />
                      ) : (
                        <UserCheck className="size-4" aria-hidden="true" />
                      )}
                    </div>

                    {/* Notification Body */}
                    <div className="space-y-1 min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-semibold text-foreground leading-snug">
                          {item.title}
                        </span>
                        {!item.isRead && (
                          <span className="inline-flex items-center rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-semibold text-primary">
                            New
                          </span>
                        )}
                        {item.trackingNumber && (
                          <span className="font-mono text-[10px] bg-muted px-1.5 py-0.5 rounded text-muted-foreground">
                            {item.trackingNumber}
                          </span>
                        )}
                      </div>

                      <p className="text-xs text-muted-foreground leading-relaxed">
                        {item.message}
                      </p>

                      <div className="flex items-center gap-2 text-[10px] text-muted-foreground/80 pt-0.5">
                        <span title={absolute}>{relative}</span>
                        <span>•</span>
                        <span>{absolute}</span>
                        {item.isRead && item.readAt && (
                          <>
                            <span>•</span>
                            <span className="text-muted-foreground/60">
                              Read {new Date(item.readAt).toLocaleDateString()}
                            </span>
                          </>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Right Actions */}
                  <div className="flex items-center justify-end gap-2 pt-2 sm:pt-0 shrink-0">
                    {!item.isRead && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={(e) => void handleMarkOneAsRead(item, e)}
                        disabled={isMarking}
                        className="h-8 text-xs text-muted-foreground hover:text-foreground hover:bg-muted"
                        title="Mark as read"
                      >
                        {isMarking ? (
                          <Loader2 className="size-3.5 animate-spin" />
                        ) : (
                          <CheckCheck className="size-3.5 mr-1" />
                        )}
                        Mark read
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Card>

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between pt-2">
          <p className="text-xs text-muted-foreground">
            Showing {currentPage * PAGE_SIZE + 1} -{" "}
            {Math.min((currentPage + 1) * PAGE_SIZE, totalItems)} of {totalItems}
          </p>

          <div className="flex items-center gap-1.5">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setCurrentPage((prev) => Math.max(0, prev - 1))}
              disabled={currentPage === 0 || isLoading}
              className="gap-1 text-xs"
            >
              <ChevronLeft className="size-3.5" />
              Previous
            </Button>
            <span className="text-xs px-2 text-muted-foreground font-medium">
              {currentPage + 1} / {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setCurrentPage((prev) => Math.min(totalPages - 1, prev + 1))}
              disabled={currentPage >= totalPages - 1 || isLoading}
              className="gap-1 text-xs"
            >
              Next
              <ChevronRight className="size-3.5" />
            </Button>
          </div>
        </div>
      )}
    </main>
  );
}

