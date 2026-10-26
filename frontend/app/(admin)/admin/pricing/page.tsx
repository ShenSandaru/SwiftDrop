import React from "react";
import { PortalPlaceholderCard } from "@/components/portal";

export default function AdminPricingPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="Delivery Zones & Pricing Rules"
        description="Configure geographical delivery zones, base delivery rates, and weight-tier fee calculation rules."
        workstream="WS8 — Pricing Administration & Zones"
        contractRef="ADM-3 · Contract §4.2"
        plannedEndpoints={[
          "GET /api/v1/zones (planned)",
          "POST /api/v1/zones (planned)",
          "PUT /api/v1/zones/{id} (planned)",
          "GET /api/v1/pricing-rules (planned)",
          "POST /api/v1/pricing-rules (planned)",
          "PUT /api/v1/pricing-rules/{id} (planned)",
        ]}
        plannedFeatures={[
          "Geographical delivery zone management table with active status flags",
          "Tiered pricing rule matrix defining base fee and incremental gram rate",
          "Size category multipliers (SMALL, MEDIUM, LARGE surcharges)",
          "Interactive fee quote simulation sandbox for rate testing",
        ]}
      />
    </div>
  );
}
