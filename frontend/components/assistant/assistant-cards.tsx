import Link from "next/link";
import { ArrowRight, MapPin, Package, PackagePlus, Radar } from "lucide-react";
import { MiniRoute } from "@/components/delivery/mini-route";
import { StatusBadge } from "@/components/delivery/status-badge";
import { SIZE_LABELS, formatShortDateTime } from "@/components/delivery/format";
import { PARCEL_STATUSES, type ParcelStatus } from "@/lib/delivery";
import type { AssistantCard, FeeCardData, TrackCardData } from "@/lib/assistant";

/** Shows what a tool found, in the Island Route style of the rest of SwiftDrop. */
export function AssistantCardView({ card }: { card: AssistantCard }) {
  switch (card.tool) {
    case "track_parcel":
      return <TrackingPass data={card.data} />;
    case "estimate_fee":
      return <FareTicket data={card.data} />;
    case "list_service_areas":
      return <ServiceAreas areas={card.data.areas} />;
    default:
      return null;
  }
}

function isParcelStatus(status: string): status is ParcelStatus {
  return (PARCEL_STATUSES as readonly string[]).includes(status);
}

/** A boarding-pass style tracking card: number, status, route and the five-stop progress. */
function TrackingPass({ data }: { data: TrackCardData }) {
  const status = isParcelStatus(data.status) ? data.status : null;
  const last = data.history[data.history.length - 1];
  return (
    <article className="overflow-hidden rounded-2xl border border-primary/25 bg-card shadow-sm">
      <div className="flex items-center justify-between gap-2 bg-primary/8 px-3.5 py-2">
        <span className="inline-flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-[0.18em] text-primary">
          <Radar className="size-3.5" aria-hidden="true" /> Tracking pass
        </span>
        <span className="font-mono text-xs font-semibold tracking-wider text-foreground">{data.trackingNumber}</span>
      </div>
      <div className="space-y-3 px-3.5 py-3">
        <div className="flex items-center gap-2">
          <City label="From" name={data.from} />
          <div className="relative flex flex-1 items-center" aria-hidden="true">
            <span className="h-px flex-1 border-t-2 border-dashed border-primary/35" />
            <Package className="mx-1 size-4 text-primary" />
            <span className="h-px flex-1 border-t-2 border-dashed border-primary/35" />
          </div>
          <City label="To" name={data.to} align="right" />
        </div>
        {status && (
          <div className="flex flex-wrap items-center justify-between gap-2">
            <StatusBadge status={status} />
            <MiniRoute status={status} />
          </div>
        )}
        {last && (
          <p className="text-[11px] text-muted-foreground">
            Updated {formatShortDateTime(last.at)}{last.hub ? ` · ${last.hub}` : ""}
          </p>
        )}
      </div>
      <Link href={`/track?code=${encodeURIComponent(data.trackingNumber)}`}
        className="flex items-center justify-between border-t border-border px-3.5 py-2.5 text-xs font-bold text-primary hover:bg-primary/5">
        Open live tracking <ArrowRight className="size-3.5" aria-hidden="true" />
      </Link>
    </article>
  );
}

function City({ label, name, align = "left" }: { label: string; name: string | null; align?: "left" | "right" }) {
  return (
    <div className={align === "right" ? "text-right" : undefined}>
      <p className="text-[9px] font-semibold uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className="text-sm font-extrabold text-foreground">{name ?? "—"}</p>
    </div>
  );
}

function money(currency: string, amount: string) {
  const value = Number(amount);
  return `${currency} ${Number.isFinite(value) ? value.toLocaleString("en-LK", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : amount}`;
}

/** A torn-off fare ticket: total on top, breakdown on the stub below the perforation. */
function FareTicket({ data }: { data: FeeCardData }) {
  return (
    <article className="relative rounded-2xl border border-border bg-card shadow-sm">
      <div className="space-y-1 px-3.5 pb-3 pt-3">
        <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-primary">Fare estimate</p>
        <p className="text-sm font-semibold text-foreground">
          {data.from} <span className="text-muted-foreground">→</span> {data.to}
        </p>
        <p className="text-2xl font-extrabold tracking-tight text-foreground">{money(data.currency, data.totalFee)}</p>
        <p className="text-[11px] text-muted-foreground">
          {SIZE_LABELS[data.size] ?? data.size} parcel · {data.chargedKg} kg charged
        </p>
      </div>
      <div className="relative" aria-hidden="true">
        <span className="absolute -left-2.5 top-1/2 size-5 -translate-y-1/2 rounded-full border border-border bg-muted [clip-path:inset(0_0_0_50%)]" />
        <span className="absolute -right-2.5 top-1/2 size-5 -translate-y-1/2 rounded-full border border-border bg-muted [clip-path:inset(0_50%_0_0)]" />
        <div className="mx-4 border-t-2 border-dashed border-border" />
      </div>
      <dl className="space-y-1 px-3.5 py-2.5 text-xs">
        <Row label="Base fare" value={money(data.currency, data.baseFee)} />
        <Row label={`Weight (${data.chargedKg} kg)`} value={money(data.currency, data.weightFee)} />
        <Row label="Size" value={money(data.currency, data.sizeFee)} />
      </dl>
      <Link href="/customer/new"
        className="flex items-center justify-between rounded-b-2xl border-t border-border px-3.5 py-2.5 text-xs font-bold text-primary hover:bg-primary/5">
        <span className="inline-flex items-center gap-1.5"><PackagePlus className="size-3.5" aria-hidden="true" /> Book this delivery</span>
        <ArrowRight className="size-3.5" aria-hidden="true" />
      </Link>
    </article>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-semibold text-foreground">{value}</dd>
    </div>
  );
}

function ServiceAreas({ areas }: { areas: string[] }) {
  return (
    <article className="rounded-2xl border border-border bg-card p-3.5 shadow-sm">
      <p className="mb-2 text-[10px] font-bold uppercase tracking-[0.18em] text-primary">We deliver between</p>
      <ul className="flex flex-wrap gap-1.5">
        {areas.map((area) => (
          <li key={area} className="inline-flex items-center gap-1 rounded-full border border-primary/25 bg-accent px-2.5 py-1 text-xs font-semibold text-primary">
            <MapPin className="size-3" aria-hidden="true" /> {area}
          </li>
        ))}
      </ul>
    </article>
  );
}
