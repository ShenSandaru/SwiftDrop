"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import {
  AlertTriangle,
  ArrowLeftRight,
  ArrowRight,
  Box,
  Calculator,
  CalendarDays,
  Check,
  CheckCircle2,
  Coins,
  Compass,
  Info,
  Layers,
  Loader2,
  MapPin,
  Package,
  RotateCcw,
  Scale,
  ShieldCheck,
  Sliders,
  Truck,
  Zap,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ThemedSelect } from "@/components/ui/select";
import { formatApiError } from "@/lib/api";
import { getQuote, type ParcelSize, type PriceQuote } from "@/lib/delivery";
import {
  getPricingConfig,
  listAdminZones,
  updatePricingConfig,
  type ZoneAdmin,
} from "@/lib/pricing";
import {
  calculateDynamicRatePerKm,
  calculateFairRoutePrice,
  calculateRoadDistanceKm,
  resolveDistrictCoordinates,
  MAX_ISLAND_DISTANCE_KM,
} from "@/lib/districts";
import { cn } from "cn";

const DEFAULT_CONFIG_FORM = {
  standardBaseFee: "350.00",
  freeKmAllowance: "30.00",
  ratePerKm: "1.33",
  maxCap: "1000.00",
  perKgFee: "50.00",
  smallSizeSurcharge: "0.00",
  mediumSizeSurcharge: "50.00",
  largeSizeSurcharge: "150.00",
  currency: "LKR",
};

