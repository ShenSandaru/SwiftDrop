import React from "react";
import { PortalPlaceholderCard } from "@/components/portal";

export default function MerchantOrdersPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="Merchant Orders & Bulk Intake"
        description="Monitor merchant order fulfillment, import bulk shipment batches, and track delivery progress."
        workstream="WS4 & WS5 — Merchant Orders & Intake"
        contractRef="MER-2, MER-4, D-03, D-25 · Contract §4.2"
        plannedEndpoints={[
          "GET /api/v1/merchants/me/orders (planned)",
          "POST /api/v1/merchant-orders (planned)",
          "GET /api/v1/merchant-orders/{merchantOrderReference} (planned)",
        ]}
        plannedFeatures={[
          "Paginated merchant order dashboard with status filtering (CREATED, IN_TRANSIT, DELIVERED)",
          "Bulk CSV order intake and automated parcel creation",
          "Merchant external reference mapping and cash-on-delivery tracking",
          "Real-time fulfillment milestone monitoring",
        ]}
      />
    </div>
  );
}
