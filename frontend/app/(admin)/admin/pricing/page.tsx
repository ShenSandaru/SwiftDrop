"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowLeftRight,
  ArrowRight,
  Box,
  Calculator,
  CalendarDays,
  CheckCircle2,
  Coins,
  Compass,
  Info,
  Layers,
  Loader2,
  MapPin,
  Package,
  RotateCcw,
  Save,
  Scale,
  ShieldCheck,
  Sliders,
  Sparkles,
  Truck,
  Warehouse,
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
  const [simulating, setSimulating] = useState(false);
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
    setSuccess("Reset to recommended standard defaults. Click 'Save Tariff Parameters' to apply.");
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
    setSimulating(true);
    setSimError(null);
    try {
      const result = await getQuote(simPickup, simDropoff, simSize, simWeightGrams);
      setQuote(result);
    } catch (failure) {
      setSimError(formatApiError(failure));
      setQuote(null);
    } finally {
      setSimulating(false);
    }
  }

  const pickupZone = useMemo(() => (zones ?? []).find((z) => z.id === simPickup), [zones, simPickup]);
  const dropoffZone = useMemo(() => (zones ?? []).find((z) => z.id === simDropoff), [zones, simDropoff]);

  return (
    <div className="space-y-6">
      {/* Top Header - Control Tower Styling */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-border pb-5">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2.5">
              <Coins className="size-6 text-primary" />
              Automated Pricing & Tariff Engine
            </h1>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-xs font-medium text-emerald-600 dark:text-emerald-400">
              <span className="size-1.5 rounded-full bg-emerald-500 animate-pulse" />
              Live distance engine
            </span>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Dynamic fair delivery pricing for Sri Lanka: 30 km local radius allowance, per-km line-haul, and island maximum ceiling.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <div className="hidden sm:flex items-center gap-1.5 rounded-md border border-border/80 bg-muted/40 px-3 py-1.5 text-xs text-muted-foreground">
            <CalendarDays className="size-3.5" />
            <span>{today}</span>
          </div>
          <Link href="/admin/hubs">
            <Button variant="outline" size="sm" className="gap-1.5 text-xs">
              <Warehouse className="size-3.5" />
              Hubs & Service Zones
            </Button>
          </Link>
        </div>
      </div>

      {/* Dispatch Hero Banner with Route Transit Graphic */}
      <div className="relative overflow-hidden rounded-xl border border-primary/20 bg-gradient-to-r from-teal-950/40 via-background to-teal-950/20 p-5 md:p-6 shadow-sm">
        {/* Background Transit SVG Graphic */}
        <div className="pointer-events-none absolute inset-0 opacity-15 overflow-hidden">
          <svg className="w-full h-full" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="none" viewBox="0 0 800 200">
            <path d="M 0 100 Q 200 20, 400 100 T 800 100" fill="none" stroke="currentColor" strokeWidth="2.5" strokeDasharray="6 6" className="text-primary" />
            <path d="M 0 140 Q 300 180, 600 60 T 800 120" fill="none" stroke="currentColor" strokeWidth="1.5" className="text-primary/60" />
            <circle cx="200" cy="60" r="5" className="fill-primary" />
            <circle cx="400" cy="100" r="7" className="fill-primary" />
            <circle cx="600" cy="60" r="5" className="fill-primary" />
          </svg>
        </div>

        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-1.5">
            <div className="flex items-center gap-2">
              <span className="rounded-full bg-primary/20 px-2.5 py-0.5 text-[11px] font-semibold text-primary uppercase tracking-wider">
                Automated Fair Formula
              </span>
              <span className="text-xs text-muted-foreground font-mono">
                No manual route rules needed
              </span>
            </div>
            <h2 className="text-lg md:text-xl font-bold tracking-tight text-foreground">
              Universal Dynamic Distance Tariff Engine
            </h2>
            <p className="text-xs md:text-sm text-muted-foreground max-w-2xl">
              All 25 districts across Sri Lanka calculate automatically based on physical road distance. Local deliveries under 30 km incur zero mileage surcharge, while cross-island deliveries are protected by a maximum delivery fee cap.
            </p>
          </div>

          <div className="flex flex-col sm:flex-row items-start sm:items-center gap-2.5 shrink-0">
            <div className="flex items-center gap-2 rounded-lg border border-primary/30 bg-primary/10 px-3.5 py-2 text-xs font-mono text-primary shadow-sm">
              <Zap className="size-4 shrink-0 text-primary" />
              <span>
                Model: {configForm.freeKmAllowance}km Base · LKR {configForm.ratePerKm}/km · Max LKR {configForm.maxCap} Cap
              </span>
            </div>
          </div>
        </div>
      </div>

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

      {/* KPI Metric Summary Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3.5">
        <div className="rounded-xl border border-border/80 bg-card p-4 shadow-xs">
          <div className="flex items-center justify-between text-muted-foreground">
            <span className="text-xs font-medium uppercase tracking-wider">Local Base Rate</span>
            <MapPin className="size-4 text-primary" />
          </div>
          <div className="mt-2 text-2xl font-bold tracking-tight text-foreground">
            LKR {parseFloat(configForm.standardBaseFee).toFixed(2)}
          </div>
          <p className="mt-1 text-[11px] text-muted-foreground">
            Covers urban radius up to {configForm.freeKmAllowance} km
          </p>
        </div>

        <div className="rounded-xl border border-border/80 bg-card p-4 shadow-xs">
          <div className="flex items-center justify-between text-muted-foreground">
            <span className="text-xs font-medium uppercase tracking-wider">Extra Line-Haul Rate</span>
            <Truck className="size-4 text-primary" />
          </div>
          <div className="mt-2 text-2xl font-bold tracking-tight text-foreground">
            LKR {parseFloat(configForm.ratePerKm).toFixed(2)}
            <span className="text-xs font-normal text-muted-foreground"> / km</span>
          </div>
          <p className="mt-1 text-[11px] text-muted-foreground">
            Billable per km beyond {configForm.freeKmAllowance} km
          </p>
        </div>

        <div className="rounded-xl border border-border/80 bg-card p-4 shadow-xs">
          <div className="flex items-center justify-between text-muted-foreground">
            <span className="text-xs font-medium uppercase tracking-wider">Island Maximum Cap</span>
            <ShieldCheck className="size-4 text-emerald-500" />
          </div>
          <div className="mt-2 text-2xl font-bold tracking-tight text-emerald-600 dark:text-emerald-400">
            LKR {parseFloat(configForm.maxCap).toFixed(2)}
          </div>
          <p className="mt-1 text-[11px] text-muted-foreground">
            Guaranteed maximum delivery ceiling
          </p>
        </div>

        <div className="rounded-xl border border-border/80 bg-card p-4 shadow-xs">
          <div className="flex items-center justify-between text-muted-foreground">
            <span className="text-xs font-medium uppercase tracking-wider">Base Weight Rate</span>
            <Scale className="size-4 text-primary" />
          </div>
          <div className="mt-2 text-2xl font-bold tracking-tight text-foreground">
            LKR {parseFloat(configForm.perKgFee).toFixed(2)}
            <span className="text-xs font-normal text-muted-foreground"> / kg</span>
          </div>
          <p className="mt-1 text-[11px] text-muted-foreground">
            Every started kg (1.2 kg billed as 2 kg)
          </p>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="flex border-b border-border">
        <button
          type="button"
          onClick={() => setTab("config")}
          className={cn(
            "flex items-center gap-2 border-b-2 px-5 py-3 text-sm font-medium transition-colors cursor-pointer",
            tab === "config"
              ? "border-primary text-primary"
              : "border-transparent text-muted-foreground hover:border-border hover:text-foreground",
          )}
        >
          <Sliders className="size-4" />
          Tariff Parameters & Pricing Rules
        </button>
        <button
          type="button"
          onClick={() => setTab("simulator")}
          className={cn(
            "flex items-center gap-2 border-b-2 px-5 py-3 text-sm font-medium transition-colors cursor-pointer",
            tab === "simulator"
              ? "border-primary text-primary"
              : "border-transparent text-muted-foreground hover:border-border hover:text-foreground",
          )}
        >
          <Calculator className="size-4" />
          Interactive Rate Simulator
          {simDistanceResult && (
            <span className="rounded-full bg-primary/20 px-2 py-0.5 text-[10px] text-primary">
              {simDistanceResult.distanceKm} km
            </span>
          )}
        </button>
      </div>

      {/* Tab 1: Global Tariff Configuration & Pricing Conditions */}
      {tab === "config" && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Left Column: Tariff Parameter Editor */}
          <div className="lg:col-span-7 space-y-6">
            <form onSubmit={handleSaveConfig} className="rounded-xl border border-border/80 bg-card p-5 md:p-6 shadow-xs space-y-6">
              <div className="flex items-center justify-between border-b border-border pb-4">
                <div>
                  <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
                    <Sliders className="size-4 text-primary" />
                    Global Tariff Parameters
                  </h3>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Modifying these values instantly recalibrates delivery rates for all 25 districts across the island.
                  </p>
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={handleResetConfig}
                  className="text-xs text-muted-foreground hover:text-foreground gap-1"
                  title="Reset to recommended defaults"
                >
                  <RotateCcw className="size-3.5" />
                  Defaults
                </Button>
              </div>

              {/* Group 1: Distance & Base Parameters */}
              <div className="space-y-3.5">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-primary">
                  <Truck className="size-3.5" />
                  <span>1. Distance & Line-Haul Parameters</span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                  <div className="space-y-1.5">
                    <Label htmlFor="standardBaseFee" className="text-xs font-medium text-foreground">
                      Standard Base Fee (LKR)
                    </Label>
                    <Input
                      id="standardBaseFee"
                      type="number"
                      step="0.01"
                      min="0"
                      value={configForm.standardBaseFee}
                      onChange={(e) => updateBaseFee(e.target.value)}
                      required
                      className="font-mono text-sm"
                    />
                    <p className="text-[11px] text-muted-foreground">Minimum fee for local urban transport.</p>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="freeKmAllowance" className="text-xs font-medium text-foreground">
                      Free Distance Allowance (km)
                    </Label>
                    <Input
                      id="freeKmAllowance"
                      type="number"
                      step="1"
                      min="0"
                      value={configForm.freeKmAllowance}
                      onChange={(e) => updateFreeKm(e.target.value)}
                      required
                      className="font-mono text-sm"
                    />
                    <p className="text-[11px] text-muted-foreground">Deliveries under this radius pay 0 km surcharge.</p>
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="ratePerKm" className="text-xs font-medium text-foreground">
                        Extra Line-Haul Rate (LKR / km)
                      </Label>
                      <span className="rounded bg-primary/10 px-1.5 py-0.2 text-[10px] font-mono text-primary font-medium">
                        ⚡ Auto-scaled
                      </span>
                    </div>
                    <Input
                      id="ratePerKm"
                      type="number"
                      step="0.01"
                      min="0"
                      value={configForm.ratePerKm}
                      onChange={(e) => setConfigForm({ ...configForm, ratePerKm: e.target.value })}
                      required
                      className="font-mono text-sm bg-muted/20"
                    />
                    <p className="text-[11px] text-muted-foreground">
                      (Max Cap - Base Fee) / ({MAX_ISLAND_DISTANCE_KM}km - {configForm.freeKmAllowance}km) = LKR {configForm.ratePerKm}/km
                    </p>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="maxCap" className="text-xs font-medium text-foreground">
                      Island Maximum Delivery Cap (LKR)
                    </Label>
                    <Input
                      id="maxCap"
                      type="number"
                      step="0.01"
                      min="0"
                      value={configForm.maxCap}
                      onChange={(e) => updateMaxCap(e.target.value)}
                      required
                      className="font-mono text-sm text-emerald-600 dark:text-emerald-400 font-semibold"
                    />
                    <p className="text-[11px] text-muted-foreground">Reached only at furthest island distance ({MAX_ISLAND_DISTANCE_KM} km).</p>
                  </div>
                </div>
              </div>

              {/* Group 2: Weight Rate */}
              <div className="space-y-3.5 border-t border-border pt-4">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-primary">
                  <Scale className="size-3.5" />
                  <span>2. Weight Rate Parameters</span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                  <div className="space-y-1.5">
                    <Label htmlFor="perKgFee" className="text-xs font-medium text-foreground">
                      Cost Per Started Kilogram (LKR / kg)
                    </Label>
                    <Input
                      id="perKgFee"
                      type="number"
                      step="0.01"
                      min="0"
                      value={configForm.perKgFee}
                      onChange={(e) => setConfigForm({ ...configForm, perKgFee: e.target.value })}
                      required
                      className="font-mono text-sm"
                    />
                    <p className="text-[11px] text-muted-foreground">Rounded up: 1.2 kg billed as 2 kg.</p>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="currency" className="text-xs font-medium text-foreground">
                      Currency Code
                    </Label>
                    <Input
                      id="currency"
                      type="text"
                      maxLength={3}
                      value={configForm.currency}
                      onChange={(e) => setConfigForm({ ...configForm, currency: e.target.value.toUpperCase() })}
                      required
                      className="font-mono text-sm uppercase"
                    />
                    <p className="text-[11px] text-muted-foreground">Standard 3-letter currency (LKR).</p>
                  </div>
                </div>
              </div>

              {/* Group 3: Physical Parcel Size Surcharges */}
              <div className="space-y-3.5 border-t border-border pt-4">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-primary">
                  <Package className="size-3.5" />
                  <span>3. Volumetric & Parcel Size Surcharges</span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
                  <div className="space-y-1.5">
                    <Label htmlFor="smallSizeSurcharge" className="text-xs font-medium text-foreground">
                      Small Surcharge (LKR)
                    </Label>
                    <Input
                      id="smallSizeSurcharge"
                      type="number"
                      step="0.01"
                      min="0"
                      value={configForm.smallSizeSurcharge}
                      onChange={(e) => setConfigForm({ ...configForm, smallSizeSurcharge: e.target.value })}
                      required
                      className="font-mono text-sm"
                    />
                    <p className="text-[10px] text-muted-foreground">Envelopes & documents</p>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="mediumSizeSurcharge" className="text-xs font-medium text-foreground">
                      Medium Surcharge (LKR)
                    </Label>
                    <Input
                      id="mediumSizeSurcharge"
                      type="number"
                      step="0.01"
                      min="0"
                      value={configForm.mediumSizeSurcharge}
                      onChange={(e) => setConfigForm({ ...configForm, mediumSizeSurcharge: e.target.value })}
                      required
                      className="font-mono text-sm"
                    />
                    <p className="text-[10px] text-muted-foreground">Standard shoebox / carton</p>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="largeSizeSurcharge" className="text-xs font-medium text-foreground">
                      Large Surcharge (LKR)
                    </Label>
                    <Input
                      id="largeSizeSurcharge"
                      type="number"
                      step="0.01"
                      min="0"
                      value={configForm.largeSizeSurcharge}
                      onChange={(e) => setConfigForm({ ...configForm, largeSizeSurcharge: e.target.value })}
                      required
                      className="font-mono text-sm"
                    />
                    <p className="text-[10px] text-muted-foreground">Heavy bulky freight</p>
                  </div>
                </div>
              </div>

              <div className="pt-2 flex justify-end">
                <Button type="submit" disabled={savingConfig} className="gap-2 text-sm px-6">
                  {savingConfig ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
                  Save Tariff Parameters
                </Button>
              </div>
            </form>
          </div>

          {/* Right Column: Pricing Formula & Charging Conditions */}
          <div className="lg:col-span-5 space-y-5">
            <div className="rounded-xl border border-border/80 bg-card p-5 md:p-6 shadow-xs space-y-4">
              <div className="flex items-center gap-2 border-b border-border pb-3">
                <Compass className="size-4 text-primary" />
                <h3 className="text-base font-semibold text-foreground">
                  Charging Conditions & Rules
                </h3>
              </div>
              <p className="text-xs text-muted-foreground">
                How SwiftDrop computes fair prices automatically without requiring manual route entries:
              </p>

              <div className="space-y-3.5 text-xs">
                {/* Condition 1 */}
                <div className="rounded-lg border border-border/70 bg-muted/30 p-3.5 space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-foreground flex items-center gap-1.5">
                      <span className="flex size-5 items-center justify-center rounded-full bg-primary/20 text-[10px] font-bold text-primary">
                        1
                      </span>
                      Local Urban Allowance (≤ {configForm.freeKmAllowance} km)
                    </span>
                    <span className="font-mono text-primary font-medium">Base Fee Only</span>
                  </div>
                  <p className="text-muted-foreground text-[11px] leading-relaxed">
                    Deliveries within the same district or neighboring zones under {configForm.freeKmAllowance} km incur zero extra distance fee. The customer pays only the flat base fee (LKR {parseFloat(configForm.standardBaseFee).toFixed(2)}).
                  </p>
                </div>

                {/* Condition 2 */}
                <div className="rounded-lg border border-border/70 bg-muted/30 p-3.5 space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-foreground flex items-center gap-1.5">
                      <span className="flex size-5 items-center justify-center rounded-full bg-primary/20 text-[10px] font-bold text-primary">
                        2
                      </span>
                      Proportional Line-Haul (&gt; {configForm.freeKmAllowance} km)
                    </span>
                    <span className="font-mono text-primary font-medium">+ LKR {configForm.ratePerKm}/km</span>
                  </div>
                  <p className="text-muted-foreground text-[11px] leading-relaxed">
                    The line-haul rate is dynamically proportional. Changing the base fee or max cap instantly updates the per-km rate so routes scale smoothly across the island without premature clamping.
                  </p>
                  <div className="rounded bg-background/80 p-2 font-mono text-[11px] text-foreground/80">
                    Rate/km = (Max Cap - Base) / ({MAX_ISLAND_DISTANCE_KM}km - {configForm.freeKmAllowance}km)
                  </div>
                </div>

                {/* Condition 3 */}
                <div className="rounded-lg border border-emerald-500/20 bg-emerald-500/10 p-3.5 space-y-1.5 text-emerald-800 dark:text-emerald-300">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold flex items-center gap-1.5">
                      <span className="flex size-5 items-center justify-center rounded-full bg-emerald-500/20 text-[10px] font-bold text-emerald-600 dark:text-emerald-400">
                        3
                      </span>
                      Island Maximum Ceiling Protection
                    </span>
                    <span className="font-mono font-bold">Max LKR {configForm.maxCap}</span>
                  </div>
                  <p className="text-[11px] leading-relaxed opacity-90">
                    The maximum delivery cap of LKR {parseFloat(configForm.maxCap).toFixed(2)} is calibrated to the longest hub corridor in Sri Lanka (Matara &harr; Jaffna ~{MAX_ISLAND_DISTANCE_KM} km). Every route under {MAX_ISLAND_DISTANCE_KM} km scales proportionally below this cap.
                  </p>
                </div>

                {/* Condition 4 */}
                <div className="rounded-lg border border-border/70 bg-muted/30 p-3.5 space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-foreground flex items-center gap-1.5">
                      <span className="flex size-5 items-center justify-center rounded-full bg-primary/20 text-[10px] font-bold text-primary">
                        4
                      </span>
                      Weight & Volumetric Surcharge
                    </span>
                    <span className="font-mono text-primary font-medium">+ Surcharges</span>
                  </div>
                  <p className="text-muted-foreground text-[11px] leading-relaxed">
                    Every started kilogram is billed at LKR {configForm.perKgFee}/kg, and the physical package size tier adds its fixed handling fee.
                  </p>
                  <div className="rounded bg-background/80 p-2 font-mono text-[11px] text-foreground/80">
                    Total = Delivery Fee + (⌈kg⌉ × {configForm.perKgFee}) + Size Fee
                  </div>
                </div>
              </div>
            </div>

            {/* Quick Link to Simulator */}
            <div className="rounded-xl border border-primary/20 bg-primary/5 p-4 flex items-center justify-between gap-3">
              <div className="space-y-0.5">
                <span className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                  <Sparkles className="size-3.5 text-primary" />
                  Want to verify these rates?
                </span>
                <p className="text-[11px] text-muted-foreground">
                  Switch to the Rate Simulator to test live quotes between any two zones in Sri Lanka.
                </p>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setTab("simulator")}
                className="shrink-0 text-xs gap-1 border-primary/30 text-primary hover:bg-primary/10"
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
            <form onSubmit={handleSimulate} className="rounded-xl border border-border/80 bg-card p-5 md:p-6 shadow-xs space-y-5">
              <div className="flex items-center justify-between border-b border-border pb-3.5">
                <div>
                  <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
                    <Calculator className="size-4 text-primary" />
                    Shipment Route & Package Parameters
                  </h3>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Select origin, destination, weight, and size to simulate customer booking quotes in real time.
                  </p>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleSwapSimulatorRoute}
                  className="gap-1.5 text-xs"
                  title="Swap pickup and dropoff routes"
                >
                  <ArrowLeftRight className="size-3.5" />
                  Swap
                </Button>
              </div>

              {/* Origin & Destination Selectors */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
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
                    <p className="text-[11px] text-muted-foreground">
                      District code: <span className="font-mono font-medium text-foreground">{pickupZone.code}</span>
                    </p>
                  )}
                </div>

                <div className="space-y-1.5">
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
                    <p className="text-[11px] text-muted-foreground">
                      District code: <span className="font-mono font-medium text-foreground">{dropoffZone.code}</span>
                    </p>
                  )}
                </div>
              </div>

              {/* Road Transit Telemetry Chip */}
              {simDistanceResult && (
                <div className="rounded-lg border border-primary/20 bg-primary/5 p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div className="flex items-center gap-2.5">
                    <div className="flex size-8 items-center justify-center rounded-md bg-primary/10 text-primary">
                      <Truck className="size-4" />
                    </div>
                    <div>
                      <div className="text-xs font-semibold text-foreground flex items-center gap-2">
                        <span>Road Transit: {simDistanceResult.distanceKm} km</span>
                        <span className="rounded-full bg-primary/20 px-2 py-0.5 text-[10px] text-primary">
                          {simDistanceResult.distanceKm <= 30
                            ? "Local Radius (0 km extra)"
                            : `${simDistanceResult.billableKm} km Line-Haul`}
                        </span>
                      </div>
                      <p className="text-[11px] text-muted-foreground">
                        {simDistanceResult.isCapped
                          ? "Long-haul distance exceeds cap: Island Max Ceiling (LKR 1,000) will apply."
                          : simDistanceResult.billableKm === 0
                          ? "Within local 30 km allowance: standard base fee applies."
                          : `Base fee + ${simDistanceResult.billableKm} km @ LKR ${configForm.ratePerKm}/km.`}
                      </p>
                    </div>
                  </div>
                  <div className="flex flex-col sm:flex-row items-end sm:items-center gap-3.5 shrink-0">
                    <div className="text-right">
                      <span className="text-[10px] text-muted-foreground uppercase font-semibold block">Route Distance Fee</span>
                      <div className="text-xs font-mono text-muted-foreground font-medium">
                        LKR {simDistanceResult.totalSuggestedBaseFee.toFixed(2)}
                      </div>
                    </div>
                    <div className="text-right sm:border-l sm:border-primary/20 sm:pl-3.5">
                      <span className="text-[10px] text-primary uppercase font-bold block">Total Delivery Fee</span>
                      <div className="text-base font-bold font-mono text-primary">
                        LKR {displayQuote ? displayQuote.totalAmount : simDistanceResult.totalSuggestedBaseFee.toFixed(2)}
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* Parcel Size Cards */}
              <div className="space-y-2">
                <Label className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                  <Package className="size-3.5 text-primary" />
                  Parcel Physical Size
                </Label>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
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
                          "flex flex-col text-left p-3.5 rounded-lg border transition-all cursor-pointer",
                          isSelected
                            ? "border-primary bg-primary/10 shadow-xs ring-1 ring-primary/40"
                            : "border-border/80 bg-card hover:bg-muted/30 hover:border-border",
                        )}
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-semibold text-xs text-foreground flex items-center gap-1.5">
                            <Icon className="size-3.5 text-primary" />
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
              <div className="space-y-2.5">
                <div className="flex items-center justify-between">
                  <Label htmlFor="simWeight" className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                    <Scale className="size-3.5 text-primary" />
                    Parcel Weight: {(simWeightGrams / 1000).toFixed(2)} kg ({simWeightGrams} grams)
                  </Label>
                  <span className="text-[11px] font-mono text-primary font-medium">
                    Billable: {Math.max(1, Math.ceil(simWeightGrams / 1000))} kg
                  </span>
                </div>

                <div className="flex items-center gap-2">
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
                    className="font-mono text-sm max-w-[140px]"
                  />
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
                        className="h-8 text-xs font-mono px-2.5"
                      >
                        {pill.label}
                      </Button>
                    ))}
                  </div>
                </div>
              </div>

              <div className="pt-2">
                <Button
                  type="submit"
                  disabled={simulating || !simPickup || !simDropoff}
                  className="w-full gap-2 text-sm font-semibold py-2.5"
                >
                  {simulating ? <Loader2 className="size-4 animate-spin" /> : <Zap className="size-4" />}
                  Calculate Delivery Quote
                </Button>
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
                  <div className="space-y-2 border-t border-border pt-3">
                    <p className="text-xs font-semibold text-foreground uppercase tracking-wider">Itemized Breakdown</p>
                    <div className="divide-y divide-border/60 text-xs">
                      {/* Base Distance Fee */}
                      <div className="flex justify-between py-2">
                        <span className="text-muted-foreground">
                          Route Distance Fee {simDistanceResult && simDistanceResult.distanceKm <= 30 ? "(Local ≤30km)" : ""}
                        </span>
                        <span className="font-mono font-medium text-foreground">
                          {displayQuote.currency} {displayQuote.baseFee}
                        </span>
                      </div>

                      {/* Weight Fee */}
                      <div className="flex justify-between py-2">
                        <span className="text-muted-foreground">
                          Weight Charge ({Math.max(1, Math.ceil(simWeightGrams / 1000))} kg billable @ {displayQuote.currency} {configForm.perKgFee}/kg)
                        </span>
                        <span className="font-mono font-medium text-foreground">
                          {displayQuote.currency} {displayQuote.weightFee}
                        </span>
                      </div>

                      {/* Size Surcharge */}
                      <div className="flex justify-between py-2">
                        <span className="text-muted-foreground">
                          Size Surcharge ({simSize.toLowerCase()} package)
                        </span>
                        <span className="font-mono font-medium text-foreground">
                          {displayQuote.currency} {displayQuote.sizeFee}
                        </span>
                      </div>

                      {/* Total Quote */}
                      <div className="flex justify-between py-3 font-bold text-base border-t border-border mt-1">
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
                      <span>Local Radius Benefit: Distance is under {configForm.freeKmAllowance} km. Zero line-haul surcharge applied.</span>
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
                    Select pickup and dropoff zones, choose package dimensions, and click <span className="font-medium text-foreground">&ldquo;Calculate Delivery Quote&rdquo;</span>.
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
