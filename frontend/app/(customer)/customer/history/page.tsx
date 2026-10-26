import React from "react";
import { PortalPlaceholderCard } from "@/components/portal";

export default function CustomerHistoryPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="Delivery History & Receipts"
        description="View past and active parcel bookings, track status milestones, and download delivery receipts."
        workstream="WS2 — Customer Booking & Pricing Integration"
        contractRef="CUS-5, D-13 · Contract §4.2"
        plannedEndpoints={[
          "GET /api/v1/parcels (planned)",
          "GET /api/v1/parcels/{id}/receipt (planned)",
        ]}
        plannedFeatures={[
          "Paginated shipment history table conforming to PageResponse contract",
          "Status indicator chips for delivery lifecycle stages",
          "Quick tracking number lookup linking to the public tracking view",
          "Printable delivery receipt viewer with breakdown of paid fees",
        ]}
      />
    </div>
  );
}
