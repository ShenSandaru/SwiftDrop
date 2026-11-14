"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Bell, CheckCheck, Loader2, Package, UserCheck, AlertCircle } from "lucide-react";
import { cn } from "cn";
import { useNotifications } from "@/lib/notification-context";
import { useAuth } from "@/lib/auth-context";
import type { NotificationResponse } from "@/lib/notifications";

function formatRelativeTime(dateString: string): string {
  const date = new Date(dateString);
  const now = new Date();
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

  if (diffInSeconds < 60) return "just now";
  const diffInMinutes = Math.floor(diffInSeconds / 60);
  if (diffInMinutes < 60) return `${diffInMinutes}m ago`;
  const diffInHours = Math.floor(diffInMinutes / 60);
  if (diffInHours < 24) return `${diffInHours}h ago`;
  const diffInDays = Math.floor(diffInHours / 24);
  if (diffInDays < 7) return `${diffInDays}d ago`;
  return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export function NotificationBell() {
  const router = useRouter();
  const { hasRole } = useAuth();
  const {
    notifications,
    unreadCount,
    isLoading,
    error,
    markAsRead,
    markAllAsRead,
    refresh,
  } = useNotifications();

  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Close dropdown on outside click or Escape
  useEffect(() => {
    if (!open) return;

    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  const handleNotificationClick = (item: NotificationResponse) => {
    if (!item.isRead) {
      // Fire-and-forget mark-as-read so network latency never delays user navigation
      void markAsRead(item.id);
    }
    setOpen(false);

    // If parcel exists, navigate to role-appropriate parcel details
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
      // Non-parcel account notification (e.g. merchant approval / profile update)
      if (hasRole("MERCHANT")) {
        router.push("/merchant/status");
      } else if (hasRole("CUSTOMER")) {
        router.push("/customer/profile");
      }
    }
  };

  const getFooterLink = () => {
    return { href: "/notifications", label: "View all notifications" };
  };

  const footerLink = getFooterLink();

  const badgeText = unreadCount > 99 ? "99+" : unreadCount > 0 ? String(unreadCount) : null;

  return (
    <div ref={containerRef} className="relative inline-flex items-center">
      {/* Bell Button */}
      <button
        type="button"
        aria-label="Notifications"
        aria-expanded={open}
        onClick={() => {
          setOpen((prev) => !prev);
          if (!open) {
            refresh();
          }
        }}
        className={cn(
          "relative inline-flex size-9 shrink-0 items-center justify-center rounded-full transition-colors",
          "bg-background/10 text-background hover:bg-background/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
          open && "bg-background/25 text-background",
        )}
      >
        <Bell className="size-4" aria-hidden="true" />
        {badgeText && (
          <span
            className="absolute -top-1 -right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-bold leading-none text-primary-foreground shadow-sm"
            aria-label={`${unreadCount} unread notifications`}
          >
            {badgeText}
          </span>
        )}
      </button>

      {/* Dropdown Menu */}
      {open && (
        <div
          className={cn(
            "fixed sm:absolute right-3 sm:right-0 top-16 sm:top-full z-50 flex w-[calc(100vw-1.5rem)] sm:w-96 max-w-[24rem] flex-col rounded-2xl border border-border bg-popover p-0 text-popover-foreground shadow-2xl ring-1 ring-black/10 dark:ring-white/10",
            "motion-safe:animate-in motion-safe:fade-in-50 motion-safe:zoom-in-95",
          )}
          role="region"
          aria-label="Notification Center"
        >
          {/* Header */}
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold tracking-tight text-foreground">Notifications</h2>
              {unreadCount > 0 && (
                <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-semibold text-primary">
                  {unreadCount} unread
                </span>
              )}
            </div>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={() => markAllAsRead()}
                className="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground"
              >
                <CheckCheck className="size-3.5" aria-hidden="true" />
                <span>Mark all as read</span>
              </button>
            )}
          </div>

          {/* List Content */}
          <div className="max-h-[22rem] overflow-y-auto divide-y divide-border/60">
            {isLoading && notifications.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-2 py-8 text-muted-foreground">
                <Loader2 className="size-5 animate-spin" aria-hidden="true" />
                <span className="text-xs">Loading notifications...</span>
              </div>
            ) : error && notifications.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-2 py-8 px-4 text-center text-muted-foreground">
                <AlertCircle className="size-5 text-destructive" aria-hidden="true" />
                <span className="text-xs text-destructive">{error}</span>
                <button
                  type="button"
                  onClick={() => refresh()}
                  className="mt-1 text-xs font-semibold text-primary hover:underline"
                >
                  Try again
                </button>
              </div>
            ) : notifications.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-2 py-10 text-center text-muted-foreground">
                <div className="flex size-10 items-center justify-center rounded-full bg-muted">
                  <Bell className="size-5 opacity-40" aria-hidden="true" />
                </div>
                <div className="space-y-0.5">
                  <p className="text-xs font-medium text-foreground">No notifications</p>
                  <p className="text-[11px] text-muted-foreground">You are all caught up!</p>
                </div>
              </div>
            ) : (
              notifications.slice(0, 10).map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => handleNotificationClick(item)}
                  className={cn(
                    "flex w-full items-start gap-3 p-3.5 text-left transition-colors hover:bg-muted/60",
                    !item.isRead ? "bg-accent/40" : "bg-transparent opacity-85",
                  )}
                >
                  <div
                    className={cn(
                      "mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full",
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
                  <div className="flex-1 space-y-1 min-w-0">
                    <div className="flex items-center justify-between gap-1">
                      <p className="truncate text-xs font-semibold text-foreground">
                        {item.title}
                      </p>
                      <span className="shrink-0 text-[10px] text-muted-foreground">
                        {formatRelativeTime(item.createdAt)}
                      </span>
                    </div>
                    <p className="line-clamp-2 text-xs text-muted-foreground">
                      {item.message}
                    </p>
                    {item.trackingNumber && (
                      <span className="inline-block font-mono text-[10px] text-primary/80">
                        {item.trackingNumber}
                      </span>
                    )}
                  </div>
                  {!item.isRead && (
                    <span
                      className="mt-1.5 size-2 shrink-0 rounded-full bg-primary"
                      aria-label="Unread"
                    />
                  )}
                </button>
              ))
            )}
          </div>

          {/* Footer */}
          <div className="border-t border-border p-2 text-center bg-muted/20">
            <Link
              href={footerLink.href}
              onClick={() => setOpen(false)}
              className="inline-flex w-full items-center justify-center rounded-lg py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            >
              {footerLink.label}
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

