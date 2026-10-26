import React from "react";
import { PortalShell, type PortalNavItem } from "@/components/portal/portal-shell";

const CUSTOMER_NAV: PortalNavItem[] = [
  { href: "/customer", label: "Overview", exact: true },
  { href: "/customer/new", label: "Book Delivery", exact: true },
  { href: "/customer/history", label: "Delivery History", exact: true },
];

export default function CustomerPortalLayout({ children }: { children: React.ReactNode }) {
  return (
    <PortalShell
      portalName="Customer Portal"
      role="CUSTOMER"
      workspaceBadge="Customer Workspace · WS2"
      navItems={CUSTOMER_NAV}
    >
      {children}
    </PortalShell>
  );
}
