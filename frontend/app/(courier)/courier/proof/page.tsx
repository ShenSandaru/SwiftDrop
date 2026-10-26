import React from "react";
import { PortalPlaceholderCard } from "@/components/portal";

export default function CourierProofPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="Proof of Delivery Capture"
        description="Record recipient confirmation, upload delivery proof photos, and finalize completed stops."
        workstream="WS6 — Courier Operations & Delivery Proof"
        contractRef="COU-3, D-12 · Contract §4.2"
        plannedEndpoints={[
          "PUT /api/v1/parcels/{id}/proof (planned)",
          "GET /api/v1/parcels/{id}/proof/photo (planned)",
        ]}
        plannedFeatures={[
          "Mobile-optimized photo capture for recipient doorstep or parcel handover",
          "Recipient name and signature verification inputs",
          "Automatic transition of parcel status to DELIVERED upon valid proof",
          "Client-side image compression prior to multipart backend upload",
        ]}
      />
    </div>
  );
}
