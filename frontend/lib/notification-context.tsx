"use client";

import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  useRef,
} from "react";
import { useAuth } from "./auth-context";
import {
  getNotifications,
  getUnreadCount,
  markNotificationAsRead as apiMarkAsRead,
  markAllNotificationsAsRead as apiMarkAllAsRead,
  type NotificationResponse,
} from "./notifications";

const POLL_INTERVAL_MS = 60_000; // 60 seconds

export interface NotificationContextValue {
  notifications: NotificationResponse[];
  unreadCount: number;
  isLoading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  markAsRead: (id: string) => Promise<void>;
  markAllAsRead: () => Promise<void>;
}

const NotificationContext = createContext<NotificationContextValue | undefined>(undefined);

export function NotificationProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth();

  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Guard to prevent concurrent fetches
  const isFetchingRef = useRef(false);

  const fetchNotificationData = useCallback(async (showLoading = false) => {
    if (!isAuthenticated || isAuthLoading || isFetchingRef.current) {
      return;
    }

    if (showLoading) {
      setIsLoading(true);
    }

    isFetchingRef.current = true;
    try {
      const [feedResponse, countResponse] = await Promise.all([
        getNotifications({ page: 0, size: 20 }),
        getUnreadCount(),
      ]);

      setNotifications(feedResponse.items);
      setUnreadCount(countResponse.unreadCount);
      setError(null);
    } catch (err) {
      // Do not crash the application on background notification refresh errors
      const message = err instanceof Error ? err.message : "Failed to load notifications";
      setError(message);
    } finally {
      setIsLoading(false);
      isFetchingRef.current = false;
    }
  }, [isAuthenticated, isAuthLoading]);

  // Initial fetch and reset on auth state changes
  useEffect(() => {
    if (isAuthLoading) {
      return;
    }

    if (!isAuthenticated) {
      queueMicrotask(() => {
        setNotifications([]);
        setUnreadCount(0);
        setIsLoading(false);
        setError(null);
      });
      return;
    }

    let cancelled = false;
    queueMicrotask(() => {
      if (!cancelled) {
        fetchNotificationData(true);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, isAuthLoading, fetchNotificationData]);

  // Polling (60s) and window focus revalidation
  useEffect(() => {
    if (!isAuthenticated || isAuthLoading) {
      return;
    }

    const intervalId = window.setInterval(() => {
      fetchNotificationData();
    }, POLL_INTERVAL_MS);

    const onFocus = () => {
      if (document.visibilityState === "visible") {
        fetchNotificationData();
      }
    };

    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onFocus);

    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onFocus);
    };
  }, [isAuthenticated, isAuthLoading, fetchNotificationData]);

  const markAsRead = useCallback(async (id: string) => {
    // Optimistic update
    setNotifications((prev) =>
      prev.map((item) =>
        item.id === id && !item.isRead
          ? { ...item, isRead: true, readAt: new Date().toISOString() }
          : item,
      ),
    );
    setUnreadCount((prev) => Math.max(0, prev - 1));

    try {
      const updated = await apiMarkAsRead(id);
      // Reconcile with server response
      setNotifications((prev) =>
        prev.map((item) => (item.id === id ? updated : item)),
      );
    } catch (err) {
      // Revert / re-sync on failure
      const message = err instanceof Error ? err.message : "Failed to mark notification as read";
      setError(message);
      fetchNotificationData();
    }
  }, [fetchNotificationData]);

  const markAllAsRead = useCallback(async () => {
    const nowIso = new Date().toISOString();
    // Optimistic update
    setNotifications((prev) =>
      prev.map((item) => (item.isRead ? item : { ...item, isRead: true, readAt: nowIso })),
    );
    setUnreadCount(0);

    try {
      await apiMarkAllAsRead();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to mark all as read";
      setError(message);
      fetchNotificationData();
    }
  }, [fetchNotificationData]);

  const value: NotificationContextValue = {
    notifications,
    unreadCount,
    isLoading,
    error,
    refresh: fetchNotificationData,
    markAsRead,
    markAllAsRead,
  };

  return (
    <NotificationContext.Provider value={value}>
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications(): NotificationContextValue {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error("useNotifications must be used within a NotificationProvider");
  }
  return context;
}

