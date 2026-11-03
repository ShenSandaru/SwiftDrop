/**
 * Typed calls to the delivery, tracking and pricing endpoints used by the customer and courier
 * pages. Types mirror the backend responses (delivery module web records); amounts are strings
 * such as "850.00" so they are never rounded through floating point.
 */
import { api, ApiError, getApiBaseUrl, type PageResponse } from "@/lib/api";

export const PARCEL_STATUSES = [
  "CREATED",
  "PICKED_UP",
  "IN_TRANSIT",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "FAILED",
] as const;
export type ParcelStatus = (typeof PARCEL_STATUSES)[number];

export const FAILURE_REASONS = [
  "RECIPIENT_UNAVAILABLE",
  "ADDRESS_NOT_FOUND",
  "REFUSED",
  "DAMAGED",
  "OTHER",
] as const;
export type FailureReason = (typeof FAILURE_REASONS)[number];

export const PARCEL_SIZES = ["SMALL", "MEDIUM", "LARGE"] as const;
export type ParcelSize = (typeof PARCEL_SIZES)[number];

export interface Money {
  amount: string;
  currency: string;
}

export interface Address {
  contactName: string;
  phone: string;
  line1: string;
  line2: string | null;
  city: string;
  zoneId: string;
  postalCode: string | null;
}

export interface AddressInput {
  contactName: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  zoneId: string;
  postalCode?: string;
}

export interface BookingRequest {
  pickup: AddressInput;
  dropoff: AddressInput;
  size: ParcelSize;
  weightGrams: number;
  description?: string;
}

export interface ParcelCreated {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  fee: Money;
  assignedCourierId: string | null;
  createdAt: string;
}

export interface ParcelListItem {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  source: "CUSTOMER" | "MERCHANT";
  size: ParcelSize;
  weightGrams: number;
  pickupCity: string;
  dropoffCity: string;
  fee: Money;
  createdAt: string;
  updatedAt: string;
}

export interface StatusEntry {
  sequence: number;
  status: ParcelStatus;
  occurredAt: string;
  failureReason: FailureReason | null;
  /** Courier note; null for viewers who may not see notes (customers). */
  note: string | null;
}

export interface ParcelDetail {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  source: "CUSTOMER" | "MERCHANT";
  merchantOrderReference: string | null;
  pickup: Address;
  dropoff: Address;
  size: ParcelSize;
  weightGrams: number;
  description: string | null;
  fee: Money;
  assignedCourierId: string | null;
  createdAt: string;
  updatedAt: string;
  history: StatusEntry[];
}

export interface Receipt {
  parcelId: string;
  trackingNumber: string;
  status: ParcelStatus;
  createdAt: string;
  pickup: Address;
  dropoff: Address;
  size: ParcelSize;
  weightGrams: number;
  description: string | null;
  fee: Money;
  deliveredAt: string | null;
  recipientName: string | null;
  failedAt: string | null;
  failureReason: FailureReason | null;
}

export interface CourierParcel {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  nextStatuses: ParcelStatus[];
  pickup: Address;
  dropoff: Address;
  size: ParcelSize;
  weightGrams: number;
  description: string | null;
  proofCaptured: boolean;
  assignedAt: string | null;
  updatedAt: string;
}

export interface Proof {
  parcelId: string;
  recipientName: string;
  hasPhoto: boolean;
  photoSizeBytes: number | null;
  capturedAt: string;
}

export interface PublicStatusEvent {
  status: ParcelStatus;
  occurredAt: string;
}

export interface PublicTracking {
  trackingNumber: string;
  status: ParcelStatus;
  pickupCity: string;
  dropoffCity: string;
  history: PublicStatusEvent[];
}

export interface Zone {
  id: string;
  code: string;
  name: string;
}

export interface PriceQuote {
  amount: string;
  currency: string;
  pricingRuleId: string;
  breakdown: { baseFee: string; weightFee: string; sizeSurcharge: string };
}

export const MAX_PHOTO_BYTES = 2 * 1024 * 1024;
export const MAX_WEIGHT_GRAMS = 30_000;
/** E.164, as the backend validates it: + then 8 to 15 digits, no leading zero. */
export const PHONE_PATTERN = /^\+[1-9][0-9]{7,14}$/;

export function isFinalStatus(status: ParcelStatus): boolean {
  return status === "DELIVERED" || status === "FAILED";
}

/** True when pricing (zones, quotes) is not deployed on this backend yet. */
export function isPricingUnavailable(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || error.status === 405);
}

// Customer

export function bookParcel(request: BookingRequest): Promise<ParcelCreated> {
  return api.post<ParcelCreated>("/api/v1/parcels", request);
}

export function listParcels(page = 0, size = 20, status?: ParcelStatus): Promise<PageResponse<ParcelListItem>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) {
    query.set("status", status);
  }
  return api.get<PageResponse<ParcelListItem>>(`/api/v1/parcels?${query}`);
}

export function getParcel(id: string): Promise<ParcelDetail> {
  return api.get<ParcelDetail>(`/api/v1/parcels/${encodeURIComponent(id)}`);
}

export function getReceipt(id: string): Promise<Receipt> {
  return api.get<Receipt>(`/api/v1/parcels/${encodeURIComponent(id)}/receipt`);
}

export function getProofPhoto(id: string): Promise<Blob> {
  return api.blob(`/api/v1/parcels/${encodeURIComponent(id)}/proof/photo`);
}

// Pricing (pricing module; may not be deployed yet)

export function listZones(): Promise<Zone[]> {
  return api.get<Zone[]>("/api/v1/zones");
}

export function getQuote(
  pickupZoneId: string,
  dropoffZoneId: string,
  size: ParcelSize,
  weightGrams: number,
): Promise<PriceQuote> {
  return api.post<PriceQuote>("/api/v1/price-quotes", { pickupZoneId, dropoffZoneId, size, weightGrams });
}

// Courier

export function listCourierParcels(): Promise<CourierParcel[]> {
  return api.get<CourierParcel[]>("/api/v1/courier/parcels");
}

export function changeStatus(
  id: string,
  toStatus: ParcelStatus,
  failureReason?: FailureReason,
  note?: string,
): Promise<ParcelDetail> {
  return api.post<ParcelDetail>(`/api/v1/parcels/${encodeURIComponent(id)}/status-changes`, {
    toStatus,
    failureReason: failureReason ?? null,
    note: note?.trim() ? note.trim() : null,
  });
}

export function uploadProof(id: string, recipientName: string, photo?: File | null): Promise<Proof> {
  const form = new FormData();
  form.append("recipientName", recipientName);
  if (photo) {
    form.append("photo", photo);
  }
  return api.put<Proof>(`/api/v1/parcels/${encodeURIComponent(id)}/proof`, form);
}

// Public tracking (no login)

/** Public URL of a tracking resource; also used for the live stream. */
export function trackingUrl(trackingNumber: string, suffix = ""): string {
  const base = getApiBaseUrl().replace(/\/+$/, "");
  return `${base}/api/v1/tracked-parcels/${encodeURIComponent(trackingNumber.trim())}${suffix}`;
}

/**
 * Never sends the login token: the endpoint is public, and an expired token would otherwise be
 * rejected (401) and sign the visitor out.
 */
export async function getPublicTracking(trackingNumber: string): Promise<PublicTracking> {
  const response = await fetch(trackingUrl(trackingNumber));
  if (!response.ok) {
    throw new ApiError(
      response.status,
      response.status === 404 ? "We couldn't find a parcel with that tracking number." : "Tracking is unavailable right now.",
    );
  }
  return (await response.json()) as PublicTracking;
}
