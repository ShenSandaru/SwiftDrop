"use client";

import React from "react";
import { usePathname } from "next/navigation";

export interface AuthLayoutProps {
  /** Visual branding panel rendered on the left (desktop) or top (mobile). */
  branding: React.ReactNode;
  /** Authentication form container rendered on the right. */
  children: React.ReactNode;
}

/**
 * Shared split-screen layout for authentication pages.
 * - Desktop (>= 1024px): 2-column split with left visual branding panel and right form panel.
 * - Tablet & Mobile (< 1024px): Single-column stacked layout with compact branding at top.
 * - Right panel incorporates softly blurred Island Teal background gradients and remounts
 *   entrance animations on client-side route transitions using the current pathname as key.
 */
export function AuthLayout({ branding, children }: AuthLayoutProps) {
  const pathname = usePathname();

  return (
    <main className="flex-1 flex flex-col lg:grid lg:grid-cols-12 min-h-screen">
      {/* Left/Top Branding Panel */}
      <aside className="lg:col-span-5 xl:col-span-5 bg-foreground text-background flex flex-col justify-between border-b lg:border-b-0 lg:border-r border-border/10 relative overflow-hidden">
        {branding}
      </aside>

      {/* Right Form Panel */}
      <section className="lg:col-span-7 xl:col-span-7 flex-1 flex items-center justify-center px-4 py-8 sm:px-6 md:px-8 lg:py-12 bg-background relative overflow-hidden">
        {/* Softly blurred Island Teal radial background gradients */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -top-20 -right-20 size-[28rem] rounded-full bg-[radial-gradient(ellipse_at_center,rgba(15,118,110,0.16),transparent_65%)] blur-3xl"
        />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 -left-24 -translate-y-1/2 size-[32rem] rounded-full bg-[radial-gradient(ellipse_at_center,rgba(45,212,191,0.18),transparent_65%)] blur-3xl"
        />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -bottom-24 right-1/4 size-[26rem] rounded-full bg-[radial-gradient(ellipse_at_center,rgba(15,118,110,0.14),transparent_65%)] blur-3xl"
        />

        <div key={pathname} className="w-full max-w-md mx-auto relative z-10 auth-card-rise">
          {children}
        </div>
      </section>
    </main>
  );
}

