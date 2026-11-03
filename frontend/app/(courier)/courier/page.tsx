"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Camera, ChevronRight, Loader2, MapPin, Navigation, Phone, RefreshCw } from "lucide-react";
import { cn } from "cn";
import { Button, buttonVariants } from "@/components/ui/button";
import { StatusBadge } from "@/components/delivery/status-badge";
import { MiniRoute } from "@/components/delivery/mini-route";
import { STATUS_LABELS, formatDate, formatWeight, SIZE_LABELS } from "@/components/delivery/format";
import { formatApiError } from "@/lib/api";
import { changeStatus, isFinalStatus, listCourierParcels, type CourierParcel, type ParcelStatus } from "@/lib/delivery";

/** The button text for each forward step; delivering needs proof first, so it opens the parcel. */
const STEP_ACTIONS: Partial<Record<ParcelStatus, string>> = {
  PICKED_UP: "Mark picked up",
  IN_TRANSIT: "Start transit",
  OUT_FOR_DELIVERY: "Out for delivery",
};

export default function CourierRunPage() {
  const [parcels, setParcels] = useState<CourierParcel[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(() => {
    listCourierParcels()
      .then((loaded) => {
        setParcels(loaded);
        setError(null);
      })
      .catch((failure) => setError(formatApiError(failure)));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function advance(parcel: CourierParcel, next: ParcelStatus) {
    setBusy(parcel.id);
    setError(null);
    try {
      await changeStatus(parcel.id, next);
      load();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setBusy(null);
    }
  }

  const active = parcels?.filter((parcel) => !isFinalStatus(parcel.status)) ?? [];
  const finished = parcels?.filter((parcel) => isFinalStatus(parcel.status)) ?? [];
  const total = parcels?.length ?? 0;
  const done = finished.length;

  return (
    <div className="mx-auto max-w-xl space-y-5">
      {/* Day header: the run as one route */}
      <section className="rounded-3xl bg-primary p-5 text-primary-foreground">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] opacity-80">Today&apos;s run</p>
            <p className="text-sm opacity-90">{formatDate(new Date().toISOString())}</p>
          </div>
          <Button variant="ghost" size="icon" onClick={load} aria-label="Refresh"
            className="text-primary-foreground hover:bg-primary-foreground/10">
            <RefreshCw aria-hidden="true" />
          </Button>
        </div>
        <p className="mt-3 text-3xl font-extrabold">
          {done} <span className="text-lg font-semibold opacity-80">of {total} done</span>
        </p>
        <div className="mt-3 h-2 overflow-hidden rounded-full bg-primary-foreground/25" role="progressbar"
          aria-valuemin={0} aria-valuemax={total} aria-valuenow={done} aria-label="Stops completed">
          <div className="h-full rounded-full bg-primary-foreground transition-[width] duration-700"
            style={{ width: total ? `${(done / total) * 100}%` : "0%" }} />
        </div>
        <p className="mt-2 text-sm opacity-90">
          {active.length === 0 ? "No stops left. Nice work." : `${active.length} stop${active.length === 1 ? "" : "s"} to go`}
        </p>
      </section>

      {error && <p role="alert" className="rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{error}</p>}

      {parcels === null && !error && (
        <div className="space-y-3" aria-hidden="true">
          {[0, 1].map((index) => <div key={index} className="h-48 animate-pulse rounded-2xl border border-border bg-muted" />)}
        </div>
      )}

      {parcels !== null && total === 0 && (
        <p className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          No deliveries assigned to you today.
        </p>
      )}

      <ol className="space-y-3">
        {active.map((parcel, index) => (
          <li key={parcel.id}>
            <StopCard parcel={parcel} stop={index + 1} busy={busy === parcel.id} onAdvance={advance} />
          </li>
        ))}
      </ol>

      {finished.length > 0 && (
        <section className="space-y-2">
          <h2 className="text-sm font-bold uppercase tracking-wider text-muted-foreground">Finished today</h2>
          <ul className="space-y-2">
            {finished.map((parcel) => (
              <li key={parcel.id}>
                <Link href={`/courier/parcels/${parcel.id}`}
                  className="flex items-center justify-between gap-3 rounded-xl border border-border bg-card p-3 opacity-80 transition-opacity hover:opacity-100">
                  <span className="min-w-0">
                    <span className="block truncate text-sm font-semibold capitalize text-foreground">{parcel.dropoff.city}</span>
                    <span className="font-mono text-[11px] tracking-wider text-muted-foreground">{parcel.trackingNumber}</span>
                  </span>
                  <StatusBadge status={parcel.status} />
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

function StopCard({ parcel, stop, busy, onAdvance }: {
  parcel: CourierParcel;
  stop: number;
  busy: boolean;
  onAdvance: (parcel: CourierParcel, next: ParcelStatus) => void;
}) {
  // Before pickup the courier heads to the sender; afterwards to the recipient
  const heading = parcel.status === "CREATED" ? "Pick up from" : "Deliver to";
  const place = parcel.status === "CREATED" ? parcel.pickup : parcel.dropoff;
  const next = parcel.nextStatuses.find((status) => status !== "FAILED");
  const mapsUrl = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
    [place.line1, place.line2, place.city].filter(Boolean).join(", "),
  )}`;

  return (
    <article className="overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
      <div className="flex items-center justify-between gap-2 border-b border-dashed border-border px-4 py-2.5">
        <span className="inline-flex items-center gap-2">
          <span className="inline-flex size-6 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground">
            {stop}
          </span>
          <span className="font-mono text-xs font-semibold tracking-[0.15em] text-primary">{parcel.trackingNumber}</span>
        </span>
        <MiniRoute status={parcel.status} />
      </div>

      <Link href={`/courier/parcels/${parcel.id}`} className="block space-y-1 px-4 pt-3 hover:bg-muted/40">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">{heading}</p>
        <p className="flex items-center justify-between gap-2 text-lg font-bold text-foreground">
          <span className="truncate">{place.contactName}</span>
          <ChevronRight className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
        </p>
        <p className="flex items-start gap-1.5 text-sm text-muted-foreground">
          <MapPin className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
          <span>
            {place.line1}
            {place.line2 && `, ${place.line2}`}, <span className="capitalize">{place.city}</span>
          </span>
        </p>
        <p className="pb-1 text-xs text-muted-foreground">
          {SIZE_LABELS[parcel.size]} · {formatWeight(parcel.weightGrams)} · {STATUS_LABELS[parcel.status]}
        </p>
      </Link>

      <div className="grid grid-cols-2 gap-2 px-4 pb-3 pt-2">
        <a href={`tel:${place.phone}`} className={cn(buttonVariants({ variant: "outline", size: "lg" }), "h-11")}>
          <Phone aria-hidden="true" /> Call
        </a>
        <a href={mapsUrl} target="_blank" rel="noreferrer" className={cn(buttonVariants({ variant: "outline", size: "lg" }), "h-11")}>
          <Navigation aria-hidden="true" /> Directions
        </a>
      </div>

      <div className="px-4 pb-4">
        {next === "DELIVERED" ? (
          <Link href={`/courier/parcels/${parcel.id}`} className={cn(buttonVariants({ size: "lg" }), "h-12 w-full text-base")}>
            <Camera aria-hidden="true" /> {parcel.proofCaptured ? "Finish delivery" : "Capture proof & deliver"}
          </Link>
        ) : next ? (
          <Button size="lg" className="h-12 w-full text-base" disabled={busy} onClick={() => onAdvance(parcel, next)}>
            {busy && <Loader2 className="animate-spin" aria-hidden="true" />}
            {STEP_ACTIONS[next] ?? STATUS_LABELS[next]}
          </Button>
        ) : null}
      </div>
    </article>
  );
}
