/**
 * Notification API service and TypeScript contracts for SwiftDrop.
 * Integrates with Spring Boot backend endpoints:
 * - GET /api/v1/notifications
 * - GET /api/v1/notifications/unread-count
 * - PATCH /api/v1/notifications/{id}/read
 * - POST /api/v1/notifications/read-all
 */

import { api, type PageResponse } from "./api";
export type { PageResponse };

export type NotificationChannel = "IN_APP" | "EMAIL" | "SMS" | "PUSH";

export interface NotificationResponse {
  id: string;
  parcelId: string | null;
  trackingNumber: string | null;
  title: string;
  message: string;
  channel: NotificationChannel;
  isRead: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export interface BulkReadResponse {
  updatedCount: number;
}

export interface NotificationFeedParams {
  unreadOnly?: boolean;
  page?: number;
  size?: number;
}

/**
 * Retrieves the paginated notification feed for the authenticated caller.
 */
export async function getNotifications(
  params: NotificationFeedParams = {},
): Promise<PageResponse<NotificationResponse>> {
  const query = new URLSearchParams();
  if (params.unreadOnly !== undefined) {
    query.set("unreadOnly", String(params.unreadOnly));
  }
  if (params.page !== undefined) {
    query.set("page", String(params.page));
  }
  if (params.size !== undefined) {
    query.set("size", String(params.size));
  }

  const queryString = query.toString();
  const endpoint = `/api/v1/notifications${queryString ? `?${queryString}` : ""}`;
  return api.get<PageResponse<NotificationResponse>>(endpoint);
}

/**
 * Returns the unread notification count for the authenticated caller.
 */
export async function getUnreadCount(): Promise<UnreadCountResponse> {
  return api.get<UnreadCountResponse>("/api/v1/notifications/unread-count");
}

/**
 * Marks a specific notification as read.
 * Idempotent. Returns the updated NotificationResponse.
 */
export async function markNotificationAsRead(id: string): Promise<NotificationResponse> {
  return api.patch<NotificationResponse>(`/api/v1/notifications/${encodeURIComponent(id)}/read`);
}

/**
 * Marks all unread in-app notifications as read for the authenticated caller.
 * Returns the count of updated notifications.
 */
export async function markAllNotificationsAsRead(): Promise<BulkReadResponse> {
  return api.post<BulkReadResponse>("/api/v1/notifications/read-all");
}

export interface PushTokenResponse {
  id: string;
  platform: string;
  active: boolean;
  updatedAt: string;
}

export async function registerPushToken(
  token: string,
  platform = "WEB",
): Promise<PushTokenResponse> {
  return api.post<PushTokenResponse>("/api/v1/notifications/push-tokens", {
    token,
    platform,
  });
}

export async function unregisterPushToken(token: string): Promise<void> {
  await api.delete("/api/v1/notifications/push-tokens", { token });
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Resolves the role-appropriate navigation destination for a notification item,
 * or returns null when no valid destination can be determined.
 */
export function resolveNotificationDestination(
  item: NotificationResponse,
  hasRole: (role: string) => boolean,
): string | null {
  const hasValidParcelId = Boolean(item.parcelId && UUID_REGEX.test(item.parcelId));

  if (hasValidParcelId && item.parcelId) {
    if (hasRole("CUSTOMER")) {
      return `/customer/parcels/${item.parcelId}`;
    }
    if (hasRole("COURIER")) {
      return `/courier/parcels/${item.parcelId}`;
    }
    if (hasRole("ADMIN") || hasRole("DISPATCHER")) {
      return "/dispatch";
    }
    if (hasRole("MERCHANT")) {
      return "/merchant/orders";
    }
    if (item.trackingNumber) {
      return `/track?number=${encodeURIComponent(item.trackingNumber)}`;
    }
    return null;
  }

  if (hasRole("ADMIN") || hasRole("DISPATCHER")) {
    return "/admin/merchants";
  }
  if (hasRole("MERCHANT")) {
    if (item.title.toLowerCase().includes("approved")) {
      return "/merchant";
    }
    return "/merchant/status";
  }
  if (hasRole("CUSTOMER")) {
    return "/customer/profile";
  }
  if (item.trackingNumber) {
    return `/track?number=${encodeURIComponent(item.trackingNumber)}`;
  }

  return null;
}

