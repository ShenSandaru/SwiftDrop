import { PortalPlaceholderCard } from "@/components/portal";

export default function CustomerPortalPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="Customer Delivery Overview"
        description="Book parcel pickups, calculate instant fee estimates, and view shipment tracking history."
        workstream="WS2 — Customer Booking & Pricing Integration"
        contractRef="CUS-2, CUS-3, CUS-5 · Contract §4.2"
        plannedEndpoints={[
          "POST /api/v1/parcels (planned)",
          "POST /api/v1/price-quotes (planned)",
          "GET /api/v1/parcels (planned)",
          "GET /api/v1/parcels/{id}/receipt (planned)",
        ]}
        plannedFeatures={[
          "Interactive delivery booking form with sender and recipient addresses",
          "Dynamic delivery fee calculation based on parcel weight and delivery zone",
          "Personal parcel tracking history with real-time status indicators",
          "Digital delivery receipt generation and printable summary",
        ]}
      />
    </div>
  );
}
