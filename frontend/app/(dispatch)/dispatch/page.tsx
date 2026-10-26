import { PortalPlaceholderCard } from "@/components/portal";

export default function DispatchPortalPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="Fleet Dispatch & Parcel Assignment"
        description="Monitor active courier workloads, assign unassigned parcels, and resolve delivery exceptions."
        workstream="WS7 — Dispatcher Assignment & Workload"
        contractRef="ADM-1, ADM-2 · Contract §4.2"
        plannedEndpoints={[
          "GET /api/v1/unassigned-parcels (planned)",
          "POST /api/v1/parcel-assignments (planned)",
          "GET /api/v1/courier-workloads (planned)",
          "GET /api/v1/couriers (planned)",
        ]}
        plannedFeatures={[
          "Live queue of unassigned parcels requiring courier dispatch",
          "Courier capacity and active workload overview per delivery zone",
          "One-click parcel assignment and reassignment workflow",
          "Fleet status monitoring and bottleneck alerts",
        ]}
      />
    </div>
  );
}
