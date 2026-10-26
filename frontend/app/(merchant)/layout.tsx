import React from "react";
import { PortalShell, type PortalNavItem } from "@/components/portal/portal-shell";

const MERCHANT_NAV: PortalNavItem[] = [
  { href: "/merchant", label: "Dashboard", exact: true },
  { href: "/merchant/orders", label: "Orders", exact: true },
  { href: "/merchant/api-keys", label: "API Keys", exact: true },
  { href: "/merchant/webhooks", label: "Webhooks", exact: true },
];

export default function MerchantPortalLayout({ children }: { children: React.ReactNode }) {
  return (
    <PortalShell
      portalName="Merchant Portal"
      role="MERCHANT"
      workspaceBadge="Merchant Workspace · WS4 & WS5"
      navItems={MERCHANT_NAV}
    >
      {children}
    </PortalShell>
  );
}
