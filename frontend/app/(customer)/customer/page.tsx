"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowRight, MapPinned, PackagePlus, Search } from "lucide-react";
import { cn } from "cn";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { MiniRoute } from "@/components/delivery/mini-route";
import { StatusBadge } from "@/components/delivery/status-badge";
import { formatDate, formatMoney } from "@/components/delivery/format";
import { formatApiError } from "@/lib/api";
import { isFinalStatus, listParcels, type ParcelListItem } from "@/lib/delivery";

export default function CustomerOverviewPage() {
  const router = useRouter();
  const [recent, setRecent] = useState<ParcelListItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [code, setCode] = useState("");

  useEffect(() => {
    listParcels(0, 6)
      .then((page) => setRecent(page.items))
      .catch((failure) => setError(formatApiError(failure)));
  }, []);

  const active = recent?.filter((parcel) => !isFinalStatus(parcel.status)) ?? [];
  const finished = recent?.filter((parcel) => isFinalStatus(parcel.status)) ?? [];

  return (
    <div className="space-y-8">
      {/* Hero: the island route */}
      <section className="relative overflow-hidden rounded-3xl bg-primary px-6 py-8 text-primary-foreground sm:px-10 sm:py-10">
        <svg
          className="pointer-events-none absolute -right-10 -top-6 h-56 w-80 opacity-25"
          viewBox="0 0 320 220"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="M10 190 C 80 120, 120 200, 180 120 S 260 40, 310 20"
            stroke="currentColor"
            strokeWidth="3"
            strokeDasharray="10 10"
            strokeLinecap="round"
          />
          <circle cx="10" cy="190" r="7" fill="currentColor" />
          <circle cx="180" cy="120" r="5" fill="currentColor" />
          <circle cx="310" cy="20" r="9" fill="currentColor" />
        </svg>
        <div className="relative max-w-xl space-y-4">
          <p className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.2em] opacity-80">
            <MapPinned className="size-4" aria-hidden="true" /> Island-wide delivery
          </p>
          <h2 className="text-3xl font-extrabold leading-tight sm:text-4xl">Send it anywhere on the island.</h2>
          <p className="text-sm opacity-90 sm:text-base">
            See the price before you book, then watch your parcel travel its route live.
          </p>
          <div className="flex flex-col gap-3 pt-2 sm:flex-row">
            <Link
              href="/customer/new"
              className={cn(
                buttonVariants({ size: "lg" }),
                "bg-primary-foreground text-primary hover:bg-primary-foreground/90",
              )}
            >
              <PackagePlus aria-hidden="true" /> Book a delivery
            </Link>
            <form
              className="flex flex-1 gap-2"
              onSubmit={(event) => {
                event.preventDefault();
                if (code.trim()) {
                  router.push(`/track?code=${encodeURIComponent(code.trim())}`);
                }
              }}
            >
              <label htmlFor="quick-track" className="sr-only">
                Tracking number
              </label>
              <Input
                id="quick-track"
                value={code}
                onChange={(event) => setCode(event.target.value)}
                placeholder="Track a number, e.g. SD7K3M9Q2XWB"
                className="h-9 border-primary-foreground/40 bg-primary-foreground/10 text-primary-foreground placeholder:text-primary-foreground/70"
              />
              <Button type="submit" size="lg" variant="outline" className="border-primary-foreground/40 bg-transparent text-primary-foreground hover:bg-primary-foreground/10">
                <Search aria-hidden="true" />
                <span className="sr-only">Track</span>
              </Button>
            </form>
          </div>
        </div>
      </section>

      {error && <p className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">{error}</p>}

      <section className="space-y-3">
        <div className="flex items-end justify-between">
          <h2 className="text-lg font-bold text-foreground">On the road</h2>
          <Link href="/customer/history" className="text-sm font-semibold text-primary hover:underline">
            All deliveries
          </Link>
        </div>
        {recent === null && !error && <SkeletonRows />}
        {recent !== null && active.length === 0 && (
          <div className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
            Nothing on the road right now.{" "}
            <Link href="/customer/new" className="font-semibold text-primary hover:underline">
              Book your first delivery
            </Link>
          </div>
        )}
        <div className="grid gap-3 sm:grid-cols-2">
          {active.map((parcel) => (
            <ParcelStrip key={parcel.id} parcel={parcel} />
          ))}
        </div>
      </section>

      {finished.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-lg font-bold text-foreground">Recently finished</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            {finished.map((parcel) => (
              <ParcelStrip key={parcel.id} parcel={parcel} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function ParcelStrip({ parcel }: { parcel: ParcelListItem }) {
  return (
    <Link
      href={`/customer/parcels/${parcel.id}`}
      className="group block space-y-3 rounded-2xl border border-border bg-card p-4 transition-all hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-md"
    >
      <div className="flex items-center justify-between gap-2">
        <span className="font-mono text-xs font-semibold tracking-[0.15em] text-primary">{parcel.trackingNumber}</span>
        <StatusBadge status={parcel.status} />
      </div>
      <p className="flex items-center gap-2 text-sm font-bold text-foreground">
        <span className="truncate capitalize">{parcel.pickupCity}</span>
        <ArrowRight className="size-4 shrink-0 text-primary" aria-hidden="true" />
        <span className="truncate capitalize">{parcel.dropoffCity}</span>
      </p>
      <div className="flex items-center justify-between gap-2">
        <MiniRoute status={parcel.status} />
        <span className="text-xs text-muted-foreground">
          {formatMoney(parcel.fee)} · {formatDate(parcel.createdAt)}
        </span>
      </div>
    </Link>
  );
}

function SkeletonRows() {
  return (
    <div className="grid gap-3 sm:grid-cols-2" aria-hidden="true">
      {[0, 1].map((index) => (
        <div key={index} className="h-28 animate-pulse rounded-2xl border border-border bg-muted" />
      ))}
    </div>
  );
}
