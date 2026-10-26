"use client";

import React, { useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { sanitizeInternalRedirect } from "@/lib/redirect";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { ShieldAlert } from "lucide-react";

export type BackendRole = "CUSTOMER" | "MERCHANT" | "COURIER" | "DISPATCHER_ADMIN";

export interface RoleGuardProps {
  requiredRole: BackendRole;
  roleLabel?: string;
  children: React.ReactNode;
}

/**
 * Client-side route guard component for role-restricted portals.
 *
 * <p>Security Notice: This client guard manages UI routing convenience only.
 * System security and resource authorization are enforced strictly by the backend REST API
 * (docs/architecture.md §5, ADR 0001).
 */
export function RoleGuard({ requiredRole, roleLabel, children }: RoleGuardProps) {
  const { user, isAuthenticated, isLoading, hasRole, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      const safeDestination = sanitizeInternalRedirect(pathname);
      const loginUrl = `/login?redirect=${encodeURIComponent(safeDestination)}`;
      router.replace(loginUrl);
    }
  }, [isLoading, isAuthenticated, pathname, router]);

  // Loading state while auth context initializes
  if (isLoading) {
    return (
      <main className="container mx-auto flex min-h-[50vh] max-w-4xl items-center justify-center px-4 py-12">
        <div className="flex flex-col items-center gap-3 text-center" role="status" aria-live="polite">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-sm text-muted-foreground animate-pulse">Verifying workspace permissions...</p>
        </div>
      </main>
    );
  }

  // Unauthenticated: redirecting to login
  if (!isAuthenticated) {
    return (
      <main className="container mx-auto flex min-h-[50vh] max-w-4xl items-center justify-center px-4 py-12">
        <div className="flex flex-col items-center gap-2 text-center" role="status" aria-live="polite">
          <p className="text-sm text-muted-foreground">Redirecting to sign in...</p>
        </div>
      </main>
    );
  }

  // Authenticated but lacks the required role (403 Forbidden UI equivalent)
  if (!hasRole(requiredRole)) {
    return (
      <main className="container mx-auto flex min-h-[60vh] max-w-lg items-center justify-center px-4 py-12">
        <Card className="w-full rounded-xl border-destructive/20 bg-card shadow-sm">
          <CardHeader className="text-center space-y-2">
            <div className="mx-auto inline-flex size-12 items-center justify-center rounded-full bg-destructive/10 text-destructive">
              <ShieldAlert className="size-6" aria-hidden="true" />
            </div>
            <div className="inline-block px-2.5 py-0.5 text-xs font-semibold uppercase tracking-wider rounded-full bg-destructive/10 text-destructive border border-destructive/20 mx-auto">
              403 · Access Denied
            </div>
            <CardTitle className="text-xl font-bold text-foreground">Access Restricted</CardTitle>
            <CardDescription className="text-sm text-muted-foreground">
              Your account ({user?.email}) does not have permission to access the {roleLabel || requiredRole} workspace.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-xs text-muted-foreground">
            <div className="rounded-md bg-muted p-3 space-y-1">
              <p>
                <span className="font-semibold text-foreground">Required role:</span>{" "}
                <code className="text-primary font-mono">{requiredRole}</code>
              </p>
              <p>
                <span className="font-semibold text-foreground">Your role(s):</span>{" "}
                {user?.roles?.length ? user.roles.join(", ") : "None"}
              </p>
            </div>
            <p className="italic text-[11px] text-muted-foreground/80 text-center">
              Note: Frontend role guards manage routing presentation. Resource authorization is enforced by the backend API.
            </p>
          </CardContent>
          <CardFooter className="flex flex-col sm:flex-row gap-2">
            <Button variant="outline" className="w-full" onClick={() => router.push("/")}>
              Return to Home
            </Button>
            <Button variant="destructive" className="w-full" onClick={logout}>
              Sign Out
            </Button>
          </CardFooter>
        </Card>
      </main>
    );
  }

  // Authorized
  return <>{children}</>;
}
