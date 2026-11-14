/**
 * Typed calls to the merchant portal and admin merchant endpoints (Phase 5 & 6).
 * Types mirror the backend response records in com.swiftdrop.merchant.web.
 */
import { api, type PageResponse } from "@/lib/api";
import type { ParcelSize, ParcelStatus } from "@/lib/delivery";

export type MerchantStatus = "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED";

/** One order row (MerchantOrderSummaryResponse). */
export interface MerchantOrderSummary {
  id: string;
  trackingNumber: string;
  merchantOrderReference: string | null;
  status: ParcelStatus;
  size: ParcelSize | string;
  weightGrams: number;
  courierAssigned?: boolean;
  createdAt: string;
}

/** GET /api/v1/merchants/me/dashboard (MerchantDashboardResponse). */
export interface MerchantDashboard {
  countsByStatus: Partial<Record<ParcelStatus, number>>;
  totalOrders: number;
  activeOrders: number;
  deliveredOrders: number;
  recentOrders: MerchantOrderSummary[];
}

export type MerchantDashboardData = MerchantDashboard;

/** One timeline step of an order. */
export interface MerchantOrderStatusEvent {
  status: ParcelStatus;
  occurredAt: string;
  hubName?: string | null;
  attempt?: number | null;
  note?: string | null;
}

/** GET /api/v1/merchants/me/orders/{id} (MerchantOrderDetailResponse). */
export interface MerchantOrderDetail extends MerchantOrderSummary {
  pickupCity: string | null;
  dropoffCity: string | null;
  assignedCourierId?: string | null;
  updatedAt?: string;
  history: MerchantOrderStatusEvent[];
}

export const WEBHOOK_DELIVERY_STATUSES = [
  "PENDING",
  "IN_PROGRESS",
  "SUCCEEDED",
  "FAILED",
  "SECRET_UNAVAILABLE",
] as const;
export type WebhookDeliveryStatus = (typeof WEBHOOK_DELIVERY_STATUSES)[number];

/** One row of GET /api/v1/merchants/me/webhook-deliveries (WebhookDeliveryView, Member B). */
export interface WebhookDelivery {
  id: string;
  endpointId: string;
  endpointUrl: string;
  eventType: ParcelStatus;
  responseStatus: number | null;
  deliveryStatus: WebhookDeliveryStatus | string;
  attemptCount: number;
  nextRetryAt: string | null;
  createdAt: string;
  completedAt: string | null;
}

export type WebhookDeliverySummary = WebhookDelivery;

export interface MerchantProfile {
  merchantId: string;
  status: MerchantStatus;
  businessName: string;
  businessType: string;
  businessRegistrationNo: string;
  contactPhone: string;
  businessAddress: string;
  businessDescription?: string | null;
  websiteUrl?: string | null;
  submittedAt?: string;
  decidedAt?: string | null;
  decisionReason?: string | null;
}

export type MerchantProfileSummary = MerchantProfile;

export interface MerchantProfileUpdateRequest {
  businessName?: string;
  businessType?: string;
  contactPhone?: string;
  businessAddress?: string;
  businessDescription?: string;
  websiteUrl?: string;
}

export interface SetupItem {
  id: string;
  enabled?: boolean;
  revokedAt?: string | null;
}

export interface ApiKeyItem {
  id: string;
  label: string;
  keyPrefix: string;
  createdAt: string;
  lastUsedAt?: string | null;
  revokedAt?: string | null;
  active: boolean;
}

export interface CreateApiKeyRequest {
  label: string;
}

export interface CreatedApiKeyResponse {
  id: string;
  label: string;
  keyPrefix: string;
  apiKey: string;
  createdAt: string;
}

export interface WebhookEndpoint {
  id: string;
  merchantId: string;
  url: string;
  description: string | null;
  events: ParcelStatus[];
  secretHint: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWebhookEndpointRequest {
  url: string;
  description?: string;
  events: ParcelStatus[];
}

export interface CreateWebhookEndpointResponse {
  id: string;
  merchantId: string;
  url: string;
  description: string | null;
  enabled: boolean;
  events: ParcelStatus[];
  secret: string;
  secretHint: string;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateWebhookEndpointRequest {
  url?: string;
  description?: string;
  events?: ParcelStatus[];
  enabled?: boolean;
}

export interface RotateSecretResponse {
  endpointId: string;
  secret: string;
  secretHint: string;
}

export interface AdminMerchantStatusHistoryItem {
  id: string;
  merchantId: string;
  fromStatus: MerchantStatus | null;
  toStatus: MerchantStatus;
  actorUserId: string | null;
  reason: string | null;
  createdAt: string;
}

export interface AdminMerchantDetail {
  id: string;
  userId: string;
  businessName: string;
  businessType: string;
  businessRegistrationNo: string;
  contactPhone: string;
  businessAddress: string;
  businessDescription: string | null;
  websiteUrl: string | null;
  status: MerchantStatus;
  statusReason: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
  statusHistory: AdminMerchantStatusHistoryItem[];
}

export interface ApprovalDecisionRequest {
  decision: "APPROVE" | "REJECT" | "SUSPEND";
  reason?: string;
}

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  }
  return search.toString();
}

/* =========================================================================
   Merchant Portal API Calls
   ========================================================================= */

export function getMerchantDashboard(token?: string | null): Promise<MerchantDashboard> {
  return api.get<MerchantDashboard>("/api/v1/merchants/me/dashboard", { token });
}

