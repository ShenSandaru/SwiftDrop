"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import { Button, buttonVariants } from "@/components/ui/button";

export function NavHeader() {
  const { user, isAuthenticated, isLoading, logout, hasRole } = useAuth();

  return (
    <header className="border-b bg-card/50 backdrop-blur-sm sticky top-0 z-40">
      <div className="container mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6">
        <div className="flex items-center gap-6">
          <Link href="/" className="flex items-center gap-2 font-semibold text-lg tracking-tight">
            <span className="text-primary font-bold">SwiftDrop</span>
          </Link>

          <nav className="hidden md:flex items-center gap-4 text-sm font-medium text-muted-foreground">
            <Link href="/track" className="hover:text-foreground transition-colors">
              Track Parcel
            </Link>
            {hasRole("CUSTOMER") && (
              <Link href="/customer" className="hover:text-foreground transition-colors">
                My Deliveries
              </Link>
            )}
            {hasRole("MERCHANT") && (
              <Link href="/merchant" className="hover:text-foreground transition-colors">
                Merchant Portal
              </Link>
            )}
            {hasRole("COURIER") && (
              <Link href="/courier" className="hover:text-foreground transition-colors">
                Courier Tasks
              </Link>
            )}
            {hasRole("DISPATCHER_ADMIN") && (
              <>
                <Link href="/dispatch" className="hover:text-foreground transition-colors">
                  Dispatch
                </Link>
                <Link href="/admin" className="hover:text-foreground transition-colors">
                  Admin
                </Link>
              </>
            )}
          </nav>
        </div>

        <div className="flex items-center gap-3">
          {isLoading ? (
            <div className="h-8 w-20 animate-pulse rounded bg-muted" />
          ) : isAuthenticated && user ? (
            <div className="flex items-center gap-3">
              <span className="text-xs text-muted-foreground hidden sm:inline">
                {user.fullName || user.email}
              </span>
              <Button variant="outline" size="sm" onClick={logout}>
                Sign Out
              </Button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Link
                href="/track"
                className={buttonVariants({ variant: "ghost", size: "sm" })}
              >
                Track
              </Link>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
