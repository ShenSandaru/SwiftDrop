"use client";

import React from "react";
import { PortalShell, ADMIN_NAV, DISPATCHER_NAV } from "@/components/portal/portal-shell";
import { useAuth } from "@/lib/auth-context";

export default function DispatchPortalLayout({ children }: { children: React.ReactNode }) {
  const { hasRole } = useAuth();
  const isAdmin = hasRole("ADMIN");

  return (
    <PortalShell
      portalName={isAdmin ? "Admin Portal" : "Dispatcher Portal"}
      allowedRoles={["ADMIN", "DISPATCHER"]}
      workspaceBadge={isAdmin ? "Admin Workspace · Dispatch Operations" : "Dispatch Workspace · Fleet Operations"}
      navItems={isAdmin ? ADMIN_NAV : DISPATCHER_NAV}
    >
      {children}
    </PortalShell>
  );
}
