"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { AlertCircle, CheckCircle2, Loader2, MapPin, Navigation, Package, Receipt, Ticket as TicketIcon } from "lucide-react";
import { cn } from "cn";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AddressFields, EMPTY_ADDRESS, zonesForDistrict } from "@/components/delivery/address-fields";
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
  listAvailableZones,
  listZones,
  type AddressInput,
  type ParcelCreated,
  type ParcelSize,
  type PriceQuote,
  type Zone,
} from "@/lib/delivery";
import {
  calculateRoadDistanceKm,
  listDistricts,
  resolveDistrictCoordinates,
  type District,
} from "@/lib/districts";
import { getPricingConfig, type PricingConfigData } from "@/lib/pricing";

/** Box drawings grow with the size, so the choice is visual rather than a word. */
const SIZE_BOXES: Record<ParcelSize, { box: string; hint: string }> = {
  SMALL: { box: "size-5", hint: "Documents, small items" },
  MEDIUM: { box: "size-7", hint: "Shoe box, books" },
  LARGE: { box: "size-9", hint: "Electronics, bulky items" },
};

export default function BookDeliveryPage() {
  const [zones, setZones] = useState<Zone[]>([]);
  const [districts, setDistricts] = useState<District[]>([]);
  const [pricingConfig, setPricingConfig] = useState<PricingConfigData | null>(null);
  const [pricingMissing, setPricingMissing] = useState(false);
  const [routeQuoteError, setRouteQuoteError] = useState<string | null>(null);
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
  const quoteRequestId = useRef(0);

  useEffect(() => {
    Promise.all([
      listAvailableZones().catch(() => listZones()),
      listDistricts().catch(() => []),
      getPricingConfig().catch(() => null),
    ])
      .then(([loadedZones, loadedDistricts, loadedConfig]) => {
        setZones(loadedZones);
        setDistricts(
          loadedDistricts.filter((district) => zonesForDistrict(district, loadedZones).length > 0)
        );
        setPricingConfig(loadedConfig);
      })
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
  const pickupZone = zones.find((zone) => zone.id === pickup.zoneId);
  const dropoffZone = zones.find((zone) => zone.id === dropoff.zoneId);
  const from = pickup.city.trim() || pickupZone?.name || "Pickup";
  const to = dropoff.city.trim() || dropoffZone?.name || "Drop-off";
  const canQuote = Boolean(pickup.zoneId && dropoff.zoneId && weightValid && !pricingMissing);

  const estimatedDistanceKm = (() => {
    if (!pickupZone || !dropoffZone) return null;
    if (pickupZone.id === dropoffZone.id) return 0;
    const pCoords =
      resolveDistrictCoordinates(pickupZone.code) ??
      resolveDistrictCoordinates(pickup.city);
    const dCoords =
      resolveDistrictCoordinates(dropoffZone.code) ??
      resolveDistrictCoordinates(dropoff.city);
    if (!pCoords || !dCoords) return null;
    return calculateRoadDistanceKm(pCoords.lat, pCoords.lng, dCoords.lat, dCoords.lng);
  })();

  // An estimate belongs to one route, size and weight; any change makes it stale
  const quoteKey = `${pickup.zoneId}|${dropoff.zoneId}|${size}|${weightGrams}`;
  const [quotedFor, setQuotedFor] = useState<string | null>(null);
  const currentQuote = quotedFor === quoteKey ? quote : null;

  const estimate = useCallback(async () => {
    if (!pickup.zoneId || !dropoff.zoneId || !weightValid || pricingMissing) return;
    const reqId = ++quoteRequestId.current;
    const targetKey = `${pickup.zoneId}|${dropoff.zoneId}|${size}|${weightGrams}`;
    setError(null);
    setRouteQuoteError(null);
    setQuoting(true);
    try {
      const result = await getQuote(pickup.zoneId, dropoff.zoneId, size, weightGrams);
      if (quoteRequestId.current !== reqId) return;
      setQuote(result);
      setQuotedFor(targetKey);
    } catch (failure) {
      if (quoteRequestId.current !== reqId) return;
      if (isPricingUnavailable(failure)) {
        setRouteQuoteError("Pricing is not configured for this route yet. Final fare is calculated on booking.");
      } else {
        setRouteQuoteError(formatApiError(failure));
      }
    } finally {
      if (quoteRequestId.current === reqId) {
        setQuoting(false);
      }
    }
  }, [pickup.zoneId, dropoff.zoneId, size, weightGrams, weightValid, pricingMissing]);

  // Automatically fetch a live estimate when pickup zone, drop-off zone, size, or weight changes
  useEffect(() => {
    if (!canQuote) return;
    const timer = setTimeout(() => {
      void estimate();
    }, 300);
    return () => clearTimeout(timer);
  }, [canQuote, estimate]);

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
    <div className="space-y-6 pb-24">
      {/* Section Header matching System Admin theme */}
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wider text-primary">
          Parcel Dispatch Booking
        </p>
        <h2 className="font-heading text-xl font-bold tracking-tight text-foreground">
          Book a Delivery
        </h2>
        <p className="text-xs text-muted-foreground">
          Select pickup origin, delivery destination, and parcel specifications for live tariff calculation.
        </p>
      </div>

      <form onSubmit={confirm} className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <div className="space-y-5">
          {pricingMissing && (
            <Alert variant="warning">
              <AlertCircle aria-hidden="true" />
              <AlertTitle>Pricing Unavailable</AlertTitle>
              <AlertDescription>
                Pricing isn&apos;t available yet, so zones and estimates can&apos;t be loaded. The fee is calculated when the delivery is booked.
              </AlertDescription>
            </Alert>
          )}

          {/* Step 01: Pickup Origin */}
          <Card className="rounded-xl border border-border bg-card shadow-2xs">
            <CardHeader className="border-b border-border/70 pb-4">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
                    <MapPin className="size-4" aria-hidden="true" />
                  </span>
                  <div className="space-y-0.5">
                    <CardTitle className="font-heading text-base font-semibold text-foreground">
                      Pickup Origin
                    </CardTitle>
                    <CardDescription className="text-xs">
                      Sender contact and collection address. Selecting a district auto-fills postal details.
                    </CardDescription>
                  </div>
                </div>
                <span className="rounded-full bg-primary/10 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-primary">
                  STEP 01
                </span>
              </div>
            </CardHeader>
            <CardContent className="pt-5">
              <AddressFields
                id="pickup"
                value={pickup}
                onChange={setPickup}
                zones={zones}
                districts={districts}
                invalidFields={invalidFields}
                disabled={booking}
              />
            </CardContent>
          </Card>

          {/* Step 02: Delivery Destination */}
          <Card className="rounded-xl border border-border bg-card shadow-2xs">
            <CardHeader className="border-b border-border/70 pb-4">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
                    <Navigation className="size-4" aria-hidden="true" />
                  </span>
                  <div className="space-y-0.5">
                    <CardTitle className="font-heading text-base font-semibold text-foreground">
                      Delivery Destination
                    </CardTitle>
                    <CardDescription className="text-xs">
                      Recipient contact and drop-off address. Zones filter automatically by district.
                    </CardDescription>
                  </div>
                </div>
                <span className="rounded-full bg-primary/10 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-primary">
                  STEP 02
                </span>
              </div>
            </CardHeader>
            <CardContent className="pt-5">
              <AddressFields
                id="dropoff"
                value={dropoff}
                onChange={setDropoff}
                zones={zones}
                districts={districts}
                invalidFields={invalidFields}
                disabled={booking}
              />
            </CardContent>
          </Card>

          {/* Step 03: Parcel Size & Weight */}
          <Card className="rounded-xl border border-border bg-card shadow-2xs">
            <CardHeader className="border-b border-border/70 pb-4">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
                    <Package className="size-4" aria-hidden="true" />
                  </span>
                  <div className="space-y-0.5">
                    <CardTitle className="font-heading text-base font-semibold text-foreground">
                      Parcel Size &amp; Weight
                    </CardTitle>
                    <CardDescription className="text-xs">
                      Choose the handling tier and enter parcel weight for live tariff calculation.
                    </CardDescription>
                  </div>
                </div>
                <span className="rounded-full bg-primary/10 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-primary">
                  STEP 03
                </span>
              </div>
            </CardHeader>
            <CardContent className="space-y-5 pt-5">
              <fieldset className="space-y-2.5" disabled={booking}>
                <legend className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Parcel size tier
                </legend>
                <div className="grid grid-cols-3 gap-3">
                  {PARCEL_SIZES.map((option) => {
                    const surcharge = pricingConfig
                      ? option === "SMALL"
                        ? pricingConfig.smallSizeSurcharge
                        : option === "MEDIUM"
                          ? pricingConfig.mediumSizeSurcharge
                          : pricingConfig.largeSizeSurcharge
                      : null;
                    const surchargeLabel =
                      surcharge === null
                        ? "—"
                        : `+ ${pricingConfig?.currency ?? "LKR"} ${surcharge.toFixed(2)}`;

                    return (
                      <label
                        key={option}
                        className={cn(
                          "flex cursor-pointer flex-col items-center gap-2 rounded-xl border p-3.5 text-center transition-colors has-[:focus-visible]:ring-3 has-[:focus-visible]:ring-ring/50",
                          size === option
                            ? "border-primary bg-accent/60 ring-1 ring-primary/30 shadow-2xs"
                            : "border-border bg-background hover:border-primary/40 hover:bg-muted/30"
                        )}
                      >
                        <input
                          type="radio"
                          name="size"
                          value={option}
                          checked={size === option}
                          onChange={() => setSize(option)}
                          className="sr-only"
                        />
                        <span className="flex h-10 items-end">
                          <Package
                            className={cn(
                              SIZE_BOXES[option].box,
                              size === option ? "text-primary" : "text-muted-foreground"
                            )}
                            aria-hidden="true"
                          />
                        </span>
                        <span className="font-heading text-sm font-semibold text-foreground">
                          {SIZE_LABELS[option]}
                        </span>
                        <span className="hidden text-[11px] leading-tight text-muted-foreground sm:block">
                          {SIZE_BOXES[option].hint}
                        </span>
                        <span
                          className={cn(
                            "mt-0.5 inline-flex items-center rounded-full px-2 py-0.5 font-mono text-[10px] font-semibold",
                            size === option
                              ? "bg-primary/15 text-primary"
                              : "bg-muted text-muted-foreground"
                          )}
                        >
                          {surchargeLabel}
                        </span>
                      </label>
                    );
                  })}
                </div>
              </fieldset>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="weight" className="text-xs font-semibold text-foreground">
                    Weight (kg)
                  </Label>
                  <Input
                    id="weight"
                    type="number"
                    inputMode="decimal"
                    min="0.001"
                    max="30"
                    step="any"
                    required
                    value={weightKg}
                    onChange={(event) => setWeightKg(event.target.value)}
                    aria-invalid={!weightValid || invalidFields.includes("weightGrams") || undefined}
                    disabled={booking}
                    className="h-9 font-mono"
                  />
                  <p className="text-xs text-muted-foreground">
                    {pricingConfig ? (
                      <>
                        Billed at{" "}
                        <span className="font-mono font-medium text-foreground">
                          {pricingConfig.currency} {pricingConfig.perKgFee.toFixed(2)}
                        </span>{" "}
                        per started kg.
                      </>
                    ) : (
                      "Each started kilogram is billed at the active tariff rate."
                    )}
                  </p>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="description" className="text-xs font-semibold text-foreground">
                    What&apos;s inside <span className="font-normal text-muted-foreground">(optional)</span>
                  </Label>
                  <Input
                    id="description"
                    maxLength={200}
                    placeholder="e.g. Documents, clothing, electronics"
                    value={description}
                    onChange={(event) => setDescription(event.target.value)}
                    disabled={booking}
                    className="h-9"
                  />
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* The fare ticket */}
        <aside className="lg:sticky lg:top-6 lg:self-start">
          <Ticket
            stub={
              <div className="space-y-3">
                {currentQuote ? (
                  <dl className="space-y-1.5 text-sm">
                    <Row label="Base / distance fare" value={`${currentQuote.currency} ${currentQuote.breakdown.baseFee}`} />
                    <Row label={`Weight (${formatWeight(weightGrams)})`} value={`${currentQuote.currency} ${currentQuote.breakdown.weightFee}`} />
                    <Row label={`${SIZE_LABELS[size]} parcel`} value={`${currentQuote.currency} ${currentQuote.breakdown.sizeSurcharge}`} />
                    <div className="flex items-baseline justify-between border-t border-border pt-2">
                      <dt className="font-heading font-semibold text-foreground">Estimated fare</dt>
                      <dd className="font-mono text-2xl font-bold text-primary">
                        {formatMoney({ amount: currentQuote.amount, currency: currentQuote.currency })}
                      </dd>
                    </div>
                  </dl>
                ) : canQuote && routeQuoteError ? (
                  <p className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-2.5 text-xs text-amber-800 dark:text-amber-300">
                    {routeQuoteError}
                  </p>
                ) : (
                  <p className="text-xs text-muted-foreground">
                    {pricingMissing
                      ? "Estimates are unavailable right now."
                      : quoting
                        ? "Calculating live fare estimate…"
                        : "Choose both zones and a weight to see your live estimate."}
                  </p>
                )}
                <Button type="button" variant="outline" className="h-9 w-full font-semibold" onClick={estimate}
                  disabled={!canQuote || quoting || booking}>
                  {quoting ? <Loader2 className="animate-spin" aria-hidden="true" /> : <TicketIcon aria-hidden="true" />}
                  {currentQuote ? "Refresh estimate" : "Get estimate"}
                </Button>
                <Button type="submit" size="lg" className="w-full font-semibold" disabled={booking || !weightValid}>
                  {booking && <Loader2 className="animate-spin" aria-hidden="true" />}
                  Confirm booking
                </Button>
                <p className="text-[11px] text-muted-foreground">The final fee is calculated by SwiftDrop when you confirm.</p>
              </div>
            }
          >
            <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-primary">Your fare</p>
            <RouteHeading from={from} to={to} />
            <div className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
              <span className="font-medium text-foreground">{SIZE_LABELS[size]}</span>
              <span aria-hidden="true">&middot;</span>
              <span className="font-mono">{weightValid ? formatWeight(weightGrams) : "—"}</span>
              {estimatedDistanceKm !== null && (
                <span className="inline-flex items-center gap-1 rounded-full bg-accent px-2 py-0.5 font-mono text-[11px] font-medium text-primary">
                  <Navigation className="size-3" aria-hidden="true" /> ~{estimatedDistanceKm} km
                </span>
              )}
            </div>
          </Ticket>
          {error && (
            <Alert variant="destructive" className="mt-3">
              <AlertCircle aria-hidden="true" />
              <AlertTitle>Booking Error</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
        </aside>
      </form>
    </div>
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
