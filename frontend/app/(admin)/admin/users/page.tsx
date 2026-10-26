import React from "react";
import { PortalPlaceholderCard } from "@/components/portal";

export default function AdminUsersPage() {
  return (
    <div className="space-y-6">
      <PortalPlaceholderCard
        title="User & Staff Management"
        description="Onboard logistics staff, filter user directories by role, and manage account authorization status."
        workstream="WS1 — Identity & Staff Onboarding"
        contractRef="ADM-3, D-04 · Contract §4.2"
        plannedEndpoints={[
          "GET /api/v1/users (planned)",
          "POST /api/v1/staff-users (planned)",
          "PATCH /api/v1/users/{id} (planned)",
        ]}
        plannedFeatures={[
          "Paginated user table with role filter (CUSTOMER, MERCHANT, COURIER, DISPATCHER_ADMIN)",
          "Staff account creation form restricted to COURIER and DISPATCHER_ADMIN roles",
          "Enable/disable user access switch with self-disablement prevention for current admin",
          "Account activity indicators and registration date filtering",
        ]}
      />
    </div>
  );
}
