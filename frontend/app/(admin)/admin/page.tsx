import { PortalPlaceholderCard } from "@/components/portal";

export default function AdminPortalPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="Administration & System Configuration"
        description="User access control, staff onboarding, delivery zone setup, and logistics performance reports."
        workstream="WS1 & WS8 — Administration, Staffing & Reports"
        contractRef="ADM-3, ADM-4 · Contract §4.2"
        plannedEndpoints={[
          "GET /api/v1/users (planned)",
          "POST /api/v1/staff-users (planned)",
          "PATCH /api/v1/users/{id} (planned)",
          "GET/POST /api/v1/pricing-rules (planned)",
          "GET /api/v1/reports/daily-deliveries (planned)",
        ]}
        plannedFeatures={[
          "User directory with role filtering, staff creation, and account enablement toggles",
          "Delivery zone mapping and weight-tier pricing rule matrix configuration",
          "Daily delivery volume and delivery outcome performance analytics",
          "Audit trail and operational system health metrics",
        ]}
      />
    </div>
  );
}