export const fetchMerchantDashboard = getMerchantDashboard;

export function getMerchantProfile(token?: string | null): Promise<MerchantProfile> {
  return api.get<MerchantProfile>("/api/v1/merchants/me", { token });
}

export const fetchMerchantProfile = getMerchantProfile;

export function updateMerchantProfile(
  payload: MerchantProfileUpdateRequest,
  token?: string | null,
): Promise<MerchantProfile> {
  return api.patch<MerchantProfile>("/api/v1/merchants/me", payload, { token });
}

export function listApiKeys(token?: string | null): Promise<ApiKeyItem[]> {
  return api.get<ApiKeyItem[]>("/api/v1/merchants/me/api-keys", { token });
}

export const fetchApiKeys = listApiKeys;

export function createApiKey(
  payload: CreateApiKeyRequest,
  token?: string | null,
): Promise<CreatedApiKeyResponse> {
  return api.post<CreatedApiKeyResponse>("/api/v1/merchants/me/api-keys", payload, { token });
}

export function revokeApiKey(
  keyId: string,
  token?: string | null,
): Promise<void> {
  return api.delete<void>(`/api/v1/merchants/me/api-keys/${keyId}`, { token });
}

export function listWebhookEndpoints(token?: string | null): Promise<WebhookEndpoint[]> {
  return api.get<WebhookEndpoint[]>("/api/v1/merchants/me/webhook-endpoints", { token });
}

export const fetchWebhookEndpoints = listWebhookEndpoints;

export function createWebhookEndpoint(
  payload: CreateWebhookEndpointRequest,
  token?: string | null,
): Promise<CreateWebhookEndpointResponse> {
  return api.post<CreateWebhookEndpointResponse>("/api/v1/merchants/me/webhook-endpoints", payload, { token });
}

export function updateWebhookEndpoint(
  endpointId: string,
  payload: UpdateWebhookEndpointRequest,
  token?: string | null,
): Promise<WebhookEndpoint> {
  return api.patch<WebhookEndpoint>(`/api/v1/merchants/me/webhook-endpoints/${endpointId}`, payload, { token });
}

export function deleteWebhookEndpoint(
  endpointId: string,
  token?: string | null,
): Promise<void> {
  return api.delete<void>(`/api/v1/merchants/me/webhook-endpoints/${endpointId}`, { token });
}

export function rotateWebhookSecret(
  endpointId: string,
  token?: string | null,
): Promise<RotateSecretResponse> {
  return api.post<RotateSecretResponse>(
    `/api/v1/merchants/me/webhook-endpoints/${endpointId}/secret-rotations`,
    {},
    { token },
  );
}

export function listMerchantOrders(
  page = 0,
  size = 20,
  status?: ParcelStatus,
  token?: string | null,
): Promise<PageResponse<MerchantOrderSummary>> {
  return api.get<PageResponse<MerchantOrderSummary>>(
    `/api/v1/merchants/me/orders?${query({ page, size, status })}`,
    { token },
  );
}

export function fetchMerchantOrders(
  params: { status?: string; page?: number; size?: number } = {},
  token?: string | null,
): Promise<PageResponse<MerchantOrderSummary>> {
  const qStatus = params.status && params.status !== "ALL" ? (params.status as ParcelStatus) : undefined;
  return listMerchantOrders(params.page ?? 0, params.size ?? 20, qStatus, token);
}

export function getMerchantOrder(id: string, token?: string | null): Promise<MerchantOrderDetail> {
  return api.get<MerchantOrderDetail>(`/api/v1/merchants/me/orders/${encodeURIComponent(id)}`, { token });
}

export const fetchMerchantOrderDetail = getMerchantOrder;

export function listWebhookDeliveries(
  page = 0,
  size = 20,
  status?: WebhookDeliveryStatus,
  endpointId?: string,
  token?: string | null,
): Promise<PageResponse<WebhookDelivery>> {
  return api.get<PageResponse<WebhookDelivery>>(
    `/api/v1/merchants/me/webhook-deliveries?${query({ page, size, status, endpointId })}`,
    { token },
  );
}

export function fetchMerchantWebhookDeliveries(
  params: { endpointId?: string; page?: number; size?: number } = {},
  token?: string | null,
): Promise<PageResponse<WebhookDelivery>> {
  return listWebhookDeliveries(params.page ?? 0, params.size ?? 20, undefined, params.endpointId, token);
}

/* =========================================================================
   Admin Merchant Review API Calls
   ========================================================================= */

export function fetchAdminMerchants(
  params: { status?: string; page?: number; size?: number } = {},
  token?: string | null,
): Promise<PageResponse<MerchantProfile>> {
  const q: Record<string, string | number | undefined> = {
    page: params.page,
    size: params.size,
    status: params.status && params.status !== "ALL" ? params.status : undefined,
  };
  return api.get<PageResponse<MerchantProfile>>(`/api/v1/merchants?${query(q)}`, { token });
}

export function fetchAdminMerchantDetail(
  id: string,
  token?: string | null,
): Promise<AdminMerchantDetail> {
  return api.get<AdminMerchantDetail>(`/api/v1/merchants/${encodeURIComponent(id)}`, { token });
}

export function submitAdminApprovalDecision(
  id: string,
  payload: ApprovalDecisionRequest,
  token?: string | null,
): Promise<MerchantProfile> {
  return api.post<MerchantProfile>(`/api/v1/merchants/${encodeURIComponent(id)}/approval-decisions`, payload, { token });
}
