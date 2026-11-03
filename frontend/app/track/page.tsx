"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { CheckCircle2, Loader2, MapPinned, PackageSearch, Search, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { RouteHeading } from "@/components/delivery/route-heading";
import { RouteProgress } from "@/components/delivery/route-progress";
import { StatusBadge } from "@/components/delivery/status-badge";
import { StatusTimeline } from "@/components/delivery/status-timeline";
import { formatDateTime } from "@/components/delivery/format";
import { useLiveTracking, type LiveConnection } from "@/lib/use-live-tracking";

/**
 * Public parcel tracking (CUS-6): anyone with a tracking number, no login. Shows only what the
 * public API returns (status, cities, times) and updates live over SSE (ADR 0007).
 */
export default function TrackPage() {
  return (
    <main className="container mx-auto max-w-2xl flex-1 space-y-6 px-4 py-10">
      <Suspense fallback={<div className="h-40 animate-pulse rounded-3xl border border-border bg-muted" />}>
        <Tracker />
      </Suspense>
    </main>
  );
}

function Tracker() {
  const router = useRouter();
  const params = useSearchParams();
  const code = params.get("code")?.trim().toUpperCase() || null;
  const [input, setInput] = useState(code ?? "");
  const { view, connection } = useLiveTracking(code);

  return (
    <>
      <section className="space-y-4 text-center">
        <p className="inline-flex items-center gap-2 rounded-full bg-accent px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-primary">
          <MapPinned className="size-4" aria-hidden="true" /> Track a parcel
        </p>
        <h1 className="text-3xl font-extrabold tracking-tight text-foreground sm:text-4xl">Where&apos;s my parcel?</h1>
        <form
          className="mx-auto flex max-w-md gap-2 rounded-2xl border border-border bg-card p-1.5 shadow-sm"
          onSubmit={(event) => {
            event.preventDefault();
            const next = input.trim().toUpperCase();
            if (next) {
              router.replace(`/track?code=${encodeURIComponent(next)}`);
            }
          }}
        >
          <label htmlFor="tracking-number" className="sr-only">Tracking number</label>
          <Input
            id="tracking-number"
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="SD7K3M9Q2XWB"
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            className="h-11 border-0 bg-transparent font-mono tracking-[0.15em] uppercase focus-visible:ring-0"
          />
          <Button type="submit" size="lg" className="h-11 px-5">
            <Search aria-hidden="true" /> Track
          </Button>
        </form>
      </section>

      {code && <Result connection={connection} view={view} code={code} />}

      {!code && (
        <p className="text-center text-sm text-muted-foreground">
          The tracking number is on your booking confirmation and receipt. No account needed.
        </p>
      )}
    </>
  );
}

function Result({ code, connection, view }: {
  code: string;
  connection: LiveConnection;
  view: ReturnType<typeof useLiveTracking>["view"];
}) {
  if (connection === "loading") {
    return (
      <div className="flex items-center justify-center gap-2 rounded-3xl border border-border bg-card p-10 text-sm text-muted-foreground"
        role="status">
        <Loader2 className="size-4 animate-spin" aria-hidden="true" /> Finding {code}…
      </div>
    );
  }
  if (connection === "not-found") {
    return (
      <div className="space-y-2 rounded-3xl border border-dashed border-border bg-card p-10 text-center" role="alert">
        <PackageSearch className="mx-auto size-10 text-muted-foreground" aria-hidden="true" />
        <p className="font-semibold text-foreground">We couldn&apos;t find a parcel with that number.</p>
        <p className="text-sm text-muted-foreground">Check the number on your receipt; it starts with SD and has 12 characters.</p>
      </div>
    );
  }
  if (!view) {
    return (
      <p className="rounded-3xl border border-destructive/30 bg-destructive/5 p-6 text-center text-sm text-destructive" role="alert">
        Tracking is unavailable right now. Please try again in a moment.
      </p>
    );
  }

  const last = view.history[view.history.length - 1];
  return (
    <div className="space-y-4 motion-safe:animate-in motion-safe:fade-in">
      <section className="space-y-6 rounded-3xl border border-border bg-card p-5 shadow-sm sm:p-7">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <RouteHeading from={view.pickupCity} to={view.dropoffCity} trackingNumber={view.trackingNumber}
            className="min-w-0 flex-1" />
          <div className="flex items-center gap-2">
            {connection === "live" && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-bold text-primary">
                <span className="relative flex size-2">
                  <span className="absolute inline-flex size-full animate-ping rounded-full bg-primary opacity-60" />
                  <span className="relative inline-flex size-2 rounded-full bg-primary" />
                </span>
                LIVE
              </span>
            )}
            {connection === "reconnecting" && (
              <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-semibold text-muted-foreground">Reconnecting…</span>
            )}
            <StatusBadge status={view.status} />
          </div>
        </div>
        <RouteProgress status={view.status} history={view.history} live={connection === "live"} />
      </section>

      {view.status === "DELIVERED" && last && (
        <p className="flex items-center gap-2 rounded-2xl bg-primary/10 p-4 text-sm font-semibold text-primary">
          <CheckCircle2 className="size-5" aria-hidden="true" /> Delivered on {formatDateTime(last.occurredAt)}
        </p>
      )}
      {view.status === "FAILED" && (
        <p className="flex items-center gap-2 rounded-2xl bg-destructive/10 p-4 text-sm font-semibold text-destructive">
          <XCircle className="size-5" aria-hidden="true" /> This delivery couldn&apos;t be completed. Please contact the sender.
        </p>
      )}

      <section className="rounded-3xl border border-border bg-card p-5 sm:p-7">
        <h2 className="mb-4 text-sm font-bold uppercase tracking-wider text-muted-foreground">Journey</h2>
        <StatusTimeline entries={view.history} />
        {connection === "live" && (
          <p className="mt-4 text-xs text-muted-foreground">This page updates by itself. No need to refresh.</p>
        )}
      </section>
    </div>
  );
}
