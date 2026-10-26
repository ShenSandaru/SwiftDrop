import React from "react";
import { PortalPlaceholderCard } from "@/components/portal";

export default function MerchantApiKeysPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="API Key Management"
        description="Manage API credentials for external e-commerce integrations, ERP systems, and automated intake."
        workstream="WS5 — Merchant API Keys & Security"
        contractRef="MER-2, D-03 · Contract §4.2"
        plannedEndpoints={[
          "GET /api/v1/merchants/me/api-keys (planned)",
          "POST /api/v1/merchants/me/api-keys (planned)",
          "DELETE /api/v1/merchants/me/api-keys/{id} (planned)",
        ]}
        plannedFeatures={[
          "Table of active API credentials with key prefix and last-used timestamp",
          "One-time display modal for newly generated API key secrets",
          "Immediate revocation control for retiring or rotated keys",
          "Store integration code snippets and OpenAPI specification link",
        ]}
      />
    </div>
  );
}
