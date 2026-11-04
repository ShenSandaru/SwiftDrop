"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowLeftRight, ArrowRight, Package } from "lucide-react";
import { cn } from "cn";
import { buttonVariants } from "@/components/ui/button";
import { SIZE_LABELS } from "@/components/delivery/format";
import { useAuth } from "@/lib/auth-context";
import { PARCEL_SIZES, type ParcelSize } from "@/lib/delivery";
import { CITIES, CityWheel, type CityCode } from "./city-wheel";
import { Reveal } from "./reveal";
import "./landing.css";

const SIZE_HINTS: Record<ParcelSize, string> = {
  SMALL: "Documents, phones, books",
  MEDIUM: "Shoes, clothes, a gift box",
  LARGE: "Small appliances, bulk items",
};

/** Decorative barcode bars, the same for the same route so it does not flicker. */
function barcode(seed: string) {
  let hash = 0;
  for (const character of seed) {
    hash = (hash * 31 + character.charCodeAt(0)) >>> 0;
  }
  return Array.from({ length: 34 }, (_, index) => {
    hash = (hash * 1103515245 + 12345) >>> 0;
    return { key: index, width: 1 + (hash % 3), gap: 1 + ((hash >> 3) % 2) };
  });
}

/**
 * "Your parcel's boarding pass": the visitor picks a route and a size and a ticket prints out of
 * a slot. Purely illustrative; the real fare is calculated when the parcel is booked.
 */
