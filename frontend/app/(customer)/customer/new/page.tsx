import React from "react";
import { PortalPlaceholderCard } from "@/components/portal";

export default function BookDeliveryPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="Book a New Parcel Delivery"
        description="Schedule a courier pickup, enter recipient coordinates, and calculate instant delivery pricing."
        workstream="WS2 — Customer Booking & Pricing Integration"
        contractRef="CUS-2, CUS-3 · Contract §4.2"
        plannedEndpoints={[
          "POST /api/v1/parcels (planned)",
          "POST /api/v1/price-quotes (planned)",
        ]}
        plannedFeatures={[
          "Sender pickup address and phone validation (E.164 format)",
          "Recipient delivery address, zone identification, and special instructions",
          "Parcel category selection (SMALL, MEDIUM, LARGE) and weight in grams",
          "Live server-recalculated fee quote breakdown (base fee, weight fee, size surcharge)",
        ]}
      />
    </div>
  );
}
