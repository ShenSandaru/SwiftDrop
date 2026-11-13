"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { cn } from "cn";
import { buttonVariants } from "@/components/ui/button";
import { useAuth } from "@/lib/auth-context";
import { Reveal } from "./reveal";
import "./landing.css";

/** The call to action band and the page footer. */
export function Closing() {
  const { hasRole } = useAuth();
  const bookingHref = hasRole("CUSTOMER") ? "/customer/new" : "/signup/customer";

  return (
    <>
      <section className="px-4 py-20 sm:px-6 md:py-24">
        <Reveal className="relative mx-auto max-w-5xl overflow-hidden rounded-[2rem] bg-secondary px-6 py-14 text-center text-secondary-foreground sm:px-12">
          {/* A route drawn across the band */}
          <svg aria-hidden="true" viewBox="0 0 800 200" preserveAspectRatio="none"
            className="pointer-events-none absolute inset-0 size-full opacity-60">
            <path d="M-20 150C120 60 220 190 380 110S640 30 820 80" className={cn("fill-none stroke-primary", "landing-network")}
              strokeWidth="2" />
          </svg>
          <div aria-hidden="true" className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_10%_0%,color-mix(in_oklab,var(--primary)_35%,transparent),transparent_45%)]" />
          <div className="relative space-y-5">
            <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl">Your next parcel is one ticket away.</h2>
            <p className="mx-auto max-w-xl opacity-80">
              Create a free account, book a pickup and share the tracking number. We take it from there.
            </p>
            <div className="flex flex-wrap justify-center gap-3">
              <Link href={bookingHref} className={cn(buttonVariants({ size: "lg" }), "h-12 px-6 text-base font-semibold")}>
                Send a parcel <ArrowRight aria-hidden="true" />
              </Link>
              <Link href="/track"
                className={cn(buttonVariants({ variant: "outline", size: "lg" }), "h-12 border-secondary-foreground/30 bg-transparent px-6 text-base text-secondary-foreground hover:bg-secondary-foreground/10 hover:text-secondary-foreground")}>
                Track a parcel
              </Link>
            </div>
          </div>
        </Reveal>
      </section>

      <footer className="border-t border-border">
        <div className="container mx-auto flex max-w-6xl flex-col gap-4 px-4 py-8 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <p>
            <span className="font-bold text-foreground">Swift<span className="text-primary">Drop</span></span>
            {" "}· Parcel delivery across Sri Lanka
          </p>
          <nav aria-label="Footer" className="flex flex-wrap gap-x-5 gap-y-2">
            <Link href="/track" className="hover:text-foreground">Track a parcel</Link>
            <Link href="/login" className="hover:text-foreground">Sign in</Link>
            <Link href="/signup/customer" className="hover:text-foreground">Register</Link>
            <Link href="/signup/merchant" className="hover:text-foreground">For Businesses</Link>
            {/* Temporary: quick links to every portal during development */}
            <Link href="/portals" className="rounded border border-dashed border-border px-1.5 hover:text-foreground">
              Developer portals
            </Link>
          </nav>
        </div>
      </footer>
    </>
  );
}