export function BoardingPass() {
  const { hasRole } = useAuth();
  const [from, setFrom] = useState<CityCode>("CMB");
  const [to, setTo] = useState<CityCode>("JAF");
  const [size, setSize] = useState<ParcelSize>("SMALL");
  const [swaps, setSwaps] = useState(0);
  const origin = CITIES.find((city) => city.code === from)!;
  const destination = CITIES.find((city) => city.code === to)!;
  const printKey = `${from}-${to}-${size}`;
  const bookingHref = hasRole("CUSTOMER") ? "/customer/new" : "/signup";

  const handleFromChange = (nextFrom: CityCode) => {
    if (nextFrom === to) {
      setTo(from);
      setSwaps((count) => count + 1);
    }
    setFrom(nextFrom);
  };

  const handleToChange = (nextTo: CityCode) => {
    if (nextTo === from) {
      setFrom(to);
      setSwaps((count) => count + 1);
    }
    setTo(nextTo);
  };

  return (
    <section className="border-b border-border py-20 md:py-28">
      <div className="container mx-auto grid max-w-6xl items-center gap-12 px-4 sm:px-6 lg:grid-cols-2">
        <Reveal className="space-y-7">
          <div className="space-y-3">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">Plan a trip</p>
            <h2 className="text-3xl font-extrabold tracking-tight text-foreground sm:text-4xl">
              Every parcel gets a boarding pass.
            </h2>
            <p className="text-muted-foreground">Pick where it starts, where it lands and how big it is. Watch its ticket print.</p>
          </div>

          <div className="grid grid-cols-[1fr_auto_1fr] items-end gap-2 sm:gap-3">
            <CityWheel label="From" value={from} onChange={handleFromChange} />
            <button
              type="button"
              aria-label="Swap from and to"
              onClick={() => {
                setFrom(to);
                setTo(from);
                setSwaps((count) => count + 1);
              }}
              className="mb-[90px] inline-flex size-10 shrink-0 items-center justify-center rounded-full border border-dashed border-primary/40 bg-card text-primary transition-colors hover:bg-accent"
            >
              <ArrowLeftRight className="size-4 transition-transform duration-500" style={{ transform: `rotate(${swaps * 180}deg)` }}
                aria-hidden="true" />
            </button>
            <CityWheel label="To" value={to} onChange={handleToChange} />
          </div>

          <fieldset className="space-y-2">
            <legend className="text-[11px] font-semibold uppercase tracking-[0.2em] text-muted-foreground">Size</legend>
            <div className="grid grid-cols-1 gap-2 min-[400px]:grid-cols-3">
              {PARCEL_SIZES.map((option) => (
                <button
                  key={option}
                  type="button"
                  aria-pressed={size === option}
                  onClick={() => setSize(option)}
                  className={cn(
                    "flex items-center gap-3 rounded-xl border p-3 text-left transition-colors min-[400px]:block",
                    size === option ? "border-primary bg-accent" : "border-border bg-card hover:border-primary/50",
                  )}
                >
                  <Package
                    className={cn("shrink-0 text-primary", option === "SMALL" ? "size-4" : option === "MEDIUM" ? "size-5" : "size-6")}
                    aria-hidden="true"
                  />
                  <div>
                    <span className="block text-sm font-bold text-foreground min-[400px]:mt-1">{SIZE_LABELS[option]}</span>
                    <span className="block text-[11px] leading-tight text-muted-foreground">{SIZE_HINTS[option]}</span>
                  </div>
                </button>
              ))}
            </div>
          </fieldset>
        </Reveal>

        {/* The printer and its ticket */}
        <Reveal delay={0.1} className="mx-auto w-full max-w-md">
          <div aria-hidden="true" className="relative z-10 h-5 rounded-full bg-secondary shadow-lg">
            <span className="absolute inset-x-6 top-1/2 h-1 -translate-y-1/2 rounded-full bg-black/60" />
            <span className={cn("landing-slot-light", "absolute right-3 top-1/2 size-1.5 -translate-y-1/2 rounded-full bg-primary")} />
          </div>
          <div className="-mt-2.5 px-2 sm:px-4">
            <p className="sr-only" aria-live="polite">
              Ticket: {origin.name} to {destination.name}, {SIZE_LABELS[size]}.
            </p>
            {/* Remounted by key, so every new choice prints a fresh ticket */}
            <article key={printKey} className={cn("landing-print", "relative rounded-b-2xl border border-t-0 border-border bg-card shadow-xl")}>
              <div className="space-y-5 p-5 pt-7 sm:p-6 sm:pt-8">
                <div className="flex items-center justify-between text-[10px] font-semibold uppercase tracking-[0.25em] text-muted-foreground">
                  <span>SwiftDrop · Parcel pass</span>
                  <span className="text-primary">LK</span>
                </div>

                <div className="flex items-end justify-between gap-3">
                  <div>
                    <p className="text-4xl font-extrabold tracking-tight text-foreground sm:text-5xl">{origin.code}</p>
                    <p className="text-xs text-muted-foreground">{origin.name}</p>
                  </div>
                  <div className="relative mb-6 h-4 flex-1" aria-hidden="true">
                    <span className="absolute inset-x-0 top-1/2 border-t-2 border-dashed border-primary/40" />
                    <span className={cn("landing-glide", "absolute top-1/2 inline-flex size-6 -translate-y-1/2 items-center justify-center rounded-md bg-primary text-primary-foreground")}>
                      <Package className="size-3.5" />
                    </span>
                  </div>
                  <div className="text-right">
                    <p className="text-4xl font-extrabold tracking-tight text-foreground sm:text-5xl">{destination.code}</p>
                    <p className="text-xs text-muted-foreground">{destination.name}</p>
                  </div>
                </div>

                <dl className="grid grid-cols-3 gap-3 text-sm">
                  <div>
                    <dt className="text-[10px] font-semibold uppercase tracking-[0.2em] text-muted-foreground">Size</dt>
                    <dd className="font-bold text-foreground">{SIZE_LABELS[size]}</dd>
                  </div>
                  <div>
                    <dt className="text-[10px] font-semibold uppercase tracking-[0.2em] text-muted-foreground">Weight</dt>
                    <dd className="font-bold text-foreground">Up to 30 kg</dd>
                  </div>
                  <div>
                    <dt className="text-[10px] font-semibold uppercase tracking-[0.2em] text-muted-foreground">Fare</dt>
                    <dd className="font-bold text-primary">At booking</dd>
                  </div>
                </dl>
              </div>

              {/* Perforation with notches */}
              <div className="relative" aria-hidden="true">
                <span className="absolute -left-3 top-1/2 size-6 -translate-y-1/2 rounded-full border border-border bg-background [clip-path:inset(0_0_0_50%)]" />
                <span className="absolute -right-3 top-1/2 size-6 -translate-y-1/2 rounded-full border border-border bg-background [clip-path:inset(0_50%_0_0)]" />
                <div className="mx-5 border-t-2 border-dashed border-border sm:mx-6" />
              </div>

              <div className="flex items-center justify-between gap-3 p-5 sm:gap-4 sm:p-6">
                <div aria-hidden="true" className="flex h-10 min-w-0 shrink items-stretch overflow-hidden">
                  {barcode(printKey).map((bar) => (
                    <span key={bar.key} className="shrink-0 bg-foreground/80" style={{ width: bar.width, marginRight: bar.gap }} />
                  ))}
                </div>
                <Link href={bookingHref} className={cn(buttonVariants({ size: "sm" }), "shrink-0 font-semibold")}>
                  Book this route <ArrowRight aria-hidden="true" />
                </Link>
              </div>
            </article>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
