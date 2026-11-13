import React from "react";

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
 * Sized with min-h-[calc(100vh-3.5rem)] to complement the sticky 3.5rem NavHeader without overflow.
 */
export function AuthLayout({ branding, children }: AuthLayoutProps) {
  return (
    <main className="flex-1 flex flex-col lg:grid lg:grid-cols-12 min-h-screen">
      {/* Left/Top Branding Panel */}
      <aside className="lg:col-span-5 xl:col-span-5 bg-foreground text-background flex flex-col justify-between border-b lg:border-b-0 lg:border-r border-border/10 relative overflow-hidden">
        {branding}
      </aside>

      {/* Right Form Panel */}
      <section className="lg:col-span-7 xl:col-span-7 flex-1 flex items-center justify-center px-3 py-6 sm:px-6 md:px-8 lg:py-12 bg-background">
        <div className="w-full max-w-md mx-auto">{children}</div>
      </section>
    </main>
  );
}

