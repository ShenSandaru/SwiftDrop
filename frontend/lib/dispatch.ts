import { api, type PageResponse } from "@/lib/api";
import type { FailureReason, ParcelDetail, ParcelStatus, ParcelSize } from "@/lib/delivery";

export type DispatchAssignmentType = "PICKUP" | "DELIVERY" | "RETURN";
export type DispatchAssignmentStatus = "ACTIVE" | "COMPLETED" | "REASSIGNED" | "CANCELLED";

/** What a dispatcher may do next with a parcel; the backend works it out (ADR 0009). */
export type DispatchAction =
  | "ASSIGN_PICKUP"
  | "CANCEL"
  | "SCAN_AT_HUB"
  | "SEND_TO_NEXT_HUB"
  | "ASSIGN_DELIVERY"
  | "START_RETURN"
  | "ASSIGN_RETURN"
  | "MARK_FAILED";

export interface DispatchParcelItem {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  source: "CUSTOMER" | "MERCHANT";
  merchantOrderReference: string | null;
  assignedCourierId: string | null;
  size: ParcelSize;
  weightGrams: number;
  pickupCity: string;
  dropoffCity: string;
  createdAt: string;
  updatedAt: string;
  /** Route hubs (ADR 0009); null before pickup and for parcels picked up before the hub network. */
  originHubCode: string | null;
  /** The central hub when the route passes through it. */
  viaHubCode: string | null;
  destinationHubCode: string | null;
  currentHubCode: string | null;
  nextHubCode: string | null;
  /** The hub to scan the parcel in at next, when SCAN_AT_HUB is offered. */
  expectedHubId: string | null;
  expectedHubCode: string | null;
  deliveryAttempts: number;
  lastFailureReason: FailureReason | null;
  actions: DispatchAction[];
}

export interface DispatchAssignment {
  id: string;
  parcelId: string;
  courierId: string;
  type: DispatchAssignmentType;
  status: DispatchAssignmentStatus;
  assignedAt: string;
  completedAt: string | null;
  dispatcherId: string | null;
}

export interface DispatchAssignmentHistory {
  parcelId: string;
  assignments: DispatchAssignment[];
}

export interface DispatchCourier {
  id: string;
  fullName: string;
}

export interface DispatchParcelPageRequest {
  page: number;
  size: number;
  status?: ParcelStatus;
  source?: "CUSTOMER" | "MERCHANT";
  assigned?: boolean;
  search?: string;
}

export function listDispatchParcels({
  page,
  size,
  status,
  source,
  assigned,
  search,
}: DispatchParcelPageRequest): Promise<PageResponse<DispatchParcelItem>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) {
    query.set("status", status);
  }
  if (source) {
    query.set("source", source);
  }
  if (assigned !== undefined) {
    query.set("assigned", String(assigned));
  }
  if (search?.trim()) {
    query.set("search", search.trim());
  }
  return api.get<PageResponse<DispatchParcelItem>>(`/api/v1/dispatch/parcels?${query.toString()}`);
}

export async function countDispatchParcels(status?: ParcelStatus): Promise<number> {
  const page = await listDispatchParcels({ page: 0, size: 1, status });
  return page.totalItems;
}

export function listDispatchCouriers(): Promise<DispatchCourier[]> {
  return api.get<DispatchCourier[]>("/api/v1/dispatch/couriers");
}

export async function listAllDispatchCouriers(): Promise<DispatchCourier[]> {
  return listDispatchCouriers();
}

export function listActiveDispatchAssignments(
  type: DispatchAssignmentType,
  page: number,
  size: number,
): Promise<PageResponse<DispatchAssignment>> {
  const query = new URLSearchParams({ type, page: String(page), size: String(size) });
  return api.get<PageResponse<DispatchAssignment>>(
    `/api/v1/dispatch/active-assignments?${query.toString()}`,
  );
}

export async function listAllActiveDispatchAssignments(
  type: DispatchAssignmentType,
): Promise<DispatchAssignment[]> {
  const firstPage = await listActiveDispatchAssignments(type, 0, 100);
  if (firstPage.totalPages < 2) {
    return firstPage.items;
  }

  const remaining = await Promise.all(
    Array.from({ length: firstPage.totalPages - 1 }, (_, index) =>
      listActiveDispatchAssignments(type, index + 1, 100),
    ),
  );
  return [...firstPage.items, ...remaining.flatMap((page) => page.items)];
}

export function getCourierWorkloads(): Promise<Record<string, number>> {
  return api.get<Record<string, number>>("/api/v1/dispatch/courier-workloads");
}

export function assignDispatchCourier(
  parcelId: string,
  courierId: string,
  type: DispatchAssignmentType,
): Promise<DispatchAssignment> {
  return api.post<DispatchAssignment>("/api/v1/dispatch/parcel-assignments", {
    parcelId,
    courierId,
    type,
  });
}

export function getDispatchAssignmentHistory(id: string): Promise<DispatchAssignmentHistory> {
  return api.get<DispatchAssignmentHistory>(
    `/api/v1/dispatch/parcels/${encodeURIComponent(id)}/assignment-history`,
  );
}

export function getDispatchParcelDetails(id: string): Promise<ParcelDetail> {
  return api.get<ParcelDetail>(`/api/v1/parcels/${encodeURIComponent(id)}`);
}

// ---- Hub network steps (ADR 0009 §3); each answers 204, or 409 when the rules do not allow it ----

export function scanParcelAtHub(parcelId: string, hubId: string): Promise<void> {
  return api.post<void>(`/api/v1/dispatch/parcels/${encodeURIComponent(parcelId)}/hub-scans`, { hubId });
}

export function sendParcelToNextHub(parcelId: string): Promise<void> {
  return api.post<void>(`/api/v1/dispatch/parcels/${encodeURIComponent(parcelId)}/transit`);
}

export function startParcelReturn(parcelId: string): Promise<void> {
  return api.post<void>(`/api/v1/dispatch/parcels/${encodeURIComponent(parcelId)}/return`);
}

export function markParcelFailed(parcelId: string, reason: FailureReason, note?: string): Promise<void> {
  return api.post<void>(`/api/v1/dispatch/parcels/${encodeURIComponent(parcelId)}/failure`, { reason, note });
}

export function cancelDispatchParcel(parcelId: string): Promise<void> {
  return api.post<void>(`/api/v1/dispatch/parcels/${encodeURIComponent(parcelId)}/cancellation`);
}
