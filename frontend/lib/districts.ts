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
  latitude?: number;
  longitude?: number;
  zones: PostalZone[];
}

/**
 * Standard GPS coordinates (capital coordinates) for all 25 Sri Lankan administrative districts.
 * Serves as persistent coordinate reference & instant client-side lookup.
 */
export const DISTRICT_COORDINATES: Record<string, { lat: number; lng: number }> = {
  CMB: { lat: 6.927079, lng: 79.861244 }, // Colombo
  GAM: { lat: 7.084000, lng: 79.994300 }, // Gampaha
  KAL: { lat: 6.585400, lng: 79.960700 }, // Kalutara
  KDY: { lat: 7.290572, lng: 80.633728 }, // Kandy
  MTL: { lat: 7.467500, lng: 80.623400 }, // Matale
  NUE: { lat: 6.949700, lng: 80.789100 }, // Nuwara Eliya
  GAL: { lat: 6.053500, lng: 80.221000 }, // Galle
  MAT: { lat: 5.954920, lng: 80.554955 }, // Matara
  HAM: { lat: 6.142900, lng: 81.121200 }, // Hambantota
  JAF: { lat: 9.661498, lng: 80.025543 }, // Jaffna
  KIL: { lat: 9.380300, lng: 80.377000 }, // Kilinochchi
  MAN: { lat: 8.981000, lng: 79.904400 }, // Mannar
  VAV: { lat: 8.754200, lng: 80.498200 }, // Vavuniya
  MUL: { lat: 9.267100, lng: 80.814200 }, // Mullaitivu
  BAT: { lat: 7.731000, lng: 81.674700 }, // Batticaloa
  AMP: { lat: 7.291200, lng: 81.672400 }, // Ampara
  TRI: { lat: 8.587400, lng: 81.215200 }, // Trincomalee
  KUR: { lat: 7.486300, lng: 80.362300 }, // Kurunegala
  PUT: { lat: 8.040800, lng: 79.839400 }, // Puttalam
  ANU: { lat: 8.311400, lng: 80.403700 }, // Anuradhapura
  POL: { lat: 7.940300, lng: 81.018800 }, // Polonnaruwa
  BAD: { lat: 6.993400, lng: 81.055000 }, // Badulla
  MON: { lat: 6.872800, lng: 81.350700 }, // Monaragala
  RAT: { lat: 6.682800, lng: 80.403400 }, // Ratnapura
  KEG: { lat: 7.251300, lng: 80.346400 }, // Kegalle
};

/**
 * Calculates straight-line distance using the Haversine formula and multiplies by 1.25
 * to estimate real Sri Lanka road transit distance between two points.
 */
export function calculateRoadDistanceKm(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  if (lat1 === lat2 && lon1 === lon2) return 0;

  const R = 6371; // Earth radius in km
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  const straightLineKm = R * c;

  // Road curvature factor: roads in Sri Lanka are typically ~1.22x to 1.25x straight-line
  const roadKm = straightLineKm * 1.25;
  return Math.round(roadKm);
}

/**
 * Resolves coordinates for a district code, district name, or zone code.
 */
export function resolveDistrictCoordinates(
  key: string
): { lat: number; lng: number } | null {
  if (!key) return null;
  const upper = key.trim().toUpperCase();

  // 1. Direct code lookup (e.g. "CMB")
  if (DISTRICT_COORDINATES[upper]) {
    return DISTRICT_COORDINATES[upper];
  }

  // 2. Prefix match (e.g. zone "CMB-MRD" -> "CMB")
  const prefix = upper.split(/[-_]/)[0];
  if (DISTRICT_COORDINATES[prefix]) {
    return DISTRICT_COORDINATES[prefix];
  }

  // 3. Name lookup (e.g. "Colombo")
  const foundCode = Object.keys(DISTRICT_COORDINATES).find(
    (code) => code.toLowerCase() === key.toLowerCase()
  );
  if (foundCode) {
    return DISTRICT_COORDINATES[foundCode];
  }

  return null;
}

export interface DistancePricingResult {
  distanceKm: number;
  freeKmAllowance: number;
  billableKm: number;
  baseFee: number;
  distanceFee: number;
  totalSuggestedBaseFee: number;
  isCapped: boolean;
  capAmount: number;
}

/**
 * The maximum hub-to-hub road distance in Sri Lanka (Matara <-> Jaffna ~520 km).
 * Used as the baseline for the proportional distance tariff model.
 */
export const MAX_ISLAND_DISTANCE_KM = 520;

/**
 * Calculates the dynamic line-haul rate per km based on the base fee and maximum cap:
 * ratePerKm = (maxCap - baseFee) / (maxIslandDistanceKm - freeKmAllowance)
 */
export function calculateDynamicRatePerKm(
  baseFee: number,
  maxCap: number,
  freeKmAllowance: number = 30,
  maxIslandDistanceKm: number = MAX_ISLAND_DISTANCE_KM
): number {
  if (maxCap <= baseFee || maxIslandDistanceKm <= freeKmAllowance) return 0;
  const rate = (maxCap - baseFee) / (maxIslandDistanceKm - freeKmAllowance);
  return Math.round(rate * 100) / 100;
}

/**
 * Calculates fair distance-based route pricing with proportional scaling:
 * - Up to freeKmAllowance (default 30 km): standardBaseFee only
 * - Between 30 km and MAX_ISLAND_DISTANCE_KM (520 km): proportionally scaled so that
 *   only the maximum distance in Sri Lanka (Matara <-> Jaffna) reaches the maxCap.
 * - ratePerKm is dynamically tied to (maxCap - standardBaseFee) / (520 - 30).
 */
export function calculateFairRoutePrice(
  distanceKm: number,
  options?: {
    standardBaseFee?: number;
    freeKmAllowance?: number;
    ratePerKm?: number;
    maxCap?: number;
    maxIslandDistanceKm?: number;
  }
): DistancePricingResult {
  const base = options?.standardBaseFee ?? 350;
  const freeKm = options?.freeKmAllowance ?? 30;
  const maxCap = options?.maxCap ?? 1000;
  const maxIslandDistance = options?.maxIslandDistanceKm ?? MAX_ISLAND_DISTANCE_KM;

  const derivedRatePerKm =
    options?.ratePerKm !== undefined && options?.ratePerKm > 0
      ? options.ratePerKm
      : calculateDynamicRatePerKm(base, maxCap, freeKm, maxIslandDistance);

  const billableKm = Math.max(0, distanceKm - freeKm);
  const distanceFee = billableKm * derivedRatePerKm;
  const rawTotal = base + distanceFee;
  const isCapped = distanceKm >= maxIslandDistance || rawTotal >= maxCap;
  const totalSuggestedBaseFee = Math.min(maxCap, rawTotal);

  return {
    distanceKm,
    freeKmAllowance: freeKm,
    billableKm,
    baseFee: base,
    distanceFee: Math.round(distanceFee * 100) / 100,
    totalSuggestedBaseFee: Math.round(totalSuggestedBaseFee * 100) / 100,
    isCapped,
    capAmount: maxCap,
  };
}

/**
 * Fetches all 25 Sri Lankan administrative districts from the backend API (GET /api/v1/districts).
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
