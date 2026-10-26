import React from "react";
import { PortalShell, DISPATCHER_ADMIN_NAV } from "@/components/portal/portal-shell";

export default function AdminPortalLayout({ children }: { children: React.ReactNode }) {
  return (
    <PortalShell
      portalName="Dispatcher & Admin Portal"
      role="DISPATCHER_ADMIN"
      workspaceBadge="Dispatcher & Admin Workspace · WS1, WS7 & WS8"
      navItems={DISPATCHER_ADMIN_NAV}
    >
      {children}
    </PortalShell>
  );
}
