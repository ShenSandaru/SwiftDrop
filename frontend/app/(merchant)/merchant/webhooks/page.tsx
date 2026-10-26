import React from "react";
import { PortalPlaceholderCard } from "@/components/portal";

export default function MerchantWebhooksPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="Webhook Notifications & Endpoints"
        description="Configure HTTPS webhook receivers to receive automated real-time status events for your shipments."
        workstream="WS4 — Webhook Endpoints & Delivery"
        contractRef="MER-3, D-17 · Contract §4.2"
        plannedEndpoints={[
          "PUT /api/v1/merchants/me/webhook-endpoint (planned)",
          "GET /api/v1/merchants/me/webhook-deliveries (planned)",
          "POST /api/v1/merchants/me/webhook-deliveries/{id}/redeliveries (planned)",
        ]}
        plannedFeatures={[
          "HTTPS endpoint configuration with URL validation",
          "HMAC-SHA256 signing secret provisioning for payload verification",
          "Recent webhook delivery history log with response codes and latency",
          "Manual redelivery trigger for debugging failed webhook attempts",
        ]}
      />
    </div>
  );
}
