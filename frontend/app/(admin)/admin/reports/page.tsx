import React from "react";
import { PortalPlaceholderCard } from "@/components/portal";

export default function AdminReportsPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="Operational Analytics & Reports"
        description="Analyze daily delivery volumes, courier fulfillment performance, and shipment outcome rates."
        workstream="WS8 — Operational Reporting"
        contractRef="ADM-4, D-08 · Contract §4.2"
        plannedEndpoints={[
          "GET /api/v1/reports/daily-deliveries?from&to (planned)",
          "GET /api/v1/reports/delivery-outcomes?from&to (planned)",
        ]}
        plannedFeatures={[
          "Daily parcel volume chart with date range filtering (from & to parameters)",
          "Delivery success vs failure outcome breakdown and failure reason distribution",
          "Courier completion rate leaderboard and turnaround time metrics",
          "CSV export capabilities for operational and financial auditing",
        ]}
      />
    </div>
  );
}
