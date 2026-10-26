"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import { Button, buttonVariants } from "@/components/ui/button";

export function NavHeader() {
  const { user, isAuthenticated, isLoading, logout, hasRole } = useAuth();

  return (
    <header className="border-b border-sidebar-border bg-sidebar text-sidebar-foreground sticky top-0 z-40">
      <div className="container mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6">
        <div className="flex items-center gap-6">
          <Link href="/" className="flex items-center gap-2 font-semibold text-lg tracking-tight">
            <span className="size-2 rounded-full bg-primary inline-block" />
            <span className="text-sidebar-foreground font-bold">
              Swift<span className="text-primary">Drop</span>
            </span>
          </Link>

          <nav className="hidden md:flex items-center gap-4 text-sm font-medium text-sidebar-foreground/75">
            <Link href="/track" className="hover:text-sidebar-foreground transition-colors">
              Track Parcel
            </Link>
            {hasRole("CUSTOMER") && (
              <Link href="/customer" className="hover:text-sidebar-foreground transition-colors">
                My Deliveries
              </Link>
            )}
            {hasRole("MERCHANT") && (
              <Link href="/merchant" className="hover:text-sidebar-foreground transition-colors">
                Merchant Portal
              </Link>
            )}
            {hasRole("COURIER") && (
              <Link href="/courier" className="hover:text-sidebar-foreground transition-colors">
                Courier Tasks
              </Link>
            )}
            {hasRole("DISPATCHER_ADMIN") && (
              <>
                <Link href="/dispatch" className="hover:text-sidebar-foreground transition-colors">
                  Dispatch
                </Link>
                <Link href="/admin" className="hover:text-sidebar-foreground transition-colors">
                  Admin
                </Link>
              </>
            )}
          </nav>
        </div>

        <div className="flex items-center gap-3">
          {isLoading ? (
            <div className="h-8 w-20 animate-pulse rounded bg-sidebar-accent" />
          ) : isAuthenticated && user ? (
            <div className="flex items-center gap-3">
              <span className="text-xs text-sidebar-foreground/70 hidden sm:inline">
                {user.fullName || user.email}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={logout}
                className="border-sidebar-border bg-sidebar-accent text-sidebar-foreground hover:bg-sidebar-accent/80 hover:text-sidebar-foreground"
              >
                Sign Out
              </Button>
            </div>
          ) : (
            <div className="flex items-center gap-1.5 sm:gap-2">
              <Link
                href="/track"
                className={
                  buttonVariants({ variant: "ghost", size: "sm" }) +
                  " text-sidebar-foreground/80 hover:text-sidebar-foreground hover:bg-sidebar-accent hidden sm:inline-flex"
                }
              >
                Track
              </Link>
              <Link
                href="/login"
                className={
                  buttonVariants({ variant: "ghost", size: "sm" }) +
                  " text-sidebar-foreground/80 hover:text-sidebar-foreground hover:bg-sidebar-accent"
                }
              >
                Sign In
              </Link>
              <Link
                href="/signup"
                className={
                  buttonVariants({ variant: "default", size: "sm" }) +
                  " font-semibold shadow-xs"
                }
              >
                Register
              </Link>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
