import { api } from "./api";

export interface ZoneAdmin {
  id: string;
  code: string;
  name: string;
  active: boolean;
  version: number;
  createdAt: string;
}

export interface ZoneInput {
  code: string;
  name: string;
  active?: boolean;
  version?: number;
}

export interface PricingRuleAdmin {
  id: string;
  originZoneId: string;
  originZoneCode: string | null;
  originZoneName: string | null;
  destinationZoneId: string;
  destinationZoneCode: string | null;
  destinationZoneName: string | null;
  baseFee: number;
  perKgFee: number;
  smallSizeSurcharge: number;
  mediumSizeSurcharge: number;
  largeSizeSurcharge: number;
  currency: string;
  active: boolean;
  version: number;
  createdAt: string;
}

export interface PricingRuleInput {
  originZoneId: string;
  destinationZoneId: string;
  baseFee: number;
  perKgFee: number;
  smallSizeSurcharge?: number;
  mediumSizeSurcharge?: number;
  largeSizeSurcharge?: number;
  currency?: string;
  active?: boolean;
  version?: number;
}

/** Lists all zones (active and inactive) for admin administration. */
export function listAdminZones(): Promise<ZoneAdmin[]> {
  return api.get<ZoneAdmin[]>("/api/v1/zones?all=true");
}

/** Creates a new geographical delivery zone. */
export function createZone(input: ZoneInput): Promise<ZoneAdmin> {
  return api.post<ZoneAdmin>("/api/v1/zones", input);
}

/** Updates zone name, code, or active status with version optimistic locking. */
export function updateZone(id: string, input: ZoneInput): Promise<ZoneAdmin> {
  return api.put<ZoneAdmin>(`/api/v1/zones/${encodeURIComponent(id)}`, input);
}

/** Lists all pricing rules across zone pairs. */
export function listPricingRules(): Promise<PricingRuleAdmin[]> {
  return api.get<PricingRuleAdmin[]>("/api/v1/pricing-rules");
}

/** Creates a pricing rule for an origin-destination zone pair. */
export function createPricingRule(input: PricingRuleInput): Promise<PricingRuleAdmin> {
  return api.post<PricingRuleAdmin>("/api/v1/pricing-rules", input);
}

/** Updates fee calculation parameters or active status for a pricing rule. */
export function updatePricingRule(id: string, input: PricingRuleInput): Promise<PricingRuleAdmin> {
  return api.put<PricingRuleAdmin>(`/api/v1/pricing-rules/${encodeURIComponent(id)}`, input);
}

