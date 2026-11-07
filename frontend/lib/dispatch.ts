import { api, type PageResponse, type UserResponse } from "@/lib/api";
import type { ParcelDetail, ParcelStatus, ParcelSize } from "@/lib/delivery";

export type DispatchAssignmentType = "PICKUP" | "DELIVERY";
export type DispatchAssignmentStatus = "ACTIVE" | "COMPLETED" | "REASSIGNED";

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

export function listDispatchCouriers(page: number, size: number): Promise<PageResponse<UserResponse>> {
  const query = new URLSearchParams({ page: String(page), size: String(size), role: "COURIER" });
  return api.get<PageResponse<UserResponse>>(`/api/v1/users?${query.toString()}`);
}

export async function listAllDispatchCouriers(): Promise<UserResponse[]> {
  const firstPage = await listDispatchCouriers(0, 100);
  if (firstPage.totalPages < 2) {
    return firstPage.items;
  }

  const remaining = await Promise.all(
    Array.from({ length: firstPage.totalPages - 1 }, (_, index) =>
      listDispatchCouriers(index + 1, 100),
    ),
  );
  return [...firstPage.items, ...remaining.flatMap((page) => page.items)];
}

export function listAwaitingDeliveryAssignments(
  page: number,
  size: number,
): Promise<PageResponse<DispatchParcelItem>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return api.get<PageResponse<DispatchParcelItem>>(
    `/api/v1/dispatch/awaiting-delivery-assignments?${query.toString()}`,
  );
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
