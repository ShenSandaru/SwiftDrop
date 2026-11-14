"use client";

import React from "react";
import { PortalShell, type PortalNavItem } from "@/components/portal/portal-shell";
import { ApprovalGate } from "@/components/merchant/approval-gate";

const MERCHANT_NAV: PortalNavItem[] = [
  { href: "/merchant", label: "Dashboard", exact: true },
  { href: "/merchant/orders", label: "Orders", exact: true },
  { href: "/merchant/api-keys", label: "API Keys", exact: true },
  { href: "/merchant/webhooks", label: "Webhooks", exact: true },
  { href: "/merchant/deliveries", label: "Deliveries", exact: true },
  { href: "/merchant/profile", label: "Business Profile", exact: true },
  { href: "/merchant/status", label: "Application Status", exact: true },
];

export default function MerchantPortalLayout({ children }: { children: React.ReactNode }) {
  return (
    <PortalShell
      portalName="Merchant Portal"
      role="MERCHANT"
      workspaceBadge="Merchant Workspace · WS4 & WS5"
      navItems={MERCHANT_NAV}
    >
      <ApprovalGate>
        {children}
      </ApprovalGate>
    </PortalShell>
  );
}
