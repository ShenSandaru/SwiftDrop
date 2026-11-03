"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { CheckCircle2, Loader2, Package, Receipt, Ticket as TicketIcon } from "lucide-react";
import { cn } from "cn";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AddressFields, EMPTY_ADDRESS } from "@/components/delivery/address-fields";
import { RouteHeading } from "@/components/delivery/route-heading";
import { Ticket } from "@/components/delivery/ticket";
import { SIZE_LABELS, formatMoney, formatWeight } from "@/components/delivery/format";
import { ApiError, formatApiError } from "@/lib/api";
import {
  MAX_WEIGHT_GRAMS,
  PARCEL_SIZES,
  bookParcel,
  getQuote,
  isPricingUnavailable,
  listZones,
  type AddressInput,
  type ParcelCreated,
  type ParcelSize,
  type PriceQuote,
  type Zone,
} from "@/lib/delivery";

/** Box drawings grow with the size, so the choice is visual rather than a word. */
const SIZE_BOXES: Record<ParcelSize, { box: string; hint: string }> = {
  SMALL: { box: "size-5", hint: "Documents, small items" },
  MEDIUM: { box: "size-7", hint: "Shoe box, books" },
  LARGE: { box: "size-9", hint: "Electronics, bulky items" },
};

export default function BookDeliveryPage() {
  const [zones, setZones] = useState<Zone[]>([]);
  const [pricingMissing, setPricingMissing] = useState(false);
  const [pickup, setPickup] = useState<AddressInput>(EMPTY_ADDRESS);
  const [dropoff, setDropoff] = useState<AddressInput>(EMPTY_ADDRESS);
  const [size, setSize] = useState<ParcelSize>("SMALL");
  const [weightKg, setWeightKg] = useState("1");
  const [description, setDescription] = useState("");
  const [quote, setQuote] = useState<PriceQuote | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [booking, setBooking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [invalidFields, setInvalidFields] = useState<string[]>([]);
  const [booked, setBooked] = useState<ParcelCreated | null>(null);

  useEffect(() => {
    listZones()
      .then(setZones)
      .catch((failure) => {
        if (isPricingUnavailable(failure)) {
          setPricingMissing(true);
        } else {
          setError(formatApiError(failure));
        }
      });
  }, []);

  const weightGrams = Math.round(Number(weightKg) * 1000);
  const weightValid = Number.isFinite(weightGrams) && weightGrams >= 1 && weightGrams <= MAX_WEIGHT_GRAMS;
  const zoneName = (id: string) => zones.find((zone) => zone.id === id)?.name;
  const from = pickup.city.trim() || zoneName(pickup.zoneId) || "Pickup";
  const to = dropoff.city.trim() || zoneName(dropoff.zoneId) || "Drop-off";
  const canQuote = Boolean(pickup.zoneId && dropoff.zoneId && weightValid && !pricingMissing);

  // An estimate belongs to one route, size and weight; any change makes it stale
  const quoteKey = `${pickup.zoneId}|${dropoff.zoneId}|${size}|${weightGrams}`;
  const [quotedFor, setQuotedFor] = useState<string | null>(null);
  const currentQuote = quotedFor === quoteKey ? quote : null;

  async function estimate() {
    setError(null);
    setQuoting(true);
    try {
      const result = await getQuote(pickup.zoneId, dropoff.zoneId, size, weightGrams);
      setQuote(result);
      setQuotedFor(quoteKey);
    } catch (failure) {
      if (isPricingUnavailable(failure)) {
        setPricingMissing(true);
      } else {
        setError(formatApiError(failure));
      }
    } finally {
      setQuoting(false);
    }
  }

  async function confirm(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setInvalidFields([]);
    setBooking(true);
    try {
      setBooked(
        await bookParcel({
          pickup: clean(pickup),
          dropoff: clean(dropoff),
          size,
          weightGrams,
          description: description.trim() || undefined,
        }),
      );
    } catch (failure) {
      if (failure instanceof ApiError && failure.problem?.invalidFields) {
        setInvalidFields(failure.problem.invalidFields);
        setError("Please check the highlighted fields.");
      } else {
        setError(formatApiError(failure));
      }
    } finally {
      setBooking(false);
    }
  }

  if (booked) {
    return <Booked parcel={booked} from={from} to={to} size={size} weightGrams={weightGrams} />;
  }

  return (
    <form onSubmit={confirm} className="grid gap-6 lg:grid-cols-[1fr_340px]">
      <div className="space-y-6">
        {pricingMissing && (
          <p className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/50 dark:text-amber-100">
            Pricing isn&apos;t available yet, so zones and estimates can&apos;t be loaded. The fee is calculated when
            the delivery is booked.
          </p>
        )}

        <section className="space-y-6 rounded-2xl border border-border bg-card p-5 sm:p-6">
          <AddressFields id="pickup" legend="① Pick up from" value={pickup} onChange={setPickup} zones={zones}
            invalidFields={invalidFields} disabled={booking} />
          <div className="border-t border-dashed border-border" aria-hidden="true" />
          <AddressFields id="dropoff" legend="② Deliver to" value={dropoff} onChange={setDropoff} zones={zones}
            invalidFields={invalidFields} disabled={booking} />
        </section>

        <section className="space-y-4 rounded-2xl border border-border bg-card p-5 sm:p-6">
          <fieldset className="space-y-3" disabled={booking}>
            <legend className="text-sm font-semibold text-foreground">③ Parcel size</legend>
            <div className="grid grid-cols-3 gap-3">
              {PARCEL_SIZES.map((option) => (
                <label
                  key={option}
                  className={cn(
                    "flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 p-3 text-center transition-colors has-[:focus-visible]:ring-3 has-[:focus-visible]:ring-ring/50",
                    size === option ? "border-primary bg-accent" : "border-border hover:border-primary/40",
                  )}
                >
                  <input type="radio" name="size" value={option} checked={size === option}
                    onChange={() => setSize(option)} className="sr-only" />
                  <span className="flex h-10 items-end">
                    <Package className={cn(SIZE_BOXES[option].box, size === option ? "text-primary" : "text-muted-foreground")}
                      aria-hidden="true" />
                  </span>
                  <span className="text-sm font-bold text-foreground">{SIZE_LABELS[option]}</span>
                  <span className="hidden text-[11px] leading-tight text-muted-foreground sm:block">{SIZE_BOXES[option].hint}</span>
                </label>
              ))}
            </div>
          </fieldset>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="weight">Weight (kg)</Label>
              <Input id="weight" type="number" inputMode="decimal" min="0.001" max="30" step="0.1" required
                value={weightKg} onChange={(event) => setWeightKg(event.target.value)}
                aria-invalid={!weightValid || invalidFields.includes("weightGrams") || undefined} disabled={booking} />
              <p className="text-xs text-muted-foreground">Up to 30 kg. Each started kilogram is charged.</p>
            </div>
            <div className="space-y-1">
              <Label htmlFor="description">What&apos;s inside <span className="text-muted-foreground">(optional)</span></Label>
              <Input id="description" maxLength={200} value={description}
                onChange={(event) => setDescription(event.target.value)} disabled={booking} />
            </div>
          </div>
        </section>
      </div>

      {/* The fare ticket */}
      <aside className="lg:sticky lg:top-6 lg:self-start">
        <Ticket
          stub={
            <div className="space-y-3">
              {currentQuote ? (
                <dl className="space-y-1.5 text-sm">
                  <Row label="Base fare" value={`${currentQuote.currency} ${currentQuote.breakdown.baseFee}`} />
                  <Row label={`Weight (${formatWeight(weightGrams)})`} value={`${currentQuote.currency} ${currentQuote.breakdown.weightFee}`} />
                  <Row label={`${SIZE_LABELS[size]} parcel`} value={`${currentQuote.currency} ${currentQuote.breakdown.sizeSurcharge}`} />
                  <div className="flex items-baseline justify-between border-t border-border pt-2">
                    <dt className="font-semibold text-foreground">Estimated fare</dt>
                    <dd className="text-2xl font-extrabold text-primary">
                      {formatMoney({ amount: currentQuote.amount, currency: currentQuote.currency })}
                    </dd>
                  </div>
                </dl>
              ) : (
                <p className="text-sm text-muted-foreground">
                  {pricingMissing ? "Estimates are unavailable right now." : "Choose both zones and a weight, then get your estimate."}
                </p>
              )}
              <Button type="button" variant="outline" className="w-full" onClick={estimate}
                disabled={!canQuote || quoting || booking}>
                {quoting ? <Loader2 className="animate-spin" aria-hidden="true" /> : <TicketIcon aria-hidden="true" />}
                {currentQuote ? "Refresh estimate" : "Get estimate"}
              </Button>
              <Button type="submit" size="lg" className="w-full" disabled={booking || !weightValid}>
                {booking && <Loader2 className="animate-spin" aria-hidden="true" />}
                Confirm booking
              </Button>
              <p className="text-[11px] text-muted-foreground">The final fee is calculated by SwiftDrop when you confirm.</p>
            </div>
          }
        >
          <p className="mb-3 text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">Your fare</p>
          <RouteHeading from={from} to={to} />
          <p className="mt-3 text-sm text-muted-foreground">
            {SIZE_LABELS[size]} · {weightValid ? formatWeight(weightGrams) : "—"}
          </p>
        </Ticket>
        {error && (
          <p role="alert" className="mt-3 rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
            {error}
          </p>
        )}
      </aside>
    </form>
  );
}

function Booked({ parcel, from, to, size, weightGrams }: {
  parcel: ParcelCreated; from: string; to: string; size: ParcelSize; weightGrams: number;
}) {
  return (
    <div className="mx-auto max-w-lg space-y-4 motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-4">
      <Ticket
        stub={
          <div className="flex flex-col gap-2 sm:flex-row">
            <Link href={`/customer/parcels/${parcel.id}`} className={cn(buttonVariants(), "flex-1")}>
              <Package aria-hidden="true" /> Follow your parcel
            </Link>
            <Link href={`/customer/parcels/${parcel.id}/receipt`} className={cn(buttonVariants({ variant: "outline" }), "flex-1")}>
              <Receipt aria-hidden="true" /> Receipt
            </Link>
          </div>
        }
      >
        <div className="space-y-4">
          <p className="inline-flex items-center gap-2 text-sm font-semibold text-primary">
            <CheckCircle2 className="size-5" aria-hidden="true" /> Booked: your parcel is on the route
          </p>
          <RouteHeading from={from} to={to} />
          <div className="grid grid-cols-3 gap-3 rounded-xl bg-muted p-3 text-center">
            <Fact label="Tracking" value={parcel.trackingNumber} mono />
            <Fact label="Parcel" value={`${SIZE_LABELS[size]} · ${formatWeight(weightGrams)}`} />
            <Fact label="Fare" value={formatMoney(parcel.fee)} />
          </div>
          <p className="text-xs text-muted-foreground">
            Share the tracking number: anyone can follow it at <span className="font-mono">/track</span>, no login needed.
          </p>
        </div>
      </Ticket>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-2">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium text-foreground">{value}</dd>
    </div>
  );
}

function Fact({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="min-w-0">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className={cn("truncate text-sm font-bold text-foreground", mono && "font-mono text-xs tracking-wider")}>{value}</p>
    </div>
  );
}

function clean(address: AddressInput): AddressInput {
  return {
    contactName: address.contactName.trim(),
    phone: address.phone.trim(),
    line1: address.line1.trim(),
    line2: address.line2?.trim() || undefined,
    city: address.city.trim(),
    zoneId: address.zoneId,
    postalCode: address.postalCode?.trim() || undefined,
  };
}
