import { PortalPlaceholderCard } from "@/components/portal";

export default function CourierPortalPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="Courier Run Sheet & Deliveries"
        description="Assigned parcel deliveries, sequence-optimized stops, and digital proof-of-delivery capture."
        workstream="WS6 — Courier Operations & Delivery Proof"
        contractRef="COU-2, COU-3, COU-4 · Contract §4.2"
        plannedEndpoints={[
          "GET /api/v1/courier/parcels (planned)",
          "POST /api/v1/parcels/{id}/status-changes (planned)",
          "PUT /api/v1/parcels/{id}/proof (planned)",
        ]}
        plannedFeatures={[
          "Daily assigned delivery stops with customer contact information",
          "Step-by-step parcel status advancement (PICKED_UP, IN_TRANSIT, DELIVERED)",
          "Camera-enabled proof-of-delivery photo upload and recipient signature",
          "Delivery failure recording with standard exception reasons (e.g. customer unavailable)",
        ]}
      />
    </div>
  );
}
