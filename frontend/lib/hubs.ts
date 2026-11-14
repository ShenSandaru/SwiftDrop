import { api } from "./api";

/** A hub of the delivery network (ADR 0009): a zone's branch, the central hub, or both. */
export interface Hub {
  id: string;
  code: string;
  name: string;
  city: string;
  zoneId: string | null;
  central: boolean;
  active: boolean;
}

export interface HubInput {
  code: string;
  name: string;
  city: string;
  zoneId: string | null;
  central: boolean;
  active?: boolean;
}

export function listHubs(): Promise<Hub[]> {
  return api.get<Hub[]>("/api/v1/hubs");
}

export function createHub(input: HubInput): Promise<Hub> {
  return api.post<Hub>("/api/v1/hubs", input);
}

/** Changes everything but the code, which parcel history records. */
export function updateHub(id: string, input: HubInput): Promise<Hub> {
  return api.put<Hub>(`/api/v1/hubs/${id}`, input);
}

/** Deletes a hub from the delivery network. */
export function deleteHub(id: string): Promise<void> {
  return api.delete<void>(`/api/v1/hubs/${encodeURIComponent(id)}`);
}

/** A direct link between two branch hubs (ADR 0011): parcels between them skip the central hub. */
export interface HubLink {
  hubId: string;
  otherHubId: string;
  createdAt: string;
}

export function listHubLinks(): Promise<HubLink[]> {
  return api.get<HubLink[]>("/api/v1/hubs/links");
}

/** Links two branches; the order of the two hubs does not matter. */
export function createHubLink(hubId: string, otherHubId: string): Promise<HubLink> {
  return api.post<HubLink>("/api/v1/hubs/links", { hubId, otherHubId });
}

export function deleteHubLink(hubId: string, otherHubId: string): Promise<void> {
  return api.delete<void>(
    `/api/v1/hubs/links/${encodeURIComponent(hubId)}/${encodeURIComponent(otherHubId)}`,
  );
}
