import React from "react";
import { PortalShell, type PortalNavItem } from "@/components/portal/portal-shell";

// Proof of delivery is captured on each parcel's page, so it has no tab of its own
const COURIER_NAV: PortalNavItem[] = [{ href: "/courier", label: "Today's run" }];

export default function CourierPortalLayout({ children }: { children: React.ReactNode }) {
  return (
    <PortalShell
      portalName="Courier Portal"
      role="COURIER"
      workspaceBadge="Courier Workspace · WS6"
      navItems={COURIER_NAV}
    >
      {children}
    </PortalShell>
  );
}
