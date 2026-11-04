"use client";

import Link from "next/link";
import type { CSSProperties } from "react";
import { ArrowRight, Camera, Radio, Search, Ticket as TicketIcon } from "lucide-react";
import { cn } from "cn";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Ticket } from "@/components/delivery/ticket";
import { useAuth } from "@/lib/auth-context";
import { IslandMap } from "./island-map";
import "./landing.css";

const delay = (seconds: number) => ({ "--delay": `${seconds}s` }) as CSSProperties;

const PROMISES = [
  { icon: TicketIcon, text: "Fare before you book" },
  { icon: Radio, text: "Live, without refreshing" },
  { icon: Camera, text: "Photo proof at the door" },
];

export function Hero() {
  const { hasRole } = useAuth();
  const bookingHref = hasRole("CUSTOMER") ? "/customer/new" : "/signup";

  return (
    <section className="relative overflow-hidden border-b border-border">
      {/* Dotted chart paper with a teal glow behind the map */}
      <div aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle,var(--border)_1px,transparent_1px)] [background-size:22px_22px] [mask-image:linear-gradient(to_bottom,black,transparent_85%)]" />
      <div aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_45%_55%_at_78%_45%,color-mix(in_oklab,var(--primary)_12%,transparent),transparent)]" />

      <div className="container relative mx-auto grid max-w-6xl items-center gap-12 px-4 py-14 sm:px-6 md:py-20 lg:grid-cols-[1.1fr_0.9fr]">
        <div className="space-y-7">
          <p className={cn("landing-rise", "inline-flex items-center gap-2 rounded-full border border-primary/20 bg-accent px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-primary")}
            style={delay(0)}>
            <span className="size-1.5 rounded-full bg-primary" aria-hidden="true" /> Parcel delivery across Sri Lanka
          </p>

          <h1 className={cn("landing-rise", "text-4xl font-extrabold leading-[1.05] tracking-tight text-foreground sm:text-5xl lg:text-6xl")}
            style={delay(0.1)}>
            From your door to{" "}
            <span className="relative inline-block text-primary">
              anywhere
              <svg aria-hidden="true" viewBox="0 0 300 24" preserveAspectRatio="none"
                className="absolute -bottom-2 left-0 h-3 w-full overflow-visible">
                <path d="M4 16C60 6 120 4 170 9S260 18 296 8" className={cn("fill-none stroke-primary/60", "landing-underline")}
                  strokeWidth="6" strokeLinecap="round" />
              </svg>
            </span>{" "}
            on the island.
          </h1>

          <p className={cn("landing-rise", "max-w-xl text-lg text-muted-foreground")} style={delay(0.2)}>
            <span className="font-semibold text-foreground">Book in a minute. Track every stop. Delivered with proof.</span>{" "}
            See the fare before you confirm and follow your parcel live across the island, right to the
            door.
          </p>

          <div className={cn("landing-rise", "flex flex-wrap gap-3")} style={delay(0.3)}>
            <Link href={bookingHref} className={cn(buttonVariants({ size: "lg" }), "h-12 px-6 text-base font-semibold")}>
              Send a parcel <ArrowRight aria-hidden="true" />
            </Link>
            <Link href="#how-it-moves" className={cn(buttonVariants({ variant: "outline", size: "lg" }), "h-12 px-6 text-base")}>
              See how it moves
            </Link>
          </div>

          {/* Tracking, styled as the ticket the customer receives */}
          <div className={cn("landing-rise", "max-w-md")} style={delay(0.4)}>
            <Ticket
              stub={
                <ul className="flex flex-wrap gap-x-4 gap-y-2 text-xs font-medium text-muted-foreground">
                  {PROMISES.map(({ icon: Icon, text }) => (
                    <li key={text} className="inline-flex items-center gap-1.5">
                      <Icon className="size-3.5 text-primary" aria-hidden="true" /> {text}
                    </li>
                  ))}
                </ul>
              }
            >
              <form action="/track" method="GET" className="space-y-2">
                <label htmlFor="hero-track-code" className="text-[11px] font-semibold uppercase tracking-[0.2em] text-muted-foreground">
                  Already sent one? Track it
                </label>
                <div className="flex gap-2">
                  <Input id="hero-track-code" name="code" placeholder="SD7K3M9Q2XWB" autoComplete="off"
                    autoCapitalize="characters" spellCheck={false} required
                    className="h-11 min-w-0 font-mono tracking-[0.15em] uppercase" />
                  <Button type="submit" size="lg" className="h-11 shrink-0 px-4">
                    <Search aria-hidden="true" /> Track
                  </Button>
                </div>
              </form>
            </Ticket>
          </div>
        </div>

        <div className="landing-rise" style={delay(0.25)}>
          <IslandMap />
        </div>
      </div>
    </section>
  );
}
