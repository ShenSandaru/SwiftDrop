import React from "react";
import { PortalShell, ADMIN_NAV } from "@/components/portal/portal-shell";

export default function AdminPortalLayout({ children }: { children: React.ReactNode }) {
  return (
    <PortalShell
      portalName="Admin Portal"
      role="ADMIN"
      workspaceBadge="Admin Workspace · System Administration"
      navItems={ADMIN_NAV}
    >
      {children}
    </PortalShell>
  );
}
