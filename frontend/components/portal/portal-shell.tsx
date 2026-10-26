"use client";

import React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { RoleGuard, type BackendRole } from "./role-guard";

export interface PortalNavItem {
  href: string;
  label: string;
  badge?: string;
  exact?: boolean;
}

export const DISPATCHER_ADMIN_NAV: PortalNavItem[] = [
  { href: "/dispatch", label: "Dispatch Fleet", exact: true },
  { href: "/admin", label: "Overview", exact: true },
  { href: "/admin/users", label: "User Directory", exact: true },
  { href: "/admin/pricing", label: "Pricing & Zones", exact: true },
  { href: "/admin/reports", label: "Reports", exact: true },
];

export interface PortalShellProps {
  portalName: string;
  role: BackendRole;
  workspaceBadge: string;
  navItems: PortalNavItem[];
  children: React.ReactNode;
}

/**
 * Reusable layout shell for role-based workspaces.
 * Encapsulates role guarding, workspace badges, navigation tabs, and consistent layout spacing.
 */
export function PortalShell({
  portalName,
  role,
  workspaceBadge,
  navItems,
  children,
}: PortalShellProps) {
  const pathname = usePathname();

  return (
    <RoleGuard requiredRole={role} roleLabel={portalName}>
      <main className="container mx-auto flex-1 px-4 py-8 max-w-5xl space-y-6">
        {/* Workspace Header */}
        <div className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-accent px-3 py-1 text-xs font-semibold text-primary">
              <span className="size-1.5 rounded-full bg-primary" aria-hidden="true" />
              {workspaceBadge}
            </div>
            <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-muted text-muted-foreground border border-border uppercase tracking-wider">
              Role: {role}
            </span>
          </div>

          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
            {portalName}
          </h1>

          {/* Navigation Tabs */}
          {navItems.length > 0 && (
            <nav
              className="flex items-center gap-1 border-b border-border overflow-x-auto pt-2"
              aria-label={`${portalName} navigation`}
            >
              {navItems.map((item) => {
                const isActive = item.exact
                  ? pathname === item.href
                  : pathname === item.href || pathname.startsWith(item.href + "/");

                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={`inline-flex items-center gap-2 px-3.5 py-2 text-sm font-medium border-b-2 transition-colors whitespace-nowrap -mb-px ${
                      isActive
                        ? "border-primary text-foreground font-semibold"
                        : "border-transparent text-muted-foreground hover:text-foreground hover:border-border"
                    }`}
                    aria-current={isActive ? "page" : undefined}
                  >
                    {item.label}
                    {item.badge && (
                      <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-accent text-primary">
                        {item.badge}
                      </span>
                    )}
                  </Link>
                );
              })}
            </nav>
          )}
        </div>

        {/* Portal Workspace Body */}
        <div className="pt-2">{children}</div>
      </main>
    </RoleGuard>
  );
}
