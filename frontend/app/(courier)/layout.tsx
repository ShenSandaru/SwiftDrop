import React from "react";
import { PortalShell, type PortalNavItem } from "@/components/portal/portal-shell";

const COURIER_NAV: PortalNavItem[] = [
  { href: "/courier", label: "Courier Runs", exact: true },
  { href: "/courier/proof", label: "Proof of Delivery", exact: true },
];

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
