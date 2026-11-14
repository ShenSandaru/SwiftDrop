/**
 * Notification API service and TypeScript contracts for SwiftDrop.
 * Integrates with Spring Boot backend endpoints:
 * - GET /api/v1/notifications
 * - GET /api/v1/notifications/unread-count
 * - PATCH /api/v1/notifications/{id}/read
 * - POST /api/v1/notifications/read-all
 */

import { api, type PageResponse } from "./api";

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