export default function AdminPricingPage() {
  const [tab, setTab] = useState<"config" | "simulator">("config");
  const [zones, setZones] = useState<ZoneAdmin[] | null>(null);

  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Global Tariff Configuration Form
  const [configForm, setConfigForm] = useState(DEFAULT_CONFIG_FORM);
  const [savingConfig, setSavingConfig] = useState(false);

  // Rate Simulator State
  const [simPickup, setSimPickup] = useState("");
  const [simDropoff, setSimDropoff] = useState("");
  const [simSize, setSimSize] = useState<ParcelSize>("MEDIUM");
  const [simWeightGrams, setSimWeightGrams] = useState(1500);
  const [quote, setQuote] = useState<PriceQuote | null>(null);
  const [simError, setSimError] = useState<string | null>(null);

  const today = useMemo(
    () =>
      new Intl.DateTimeFormat("en-US", {
        timeZone: "Asia/Colombo",
        dateStyle: "medium",
      }).format(new Date()),
    [],
  );

  const loadData = useCallback(() => {
    Promise.all([listAdminZones(), getPricingConfig().catch(() => null)])
      .then(([loadedZones, loadedConfig]) => {
        setZones(loadedZones);
        if (loadedConfig) {
          setConfigForm({
            standardBaseFee: String(loadedConfig.standardBaseFee),
            freeKmAllowance: String(loadedConfig.freeKmAllowance),
            ratePerKm: String(loadedConfig.ratePerKm),
            maxCap: String(loadedConfig.maxCap),
            perKgFee: String(loadedConfig.perKgFee),
            smallSizeSurcharge: String(loadedConfig.smallSizeSurcharge),
            mediumSizeSurcharge: String(loadedConfig.mediumSizeSurcharge),
            largeSizeSurcharge: String(loadedConfig.largeSizeSurcharge),
            currency: loadedConfig.currency || "LKR",
          });
        }
        setError(null);
        if (loadedZones.length > 0) {
          setSimPickup((prev) => prev || loadedZones[0].id);
          setSimDropoff((prev) => prev || (loadedZones.length > 1 ? loadedZones[1].id : loadedZones[0].id));
        }
      })
      .catch((failure) => setError(formatApiError(failure)));
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Zone select options
  const activeZoneSelectOptions = useMemo(() => {
    return (zones ?? [])
      .filter((z) => z.active)
      .map((z) => ({
        value: z.id,
        label: `${z.name} (${z.code})`,
        badge: z.code,
        description: "Active delivery zone",
      }));
  }, [zones]);

  // Distance calculator helper between two zone IDs
  const getZoneDistanceKm = useCallback(
    (originId: string, destId: string): number | null => {
      if (!zones || !originId || !destId) return null;
      if (originId === destId) return 0;

      const oz = zones.find((z) => z.id === originId);
      const dz = zones.find((z) => z.id === destId);
      if (!oz || !dz) return null;

      const oCoords = resolveDistrictCoordinates(oz.code || oz.name);
      const dCoords = resolveDistrictCoordinates(dz.code || dz.name);
      if (!oCoords || !dCoords) return null;

      return calculateRoadDistanceKm(oCoords.lat, oCoords.lng, dCoords.lat, dCoords.lng);
    },
    [zones],
  );

  // Active Simulator Route Distance
  const simDistanceResult = useMemo(() => {
    const km = getZoneDistanceKm(simPickup, simDropoff);
    if (km === null) return null;

    const base = parseFloat(configForm.standardBaseFee) || 350;
    const freeKm = parseFloat(configForm.freeKmAllowance) || 30;
    const perKm = parseFloat(configForm.ratePerKm) || 2.0;
    const cap = parseFloat(configForm.maxCap) || 1000;

    return calculateFairRoutePrice(km, {
      standardBaseFee: base,
      freeKmAllowance: freeKm,
      ratePerKm: perKm,
      maxCap: cap,
    });
  }, [simPickup, simDropoff, configForm, getZoneDistanceKm]);

  // Live Complete Quotation (Route + Weight + Size) calculated instantly in real-time
  const liveSimulationQuote = useMemo(() => {
    if (!simDistanceResult) return null;

    const routeFee = simDistanceResult.totalSuggestedBaseFee;
    const chargeableKg = Math.max(1, Math.ceil(simWeightGrams / 1000));
    const perKgRate = parseFloat(configForm.perKgFee) || 50;
    const weightFee = chargeableKg * perKgRate;

    const sizeSurcharge =
      simSize === "SMALL"
        ? parseFloat(configForm.smallSizeSurcharge) || 0
        : simSize === "MEDIUM"
        ? parseFloat(configForm.mediumSizeSurcharge) || 50
        : parseFloat(configForm.largeSizeSurcharge) || 150;

    const totalFee = routeFee + weightFee + sizeSurcharge;

    return {
      routeFee,
      chargeableKg,
      weightFee,
      sizeSurcharge,
      totalFee,
      currency: configForm.currency || "LKR",
    };
  }, [simDistanceResult, simWeightGrams, simSize, configForm]);

  // Unified quote display: uses backend quote if available, otherwise seamlessly uses live client formula
  const displayQuote = useMemo(() => {
    if (quote) {
      return {
        baseFee: quote.breakdown.baseFee,
        weightFee: quote.breakdown.weightFee,
        sizeFee: quote.breakdown.sizeSurcharge,
        totalAmount: quote.amount,
        currency: quote.currency,
        isLiveServer: true,
        engineRef: quote.pricingRuleId,
      };
    }
    if (liveSimulationQuote) {
      return {
        baseFee: liveSimulationQuote.routeFee.toFixed(2),
        weightFee: liveSimulationQuote.weightFee.toFixed(2),
        sizeFee: liveSimulationQuote.sizeSurcharge.toFixed(2),
        totalAmount: liveSimulationQuote.totalFee.toFixed(2),
        currency: liveSimulationQuote.currency,
        isLiveServer: false,
        engineRef: "Universal Distance Tariff Engine",
      };
    }
    return null;
  }, [quote, liveSimulationQuote]);

  // Dynamic calculation helpers: automatically recalibrate ratePerKm when base fee or max cap changes
  function updateBaseFee(val: string) {
    const base = parseFloat(val) || 0;
    const cap = parseFloat(configForm.maxCap) || 1000;
    const freeKm = parseFloat(configForm.freeKmAllowance) || 30;
    const dynamicRate = calculateDynamicRatePerKm(base, cap, freeKm);
    setConfigForm((prev) => ({
      ...prev,
      standardBaseFee: val,
      ratePerKm: dynamicRate.toFixed(2),
    }));
  }

  function updateMaxCap(val: string) {
    const cap = parseFloat(val) || 0;
    const base = parseFloat(configForm.standardBaseFee) || 350;
    const freeKm = parseFloat(configForm.freeKmAllowance) || 30;
    const dynamicRate = calculateDynamicRatePerKm(base, cap, freeKm);
    setConfigForm((prev) => ({
      ...prev,
      maxCap: val,
      ratePerKm: dynamicRate.toFixed(2),
    }));
  }

  function updateFreeKm(val: string) {
    const freeKm = parseFloat(val) || 0;
    const base = parseFloat(configForm.standardBaseFee) || 350;
    const cap = parseFloat(configForm.maxCap) || 1000;
    const dynamicRate = calculateDynamicRatePerKm(base, cap, freeKm);
    setConfigForm((prev) => ({
      ...prev,
      freeKmAllowance: val,
      ratePerKm: dynamicRate.toFixed(2),
    }));
  }

  // Save Global Tariff Configuration
  async function handleSaveConfig(e: FormEvent) {
    e.preventDefault();
    setSavingConfig(true);
    setError(null);
    setSuccess(null);
    try {
      await updatePricingConfig({
        standardBaseFee: parseFloat(configForm.standardBaseFee),
        freeKmAllowance: parseFloat(configForm.freeKmAllowance),
        ratePerKm: parseFloat(configForm.ratePerKm),
        maxCap: parseFloat(configForm.maxCap),
        perKgFee: parseFloat(configForm.perKgFee),
        smallSizeSurcharge: parseFloat(configForm.smallSizeSurcharge),
        mediumSizeSurcharge: parseFloat(configForm.mediumSizeSurcharge),
        largeSizeSurcharge: parseFloat(configForm.largeSizeSurcharge),
        currency: configForm.currency,
      });
      setSuccess("Global tariff parameters updated successfully. All island route prices now reflect these rates.");
      loadData();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setSavingConfig(false);
    }
  }

  function handleResetConfig() {
    setConfigForm(DEFAULT_CONFIG_FORM);
    setSuccess("Reset to recommended standard defaults. Click 'Save Tariff Rates' to apply.");
  }

  // Swap origin and destination in simulator
  function handleSwapSimulatorRoute() {
    setSimPickup(simDropoff);
    setSimDropoff(simPickup);
    setQuote(null);
  }

  // Run Quote Simulator
  async function handleSimulate(e: FormEvent) {
    e.preventDefault();
    if (!simPickup || !simDropoff) return;
    setSimError(null);
    try {
      const result = await getQuote(simPickup, simDropoff, simSize, simWeightGrams);
      setQuote(result);
    } catch (failure) {
      setSimError(formatApiError(failure));
      setQuote(null);
    }
  }

  const pickupZone = useMemo(() => (zones ?? []).find((z) => z.id === simPickup), [zones, simPickup]);
  const dropoffZone = useMemo(() => (zones ?? []).find((z) => z.id === simDropoff), [zones, simDropoff]);

  return (
    <div className="space-y-6">
      {/* Top Operations Header Bar */}
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-card p-4 shadow-xs sm:flex-row sm:items-center sm:justify-between sm:p-5">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1.5 font-bold text-primary">
              <Coins className="size-3.5" aria-hidden="true" />
              Rate &amp; Pricing Control Tower
            </span>
            <span aria-hidden="true">&middot;</span>
            <span className="inline-flex items-center gap-1.5">
              <CalendarDays className="size-3.5" aria-hidden="true" />
              <time suppressHydrationWarning>{today}</time>
            </span>
            <span aria-hidden="true">&middot;</span>
            <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
              Automated Distance Engine
            </span>
          </div>
          <p className="text-sm text-muted-foreground">
            Coordinate island-wide automated fair distance delivery pricing, local allowances, and extra distance tariffs.
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs text-muted-foreground shrink-0 self-start sm:self-center">
          <span className="relative flex size-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full size-2 bg-emerald-500"></span>
          </span>
          <span className="font-mono text-[11px] text-muted-foreground">Live synced</span>
        </div>
      </div>

      {/* Island Pricing & Tariff Hero Banner (Matches Hubs & Dispatch Control) */}
      <section className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-primary via-primary/95 to-primary/90 px-6 py-7 text-primary-foreground sm:px-8 sm:py-8 shadow-sm">
        <svg
          className="pointer-events-none absolute -right-12 -top-10 h-64 w-96 opacity-20"
          viewBox="0 0 320 220"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="M10 190 C 80 120, 120 200, 180 120 S 260 40, 310 20"
            stroke="currentColor"
            strokeWidth="3.5"
            strokeDasharray="10 10"
            strokeLinecap="round"
          />
          <circle cx="10" cy="190" r="7" fill="currentColor" />
          <circle cx="180" cy="120" r="6" fill="currentColor" />
          <circle cx="310" cy="20" r="8" fill="currentColor" />
        </svg>
        <div className="relative max-w-4xl space-y-3">
          <p className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.15em] opacity-85">
            <Coins className="size-4" aria-hidden="true" /> Island-Wide Distance Tariff Command
          </p>
          <h2 className="text-2xl font-extrabold leading-tight sm:text-3xl">
            Dynamic Distance Pricing &amp; Tariff Control
          </h2>
          <p className="text-sm opacity-90 sm:text-base leading-relaxed max-w-2xl">
            All 25 districts across Sri Lanka calculate automatically based on physical road distance. Local deliveries under 30 km incur zero mileage surcharge, while cross-island deliveries are protected by a maximum delivery fee cap.
          </p>
          <div className="flex flex-wrap items-center gap-2.5 pt-2">
            <Button
              type="button"
              size="sm"
              onClick={() => setTab("config")}
              className={cn(
                "rounded-full font-semibold transition-colors px-3.5",
                tab === "config"
                  ? "bg-primary-foreground text-primary hover:bg-primary-foreground/90 shadow-xs"
                  : "border border-primary-foreground/40 bg-primary-foreground/10 text-primary-foreground hover:bg-primary-foreground/20",
              )}
            >
              <Sliders className="size-4 mr-1.5" /> Tariff Parameters
            </Button>
            <Button
              type="button"
              size="sm"
              onClick={() => setTab("simulator")}
              className={cn(
                "rounded-full font-semibold transition-colors px-3.5",
                tab === "simulator"
                  ? "bg-primary-foreground text-primary hover:bg-primary-foreground/90 shadow-xs"
                  : "border border-primary-foreground/40 bg-primary-foreground/10 text-primary-foreground hover:bg-primary-foreground/20",
              )}
            >
              <Calculator className="size-4 mr-1.5" /> Rate Simulator
            </Button>
            <div className="inline-flex items-center gap-1.5 rounded-full border border-primary-foreground/30 bg-primary-foreground/10 px-3.5 py-1.5 text-xs font-medium text-primary-foreground whitespace-nowrap">
              <Zap className="size-3.5" /> Model: {configForm.freeKmAllowance}km Base &middot; LKR {configForm.ratePerKm}/km &middot; Max LKR {configForm.maxCap} Cap
            </div>
          </div>
        </div>
      </section>

      {/* Alerts */}
      {error && (
        <div className="rounded-lg border border-destructive/30 bg-destructive/10 p-4 text-xs md:text-sm text-destructive flex items-center gap-2.5">
          <AlertTriangle className="size-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}
      {success && (
        <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4 text-xs md:text-sm text-emerald-700 dark:text-emerald-400 flex items-center gap-2.5">
          <CheckCircle2 className="size-4 shrink-0" />
          <span>{success}</span>
        </div>
      )}

      {/* Quick Metrics Cards (matching Hubs & Dispatch Fleet Control) */}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="flex h-full flex-col justify-between gap-3 rounded-lg border border-border bg-card p-4 shadow-2xs">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0 flex-1">
              <p className="text-xs font-medium text-muted-foreground">Local base rate</p>
              <p className="mt-1 font-mono text-xl font-bold text-foreground whitespace-nowrap sm:text-2xl">
                LKR {parseFloat(configForm.standardBaseFee).toFixed(2)}
              </p>
            </div>
            <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
              <MapPin className="size-4" aria-hidden="true" />
            </span>
          </div>
          <p className="text-xs text-muted-foreground">
            Covers urban radius up to {configForm.freeKmAllowance} km
          </p>
        </div>

        <div className="flex h-full flex-col justify-between gap-3 rounded-lg border border-border bg-card p-4 shadow-2xs">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0 flex-1">
              <p className="text-xs font-medium text-muted-foreground">Extra distance rate</p>
              <p className="mt-1 font-mono text-xl font-bold text-foreground whitespace-nowrap sm:text-2xl">
                LKR {parseFloat(configForm.ratePerKm).toFixed(2)}
                <span className="text-xs font-normal text-muted-foreground whitespace-nowrap"> / km</span>
              </p>
            </div>
            <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
              <Truck className="size-4" aria-hidden="true" />
            </span>
          </div>
          <p className="text-xs text-muted-foreground">
            Billable per km beyond {configForm.freeKmAllowance} km
          </p>
        </div>

        <div className="flex h-full flex-col justify-between gap-3 rounded-lg border border-border bg-card p-4 shadow-2xs">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0 flex-1">
              <p className="text-xs font-medium text-muted-foreground">Island maximum cap</p>
              <p className="mt-1 font-mono text-xl font-bold text-emerald-600 dark:text-emerald-400 whitespace-nowrap sm:text-2xl">
                LKR {parseFloat(configForm.maxCap).toFixed(2)}
              </p>
            </div>
            <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-emerald-500/20 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              <ShieldCheck className="size-4" aria-hidden="true" />
            </span>
          </div>
          <p className="text-xs text-muted-foreground">
            Guaranteed maximum delivery ceiling
          </p>
        </div>

        <div className="flex h-full flex-col justify-between gap-3 rounded-lg border border-border bg-card p-4 shadow-2xs">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0 flex-1">
              <p className="text-xs font-medium text-muted-foreground">Base weight rate</p>
              <p className="mt-1 font-mono text-xl font-bold text-foreground whitespace-nowrap sm:text-2xl">
                LKR {parseFloat(configForm.perKgFee).toFixed(2)}
                <span className="text-xs font-normal text-muted-foreground whitespace-nowrap"> / kg</span>
              </p>
            </div>
            <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-accent text-primary">
              <Scale className="size-4" aria-hidden="true" />
            </span>
          </div>
          <p className="text-xs text-muted-foreground">
            Every started kg (1.2 kg billed as 2 kg)
          </p>
        </div>
      </div>

      {/* Section Header */}
      <div className="space-y-1 pt-1">
        <p className="text-xs font-semibold uppercase tracking-wider text-primary">
          DISTANCE TARIFF &amp; RATE MATRIX
        </p>
        <h3 className="text-xl font-bold tracking-tight text-foreground">
          Tariff Configuration &amp; Interactive Simulator
        </h3>
        <p className="text-xs text-muted-foreground">
          Manage dynamic distance pricing rules, adjust volumetric surcharges, and simulate quotes.
        </p>
      </div>

      {/* Pill Tabs (matches Hubs tabs exactly) */}
      <div className="flex items-center gap-2 border-b border-border pb-3">
        <button
          type="button"
          onClick={() => setTab("config")}
          className={cn(
            "inline-flex items-center gap-2 rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-colors cursor-pointer",
            tab === "config"
              ? "bg-primary text-primary-foreground shadow-xs"
              : "text-muted-foreground hover:bg-muted/60 hover:text-foreground",
          )}
        >
          <Sliders className="size-3.5" />
          Tariff Parameters
        </button>
        <button
          type="button"
          onClick={() => setTab("simulator")}
          className={cn(
            "inline-flex items-center gap-2 rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-colors cursor-pointer",
            tab === "simulator"
              ? "bg-primary text-primary-foreground shadow-xs"
              : "text-muted-foreground hover:bg-muted/60 hover:text-foreground",
          )}
        >
          <Calculator className="size-3.5" />
          Rate Simulator
        </button>
      </div>

      {/* Tab 1: Global Tariff Configuration & Pricing Conditions */}
      {tab === "config" && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Left Column: Tariff Parameter Editor */}
          <div className="lg:col-span-7 space-y-6">
            <form onSubmit={handleSaveConfig} className="rounded-xl border border-border bg-card p-5 md:p-6 shadow-xs space-y-6">
              <div className="border-b border-border pb-4">
                <h3 className="text-base font-semibold tracking-tight text-foreground flex items-center gap-2">
                  <Sliders className="size-4 text-primary" />
                  Tariff Engine Configuration
                </h3>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Configure island-wide base rates, extra distance thresholds, and parcel handling surcharges.
                </p>
              </div>

              {/* Section 1: Distance & Road Transit */}
              <div className="space-y-4">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-primary">
                  <Truck className="size-3.5" />
                  <span>Base &amp; Extra Distance Tariff</span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {/* Standard Base Fee */}
                  <div className="space-y-1.5">
                    <Label htmlFor="standardBaseFee" className="text-xs font-medium text-foreground">
                      Standard Base Fee
                    </Label>
                    <div className="relative">
                      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-xs font-mono text-muted-foreground">
                        LKR
                      </span>
                      <Input
                        id="standardBaseFee"
                        type="number"
                        step="0.01"
                        min="0"
                        value={configForm.standardBaseFee}
                        onChange={(e) => updateBaseFee(e.target.value)}
                        required
                        className="font-mono text-sm pl-11"
                      />
                    </div>
                    <p className="text-[11px] text-muted-foreground">Local urban base transport fee.</p>
                  </div>

                  {/* Free Distance Allowance */}
                  <div className="space-y-1.5">
                    <Label htmlFor="freeKmAllowance" className="text-xs font-medium text-foreground">
                      Local Free Radius Allowance
                    </Label>
                    <div className="relative">
                      <Input
                        id="freeKmAllowance"
                        type="number"
                        step="1"
                        min="0"
                        value={configForm.freeKmAllowance}
                        onChange={(e) => updateFreeKm(e.target.value)}
                        required
                        className="font-mono text-sm pr-9"
                      />
                      <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-xs font-mono text-muted-foreground">
                        km
                      </span>
                    </div>
                    <p className="text-[11px] text-muted-foreground">Deliveries within this radius pay 0 km extra surcharge.</p>
                  </div>

                  {/* Island Maximum Cap */}
                  <div className="space-y-1.5">
                    <Label htmlFor="maxCap" className="text-xs font-medium text-foreground">
                      Island Maximum Delivery Cap
                    </Label>
                    <div className="relative">
                      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-xs font-mono text-muted-foreground">
                        LKR
                      </span>
                      <Input
                        id="maxCap"
                        type="number"
                        step="0.01"
                        min="0"
                        value={configForm.maxCap}
                        onChange={(e) => updateMaxCap(e.target.value)}
                        required
                        className="font-mono text-sm pl-11 text-emerald-600 dark:text-emerald-400 font-semibold"
                      />
                    </div>
                    <p className="text-[11px] text-muted-foreground">
                      Ceiling reached at furthest island distance ({MAX_ISLAND_DISTANCE_KM} km).
                    </p>
                  </div>

                  {/* Calculated Extra Distance Rate */}
                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="ratePerKm" className="text-xs font-medium text-foreground">
                        Extra Distance Rate
                      </Label>
                      <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-mono text-primary font-medium">
                        Auto-calibrated
                      </span>
                    </div>
                    <div className="relative">
                      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-xs font-mono text-muted-foreground">
                        LKR
                      </span>
                      <Input
                        id="ratePerKm"
                        type="number"
                        step="0.01"
                        min="0"
                        value={configForm.ratePerKm}
                        onChange={(e) => setConfigForm({ ...configForm, ratePerKm: e.target.value })}
                        required
                        className="font-mono text-sm pl-11 pr-12 bg-muted/20"
                      />
                      <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-xs font-mono text-muted-foreground">
                        / km
                      </span>
                    </div>
                    <p className="text-[11px] text-muted-foreground">
                      Prorated over remaining ({MAX_ISLAND_DISTANCE_KM - Number(configForm.freeKmAllowance || 30)} km) island corridor.
                    </p>
                  </div>
                </div>
              </div>

              {/* Section 2: Weight Rate Parameters */}
              <div className="space-y-4 border-t border-border pt-5">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-primary">
                  <Scale className="size-3.5" />
                  <span>Weight Tariff &amp; Currency</span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="perKgFee" className="text-xs font-medium text-foreground">
                      Cost Per Started Kilogram
                    </Label>
                    <div className="relative">
                      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-xs font-mono text-muted-foreground">
                        LKR
                      </span>
                      <Input
                        id="perKgFee"
                        type="number"
                        step="0.01"
                        min="0"
                        value={configForm.perKgFee}
                        onChange={(e) => setConfigForm({ ...configForm, perKgFee: e.target.value })}
                        required
                        className="font-mono text-sm pl-11 pr-12"
                      />
                      <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-xs font-mono text-muted-foreground">
                        / kg
                      </span>
                    </div>
                    <p className="text-[11px] text-muted-foreground">Rounded up to next whole kg (1.2 kg billed as 2 kg).</p>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="currency" className="text-xs font-medium text-foreground">
                      System Billing Currency
                    </Label>
                    <Input
                      id="currency"
                      type="text"
                      maxLength={3}
                      value={configForm.currency}
                      onChange={(e) => setConfigForm({ ...configForm, currency: e.target.value.toUpperCase() })}
                      required
                      className="font-mono text-sm uppercase bg-muted/20"
                    />
                    <p className="text-[11px] text-muted-foreground">Standard 3-letter currency code (ISO 4217).</p>
                  </div>
                </div>
              </div>

              {/* Section 3: Volumetric & Parcel Size Surcharges */}
              <div className="space-y-4 border-t border-border pt-5">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-primary">
                  <Package className="size-3.5" />
                  <span>Volumetric &amp; Parcel Size Surcharges</span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <div className="space-y-1.5">
                    <Label htmlFor="smallSizeSurcharge" className="text-xs font-medium text-foreground">
                      Small Surcharge
                    </Label>
                    <div className="relative">
                      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-xs font-mono text-muted-foreground">
                        +LKR
                      </span>
                      <Input
                        id="smallSizeSurcharge"
                        type="number"
                        step="0.01"
                        min="0"
                        value={configForm.smallSizeSurcharge}
                        onChange={(e) => setConfigForm({ ...configForm, smallSizeSurcharge: e.target.value })}
                        required
                        className="font-mono text-sm pl-12"
                      />
                    </div>
                    <p className="text-[10px] text-muted-foreground">Envelopes &amp; documents</p>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="mediumSizeSurcharge" className="text-xs font-medium text-foreground">
                      Medium Surcharge
                    </Label>
                    <div className="relative">
                      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-xs font-mono text-muted-foreground">
                        +LKR
                      </span>
                      <Input
                        id="mediumSizeSurcharge"
                        type="number"
                        step="0.01"
                        min="0"
                        value={configForm.mediumSizeSurcharge}
                        onChange={(e) => setConfigForm({ ...configForm, mediumSizeSurcharge: e.target.value })}
                        required
                        className="font-mono text-sm pl-12"
                      />
                    </div>
                    <p className="text-[10px] text-muted-foreground">Shoeboxes &amp; standard</p>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="largeSizeSurcharge" className="text-xs font-medium text-foreground">
                      Large Surcharge
                    </Label>
                    <div className="relative">
                      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-xs font-mono text-muted-foreground">
                        +LKR
                      </span>
                      <Input
                        id="largeSizeSurcharge"
                        type="number"
                        step="0.01"
                        min="0"
                        value={configForm.largeSizeSurcharge}
                        onChange={(e) => setConfigForm({ ...configForm, largeSizeSurcharge: e.target.value })}
                        required
                        className="font-mono text-sm pl-12"
                      />
                    </div>
                    <p className="text-[10px] text-muted-foreground">Heavy bulky cartons</p>
                  </div>
                </div>
              </div>

              {/* Form Action Toolbar */}
              <div className="flex flex-col-reverse sm:flex-row sm:items-center sm:justify-between gap-3 border-t border-border pt-4">
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <span className="size-1.5 rounded-full bg-emerald-500 inline-block" />
                  <span>Live synchronized with 25-district distance engine</span>
                </div>
                <div className="flex items-center gap-2.5 self-end sm:self-auto">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleResetConfig}
                    disabled={savingConfig}
                    className="text-xs gap-1.5 h-8 font-medium cursor-pointer border-border hover:bg-muted/50"
                  >
                    <RotateCcw className="size-3.5" />
                    Reset Defaults
                  </Button>
                  <Button
                    type="submit"
                    disabled={savingConfig}
                    className="text-xs font-semibold gap-1.5 h-8 px-4 cursor-pointer shadow-xs"
                  >
                    {savingConfig ? (
                      <Loader2 className="size-3.5 animate-spin" />
                    ) : (
                      <Check className="size-3.5" />
                    )}
                    Save Tariff Rates
                  </Button>
                </div>
              </div>
            </form>
          </div>

          {/* Right Column: Pricing Formula & Charging Specifications */}
          <div className="lg:col-span-5 space-y-4">
            <div className="rounded-xl border border-border bg-card p-5 md:p-6 shadow-xs space-y-4">
              <div className="flex items-center gap-2 border-b border-border pb-3.5">
                <Compass className="size-4 text-primary" />
                <div>
                  <h3 className="text-base font-semibold tracking-tight text-foreground">
                    Tariff Model &amp; Specifications
                  </h3>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Automated island-wide road distance calculation rules.
                  </p>
                </div>
              </div>

              {/* Mathematical Formula Card */}
              <div className="rounded-lg border border-border bg-muted/30 p-3 space-y-1.5">
                <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Universal Fare Model
                </span>
                <div className="rounded bg-background px-2.5 py-1.5 font-mono text-[11px] text-foreground border border-border/60 tracking-tight">
                  Delivery Fee = Base + (Extra km &times; LKR {configForm.ratePerKm})
                </div>
                <p className="text-[11px] text-muted-foreground tracking-tight">
                  Total = Delivery Fee + (⌈kg⌉ &times; LKR {configForm.perKgFee}) + Size Fee
                </p>
              </div>

              {/* Policy Specifications List */}
              <div className="space-y-3 text-xs">
                {/* Rule 1: Urban Radius */}
                <div className="flex items-start gap-3 rounded-lg border border-border/70 p-3">
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary mt-0.5">
                    <MapPin className="size-3.5" />
                  </span>
                  <div className="space-y-0.5 flex-1 min-w-0">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-foreground">Urban Radius Allowance</span>
                      <span className="font-mono text-primary font-medium">Flat Base Only</span>
                    </div>
                    <p className="text-[11px] text-muted-foreground leading-relaxed">
                      Deliveries under {configForm.freeKmAllowance} km incur zero extra mileage surcharge; customer pays only the flat base fee (LKR {parseFloat(configForm.standardBaseFee).toFixed(2)}).
                    </p>
                  </div>
                </div>

                {/* Rule 2: Extra Distance Rate */}
                <div className="flex items-start gap-3 rounded-lg border border-border/70 p-3">
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary mt-0.5">
                    <Truck className="size-3.5" />
                  </span>
                  <div className="space-y-0.5 flex-1 min-w-0">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-foreground">Extra Distance Rate</span>
                      <span className="font-mono text-primary font-medium">LKR {configForm.ratePerKm} / km</span>
                    </div>
                    <p className="text-[11px] text-muted-foreground leading-relaxed">
                      Distances beyond {configForm.freeKmAllowance} km scale at the extra distance rate across the island, avoiding sharp step functions.
                    </p>
                  </div>
                </div>

                {/* Rule 3: Maximum Island Ceiling */}
                <div className="flex items-start gap-3 rounded-lg border border-emerald-500/20 bg-emerald-500/5 p-3">
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 mt-0.5">
                    <ShieldCheck className="size-3.5" />
                  </span>
                  <div className="space-y-0.5 flex-1 min-w-0">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-foreground">Island Maximum Ceiling</span>
                      <span className="font-mono text-emerald-600 dark:text-emerald-400 font-bold">
                        Max LKR {parseFloat(configForm.maxCap).toFixed(2)}
                      </span>
                    </div>
                    <p className="text-[11px] text-muted-foreground leading-relaxed">
                      Calibrated to the longest corridor in Sri Lanka ({MAX_ISLAND_DISTANCE_KM} km). Guarantees reliable consumer pricing.
                    </p>
                  </div>
                </div>

                {/* Rule 4: Surcharges */}
                <div className="flex items-start gap-3 rounded-lg border border-border/70 p-3">
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary mt-0.5">
                    <Scale className="size-3.5" />
                  </span>
                  <div className="space-y-0.5 flex-1 min-w-0">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-foreground">Weight &amp; Volumetric Handling</span>
                      <span className="font-mono text-primary font-medium">+ Surcharges</span>
                    </div>
                    <p className="text-[11px] text-muted-foreground leading-relaxed">
                      Billed per started kilogram (LKR {configForm.perKgFee}/kg) plus fixed handling surcharge for selected physical package tier.
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* Quick Link to Simulator */}
            <div className="rounded-xl border border-border bg-card p-4 flex items-center justify-between gap-3 shadow-2xs">
              <div className="space-y-0.5">
                <span className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                  <Calculator className="size-3.5 text-primary" />
                  Verify Rates with Simulator
                </span>
                <p className="text-[11px] text-muted-foreground">
                  Test quotes between any two zones in Sri Lanka using these parameters.
                </p>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setTab("simulator")}
                className="shrink-0 text-xs gap-1 border-primary/30 text-primary hover:bg-primary/10 cursor-pointer h-8"
              >
                Open Simulator
                <ArrowRight className="size-3" />
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: Professional Rate Simulator & Route Inspector */}
      {tab === "simulator" && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Left Column: Shipment Parameters Form */}
          <div className="lg:col-span-7 space-y-6">
            <form onSubmit={handleSimulate} className="rounded-xl border border-border bg-card p-5 md:p-6 shadow-xs space-y-5">
              <div className="border-b border-border pb-3.5">
                <h3 className="text-base font-semibold tracking-tight text-foreground flex items-center gap-2">
                  <Calculator className="size-4 text-primary" />
                  Route &amp; Package Simulator
                </h3>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Configure shipment route and package parameters for real-time quotation.
                </p>
              </div>

              {/* Origin & Destination Selectors with Swap Button in the middle (strictly horizontal: left -> middle -> right) */}
              <div className="flex items-start gap-2 sm:gap-3 w-full">
                {/* Left Side: Pickup Zone (Origin) */}
                <div className="flex-1 min-w-0 space-y-1.5">
                  <Label className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                    <MapPin className="size-3.5 text-primary" />
                    Pickup Zone (Origin)
                  </Label>
                  <ThemedSelect
                    options={activeZoneSelectOptions}
                    value={simPickup}
                    onChange={(val) => {
                      setSimPickup(val);
                      setQuote(null);
                    }}
                    placeholder="Select pickup zone"
                  />
                  {pickupZone && (
                    <p className="text-[11px] text-muted-foreground truncate">
                      District code: <span className="font-mono font-medium text-foreground">{pickupZone.code}</span>
                    </p>
                  )}
                </div>

                {/* Middle: Swap Button */}
                <div className="pt-[22px] shrink-0">
                  <Button
                    type="button"
                    variant="outline"
                    size="icon-sm"
                    onClick={handleSwapSimulatorRoute}
                    className="size-9 rounded-full border border-border bg-card text-muted-foreground hover:bg-accent hover:text-foreground hover:border-primary/40 shadow-2xs transition-colors cursor-pointer"
                    title="Swap pickup and dropoff routes"
                    aria-label="Swap pickup and dropoff routes"
                  >
                    <ArrowLeftRight className="size-4" />
                  </Button>
                </div>

                {/* Right Side: Dropoff Zone (Destination) */}
                <div className="flex-1 min-w-0 space-y-1.5">
                  <Label className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                    <MapPin className="size-3.5 text-emerald-500" />
                    Dropoff Zone (Destination)
                  </Label>
                  <ThemedSelect
                    options={activeZoneSelectOptions}
                    value={simDropoff}
                    onChange={(val) => {
                      setSimDropoff(val);
                      setQuote(null);
                    }}
                    placeholder="Select dropoff zone"
                  />
                  {dropoffZone && (
                    <p className="text-[11px] text-muted-foreground truncate">
                      District code: <span className="font-mono font-medium text-foreground">{dropoffZone.code}</span>
                    </p>
                  )}
                </div>
              </div>

              {/* Parcel Size Cards */}
              <div className="space-y-2">
                <Label className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                  <Package className="size-3.5 text-primary" />
                  Parcel Size Tier
                </Label>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                  {(
                    [
                      {
                        size: "SMALL" as ParcelSize,
                        label: "Small",
                        icon: Package,
                        desc: "Envelopes & documents up to 1kg",
                        surcharge: configForm.smallSizeSurcharge,
                      },
                      {
                        size: "MEDIUM" as ParcelSize,
                        label: "Medium",
                        icon: Box,
                        desc: "Shoeboxes & standard packages up to 5kg",
                        surcharge: configForm.mediumSizeSurcharge,
                      },
                      {
                        size: "LARGE" as ParcelSize,
                        label: "Large",
                        icon: Layers,
                        desc: "Heavy cartons & bulky cargo up to 30kg",
                        surcharge: configForm.largeSizeSurcharge,
                      },
                    ] as const
                  ).map((item) => {
                    const Icon = item.icon;
                    const isSelected = simSize === item.size;
                    return (
                      <button
                        key={item.size}
                        type="button"
                        onClick={() => {
                          setSimSize(item.size);
                          setQuote(null);
                        }}
                        className={cn(
                          "flex flex-col text-left p-3 rounded-lg border transition-colors cursor-pointer",
                          isSelected
                            ? "border-primary bg-primary/5 shadow-2xs ring-1 ring-primary/30"
                            : "border-border bg-card hover:bg-muted/40",
                        )}
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-semibold text-xs text-foreground flex items-center gap-1.5">
                            <Icon className={cn("size-3.5", isSelected ? "text-primary" : "text-muted-foreground")} />
                            {item.label}
                          </span>
                          <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-mono text-muted-foreground">
                            +LKR {parseFloat(item.surcharge).toFixed(0)}
                          </span>
                        </div>
                        <p className="mt-1 text-[11px] text-muted-foreground leading-snug">{item.desc}</p>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Weight Selector */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label htmlFor="simWeight" className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                    <Scale className="size-3.5 text-primary" />
                    Parcel Weight
                  </Label>
                  <span className="text-xs text-muted-foreground">
                    {(simWeightGrams / 1000).toFixed(2)} kg &middot;{" "}
                    <span className="font-mono font-medium text-foreground">
                      Billable: {Math.max(1, Math.ceil(simWeightGrams / 1000))} kg
                    </span>
                  </span>
                </div>

                <div className="flex flex-wrap sm:flex-nowrap items-center gap-2">
                  <div className="relative w-36 shrink-0">
                    <Input
                      id="simWeight"
                      type="number"
                      min={1}
                      max={30000}
                      step="any"
                      value={simWeightGrams}
                      onChange={(e) => {
                        setSimWeightGrams(Math.max(1, Math.min(30000, parseInt(e.target.value) || 1)));
                        setQuote(null);
                      }}
                      className="font-mono text-sm pr-7"
                    />
                    <span className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">
                      g
                    </span>
                  </div>
                  {/* Preset Pills */}
                  <div className="flex flex-wrap items-center gap-1.5">
                    {[
                      { label: "500g", val: 500 },
                      { label: "1.5 kg", val: 1500 },
                      { label: "3 kg", val: 3000 },
                      { label: "5 kg", val: 5000 },
                      { label: "10 kg", val: 10000 },
                    ].map((pill) => (
                      <Button
                        key={pill.val}
                        type="button"
                        variant={simWeightGrams === pill.val ? "default" : "outline"}
                        size="sm"
                        onClick={() => {
                          setSimWeightGrams(pill.val);
                          setQuote(null);
                        }}
                        className={cn(
                          "h-8 text-xs font-mono px-2.5 cursor-pointer",
                          simWeightGrams === pill.val && "shadow-xs",
                        )}
                      >
                        {pill.label}
                      </Button>
                    ))}
                  </div>
                </div>
              </div>

              {/* Clean Footer Notice */}
              <div className="flex items-center justify-between border-t border-border pt-3 text-[11px] text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  <span className="size-1.5 rounded-full bg-emerald-500 inline-block" />
                  Live quotation active
                </span>
                <span className="font-mono">Real-time simulation</span>
              </div>
            </form>
          </div>

          {/* Right Column: Quote Receipt Breakdown */}
          <div className="lg:col-span-5 space-y-4">
            <div className="rounded-xl border border-border/80 bg-card p-5 md:p-6 shadow-xs space-y-4">
              <div className="flex items-center justify-between border-b border-border pb-3">
                <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                  <Package className="size-3.5 text-primary" />
                  Official Delivery Quote
                </span>
                {displayQuote && (
                  <span
                    className={cn(
                      "rounded-full px-2 py-0.5 text-[10px] font-medium",
                      displayQuote.isLiveServer
                        ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                        : "bg-primary/10 text-primary",
                    )}
                  >
                    {displayQuote.isLiveServer ? "Live API Verified" : "Live Formula Calculated"}
                  </span>
                )}
              </div>

              {simError && (
                <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-2.5 text-xs text-amber-700 dark:text-amber-400 flex items-center gap-2">
                  <Info className="size-4 shrink-0" />
                  <span>Displaying live formula pricing below (backend server restart pending for direct API verification).</span>
                </div>
              )}

              {displayQuote ? (
                <div className="space-y-4">
                  {/* Route Summary Pill */}
                  <div className="rounded-lg border border-border/70 bg-muted/40 p-3 flex items-center justify-between text-xs">
                    <div className="flex items-center gap-2 font-medium text-foreground">
                      <span>{pickupZone?.name || "Origin"}</span>
                      <ArrowRight className="size-3 text-muted-foreground" />
                      <span>{dropoffZone?.name || "Destination"}</span>
                    </div>
                    {simDistanceResult && (
                      <span className="font-mono text-muted-foreground font-semibold">
                        {simDistanceResult.distanceKm} km
                      </span>
                    )}
                  </div>

                  {/* Itemized Receipt Breakdown */}
                  <div className="space-y-3 border-t border-border pt-3">
                    <p className="text-xs font-semibold text-foreground uppercase tracking-wider">Itemized Breakdown</p>
                    <div className="space-y-2.5 text-xs">
                      {/* Base Distance Fee */}
                      <div className="flex justify-between">
                        <span className="text-muted-foreground">
                          Route Distance Fee {simDistanceResult && simDistanceResult.distanceKm <= 30 ? "(Local ≤30km)" : ""}
                        </span>
                        <span className="font-mono font-medium text-foreground">
                          {displayQuote.currency} {displayQuote.baseFee}
                        </span>
                      </div>

                      {/* Weight Fee */}
                      <div className="flex justify-between">
                        <span className="text-muted-foreground">
                          Weight Charge ({Math.max(1, Math.ceil(simWeightGrams / 1000))} kg billable @ {displayQuote.currency} {configForm.perKgFee}/kg)
                        </span>
                        <span className="font-mono font-medium text-foreground">
                          {displayQuote.currency} {displayQuote.weightFee}
                        </span>
                      </div>

                      {/* Size Surcharge */}
                      <div className="flex justify-between">
                        <span className="text-muted-foreground">
                          Size Surcharge ({simSize.toLowerCase()} package)
                        </span>
                        <span className="font-mono font-medium text-foreground">
                          {displayQuote.currency} {displayQuote.sizeFee}
                        </span>
                      </div>

                      {/* Total Quote (Single clean border line) */}
                      <div className="flex items-center justify-between border-t border-border pt-3 mt-3 font-bold text-base">
                        <span className="text-foreground">Total Delivery Fee</span>
                        <span className="font-mono text-primary text-xl">
                          {displayQuote.currency} {displayQuote.totalAmount}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Protection Badges */}
                  {simDistanceResult && simDistanceResult.isCapped && (
                    <div className="flex items-center gap-2 rounded-lg border border-emerald-500/20 bg-emerald-500/10 p-2.5 text-xs text-emerald-700 dark:text-emerald-400">
                      <ShieldCheck className="size-4 shrink-0" />
                      <span>Fair Pricing Policy: Route delivery fee is protected at the island ceiling of LKR {parseFloat(configForm.maxCap).toFixed(2)}.</span>
                    </div>
                  )}

                  {simDistanceResult && simDistanceResult.billableKm === 0 && (
                    <div className="flex items-center gap-2 rounded-lg border border-primary/20 bg-primary/10 p-2.5 text-xs text-primary">
                      <CheckCircle2 className="size-4 shrink-0" />
                      <span>Local Radius Benefit: Distance is under {configForm.freeKmAllowance} km. Zero extra distance surcharge applied.</span>
                    </div>
                  )}

                  <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground pt-1">
                    <Info className="size-3.5 shrink-0" />
                    <span>Engine Reference: {displayQuote.engineRef}</span>
                  </div>
                </div>
              ) : !simError ? (
                <div className="py-14 text-center text-xs text-muted-foreground space-y-2.5">
                  <div className="flex size-12 mx-auto items-center justify-center rounded-full bg-muted/60 text-muted-foreground/60">
                    <Calculator className="size-6" />
                  </div>
                  <p className="max-w-[240px] mx-auto">
                    Select pickup and dropoff zones to simulate live delivery rates and inspect customer quotes in real time.
                  </p>
                </div>
              ) : null}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
