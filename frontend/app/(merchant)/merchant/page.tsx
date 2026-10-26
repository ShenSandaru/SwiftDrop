import { PortalPlaceholderCard } from "@/components/portal";

export default function MerchantPortalPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="Merchant Logistics Dashboard"
        description="Bulk shipment intake, cash-on-delivery settlements, and e-commerce API integration."
        workstream="WS4 & WS5 — Merchant Registration, Orders & Webhooks"
        contractRef="MER-1, MER-2 · Contract §4.2"
        plannedEndpoints={[
          "POST /api/v1/merchant-orders (planned)",
          "GET /api/v1/merchants/me/orders (planned)",
          "GET/POST /api/v1/merchants/me/api-keys (planned)",
          "PUT /api/v1/merchants/me/webhook-endpoint (planned)",
        ]}
        plannedFeatures={[
          "Merchant dashboard showing daily shipment volumes and delivery success rates",
          "Bulk CSV parcel manifest import and automatic label generation",
          "Scoped API key generation and secret management for external stores",
          "HMAC-SHA256 signed webhook endpoint configuration for delivery updates",
        ]}
      />
    </div>
  );
}
