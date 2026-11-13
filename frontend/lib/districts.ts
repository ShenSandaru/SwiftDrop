import { apiFetch } from "./api";

export interface PostalZone {
  code: string;
  name: string;
  postalCode?: string;
}

export interface District {
  code: string;
  name: string;
  province: string;
  postalCode: string;
  defaultBranchName: string;
  zones: PostalZone[];
}

/**
 * Fetches all 25 Sri Lankan administrative districts and their cataloged
 * postal delivery zones from the backend database (GET /api/v1/districts).
 */
export async function listDistricts(): Promise<District[]> {
  return apiFetch<District[]>("/api/v1/districts");
}

/**
 * Fetches a single district by its unique code (GET /api/v1/districts/:code).
 */
export async function getDistrict(code: string): Promise<District> {
  return apiFetch<District>(`/api/v1/districts/${encodeURIComponent(code)}`);
}

