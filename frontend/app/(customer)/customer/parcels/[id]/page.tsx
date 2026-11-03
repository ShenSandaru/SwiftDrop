"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Camera, MapPin, Phone, Receipt } from "lucide-react";
import { cn } from "cn";
import { buttonVariants } from "@/components/ui/button";
import { RouteHeading } from "@/components/delivery/route-heading";
import { RouteProgress } from "@/components/delivery/route-progress";
import { StatusBadge } from "@/components/delivery/status-badge";
import { StatusTimeline } from "@/components/delivery/status-timeline";
import { Ticket } from "@/components/delivery/ticket";
import { SIZE_LABELS, formatDateTime, formatMoney, formatWeight } from "@/components/delivery/format";
import { formatApiError } from "@/lib/api";
import { getParcel, getProofPhoto, type Address, type ParcelDetail } from "@/lib/delivery";
import { useLiveTracking, type LiveConnection } from "@/lib/use-live-tracking";

export default function ParcelDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [detail, setDetail] = useState<ParcelDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { view, connection } = useLiveTracking(detail?.trackingNumber ?? null);
  const liveSteps = view?.history.length ?? 0;

  // Load once, and again whenever the live stream reports a step the detail does not have yet
  useEffect(() => {
    if (detail && liveSteps <= detail.history.length) {
      return;
    }
    let cancelled = false;
    getParcel(id)
      .then((loaded) => !cancelled && setDetail(loaded))
      .catch((failure) => !cancelled && setError(formatApiError(failure)));
    return () => {
      cancelled = true;
    };
  }, [id, liveSteps, detail]);

  if (error) {
    return (
      <div className="space-y-4">
        <BackLink />
        <p className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">{error}</p>
      </div>
    );
  }
  if (!detail) {
    return <div className="h-64 animate-pulse rounded-3xl border border-border bg-muted" aria-label="Loading parcel" />;
  }

  // The live view can be one step ahead while the detail reloads
  const status = view && view.history.length > detail.history.length ? view.status : detail.status;
  const history = view && view.history.length > detail.history.length ? view.history : detail.history;

  return (
    <div className="space-y-6">
      <BackLink />

      <Ticket
        stub={
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Fact label="Parcel" value={`${SIZE_LABELS[detail.size]} · ${formatWeight(detail.weightGrams)}`} />
            <Fact label="Fare" value={formatMoney(detail.fee)} />
            <Fact label="Booked" value={formatDateTime(detail.createdAt)} />
            <Fact label="Contents" value={detail.description ?? "—"} />
          </div>
        }
      >
        <div className="space-y-6">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <RouteHeading from={detail.pickup.city} to={detail.dropoff.city} trackingNumber={detail.trackingNumber}
              className="min-w-0 flex-1" />
            <div className="flex items-center gap-2">
              <LiveChip connection={connection} />
              <StatusBadge status={status} />
            </div>
          </div>
          <RouteProgress status={status} history={history} live={connection === "live"} />
        </div>
      </Ticket>

      <div className="grid gap-4 md:grid-cols-2">
        <AddressCard title="Pickup" address={detail.pickup} />
        <AddressCard title="Drop-off" address={detail.dropoff} />
      </div>

      <div className="grid gap-4 md:grid-cols-[1fr_280px]">
        <section className="rounded-2xl border border-border bg-card p-5">
          <h2 className="mb-4 text-sm font-bold uppercase tracking-wider text-muted-foreground">Journey log</h2>
          <StatusTimeline entries={detail.history} />
        </section>
        <aside className="space-y-4">
          {detail.status === "DELIVERED" && <ProofPhoto parcelId={detail.id} />}
          <Link href={`/customer/parcels/${detail.id}/receipt`} className={cn(buttonVariants({ variant: "outline", size: "lg" }), "w-full")}>
            <Receipt aria-hidden="true" /> View receipt
          </Link>
        </aside>
      </div>
    </div>
  );
}

function LiveChip({ connection }: { connection: LiveConnection }) {
  if (connection === "live") {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-bold text-primary">
        <span className="relative flex size-2">
          <span className="absolute inline-flex size-full animate-ping rounded-full bg-primary opacity-60" />
          <span className="relative inline-flex size-2 rounded-full bg-primary" />
        </span>
        LIVE
      </span>
    );
  }
  if (connection === "reconnecting") {
    return <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-semibold text-muted-foreground">Reconnecting…</span>;
  }
  return null;
}

function AddressCard({ title, address }: { title: string; address: Address }) {
  return (
    <section className="space-y-2 rounded-2xl border border-border bg-card p-5">
      <h2 className="flex items-center gap-2 text-sm font-bold uppercase tracking-wider text-muted-foreground">
        <MapPin className="size-4 text-primary" aria-hidden="true" /> {title}
      </h2>
      <p className="font-semibold text-foreground">{address.contactName}</p>
      <p className="text-sm text-muted-foreground">
        {address.line1}
        {address.line2 && <>, {address.line2}</>}
        <br />
        {address.city}
        {address.postalCode && ` ${address.postalCode}`}
      </p>
      <a href={`tel:${address.phone}`} className="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline">
        <Phone className="size-3.5" aria-hidden="true" /> {address.phone}
      </a>
    </section>
  );
}

/** Downloaded with the login token (an img tag cannot send it), shown from a temporary object URL. */
function ProofPhoto({ parcelId }: { parcelId: string }) {
  const [url, setUrl] = useState<string | null>(null);
  const [missing, setMissing] = useState(false);

  useEffect(() => {
    let objectUrl: string | null = null;
    getProofPhoto(parcelId)
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch(() => setMissing(true));
    return () => {
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [parcelId]);

  if (missing) {
    return null;
  }
  return (
    <figure className="overflow-hidden rounded-2xl border border-border bg-card">
      {url ? (
        // eslint-disable-next-line @next/next/no-img-element -- object URL of a private photo; next/image cannot load it
        <img src={url} alt="Proof of delivery" className="aspect-[4/3] w-full object-cover" />
      ) : (
        <div className="aspect-[4/3] w-full animate-pulse bg-muted" />
      )}
      <figcaption className="flex items-center gap-1.5 p-3 text-xs font-semibold text-muted-foreground">
        <Camera className="size-3.5" aria-hidden="true" /> Proof of delivery
      </figcaption>
    </figure>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className="truncate text-sm font-bold text-foreground">{value}</p>
    </div>
  );
}

function BackLink() {
  return (
    <Link href="/customer/history" className="inline-flex items-center gap-1.5 text-sm font-semibold text-muted-foreground hover:text-foreground">
      <ArrowLeft className="size-4" aria-hidden="true" /> All deliveries
    </Link>
  );
}
