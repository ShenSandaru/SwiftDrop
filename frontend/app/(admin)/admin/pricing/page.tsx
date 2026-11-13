"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import {
  AlertTriangle,
  ArrowRight,
  Calculator,
  CheckCircle2,
  Coins,
  Edit2,
  Loader2,
  MapPin,
  Plus,
  RefreshCw,
  Trash2,
  Warehouse,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ConfirmationDialog } from "@/components/ui/confirmation-dialog";
import { formatApiError } from "@/lib/api";
import { listHubs, type Hub } from "@/lib/hubs";
import { getQuote, type ParcelSize, PARCEL_SIZES, type PriceQuote } from "@/lib/delivery";
import {
  createPricingRule,
  createZone,
  deleteZone,
  listAdminZones,
  listPricingRules,
  updatePricingRule,
  updateZone,
  type PricingRuleAdmin,
  type ZoneAdmin,
} from "@/lib/pricing";
import { listDistricts, type District } from "@/lib/districts";

const EMPTY_ZONE_FORM = { code: "", name: "" };
const EMPTY_RULE_FORM = {
  originZoneId: "",
  destinationZoneId: "",
  baseFee: "400.00",
  perKgFee: "100.00",
  smallSizeSurcharge: "0.00",
  mediumSizeSurcharge: "50.00",
  largeSizeSurcharge: "150.00",
  currency: "LKR",
};

export default function AdminPricingPage() {
  const [tab, setTab] = useState<"zones" | "rules" | "simulator">("zones");
  const [zones, setZones] = useState<ZoneAdmin[] | null>(null);
  const [rules, setRules] = useState<PricingRuleAdmin[] | null>(null);
  const [hubs, setHubs] = useState<Hub[] | null>(null);
  const [districts, setDistricts] = useState<District[]>([]);

  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Zone form states
  const [zoneForm, setZoneForm] = useState(EMPTY_ZONE_FORM);
  const [selectedHubId, setSelectedHubId] = useState("");
  const [selectedSubZoneName, setSelectedSubZoneName] = useState("");
  const [savingZone, setSavingZone] = useState(false);
  const [editingZone, setEditingZone] = useState<ZoneAdmin | null>(null);
  const [zoneToDelete, setZoneToDelete] = useState<ZoneAdmin | null>(null);
  const [deletingZone, setDeletingZone] = useState(false);

  // Pricing rule form states
  const [ruleForm, setRuleForm] = useState(EMPTY_RULE_FORM);
  const [savingRule, setSavingRule] = useState(false);
  const [editingRule, setEditingRule] = useState<PricingRuleAdmin | null>(null);

  // Rate simulator states
  const [simPickup, setSimPickup] = useState("");
  const [simDropoff, setSimDropoff] = useState("");
  const [simSize, setSimSize] = useState<ParcelSize>("MEDIUM");
  const [simWeightGrams, setSimWeightGrams] = useState(1500);
  const [quote, setQuote] = useState<PriceQuote | null>(null);
  const [simulating, setSimulating] = useState(false);
  const [simError, setSimError] = useState<string | null>(null);

  const loadData = useCallback(() => {
    Promise.all([listAdminZones(), listPricingRules(), listHubs(), listDistricts()])
      .then(([loadedZones, loadedRules, loadedHubs, loadedDistricts]) => {
        setZones(loadedZones);
        setRules(loadedRules);
        setHubs(loadedHubs);
        setDistricts(loadedDistricts);
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

  const servedZoneMap = useMemo(() => {
    const map = new Map<string, Hub>();
    (hubs ?? []).forEach((h) => {
      if (h.active && h.zoneId) {
        map.set(h.zoneId, h);
      }
    });
    return map;
  }, [hubs]);

  const existingZoneCodes = useMemo(
    () => new Set((zones ?? []).map((z) => z.code.trim().toUpperCase())),
    [zones],
  );

  const existingZoneNames = useMemo(
    () => new Set((zones ?? []).map((z) => z.name.trim().toLowerCase())),
    [zones],
  );

  const selectedHub = useMemo(
    () => (hubs ?? []).find((h) => h.id === selectedHubId),
    [hubs, selectedHubId],
  );

  const currentDistrict = useMemo(() => {
    if (!selectedHub) return undefined;
    const hubCity = selectedHub.city.trim().toLowerCase();
    const hubCode = selectedHub.code.trim().toLowerCase();
    return districts.find(
      (d) => d.name.toLowerCase() === hubCity || d.code.toLowerCase() === hubCode
    );
  }, [selectedHub, districts]);

  const availableSubZones = useMemo(() => {
    if (!currentDistrict) return [];
    return currentDistrict.zones;
  }, [currentDistrict]);

  function handleHubChange(hubId: string) {
    setSelectedHubId(hubId);
    setSelectedSubZoneName("");
    setZoneForm(EMPTY_ZONE_FORM);
  }

  function handleSubZoneChange(subZoneName: string) {
    setSelectedSubZoneName(subZoneName);
    const matched = availableSubZones.find((sz) => sz.name === subZoneName);
    if (matched) {
      setZoneForm({
        name: matched.name,
        code: matched.code,
      });
    } else {
      setZoneForm((prev) => ({
        ...prev,
        name: subZoneName,
      }));
    }
  }

  // Zone actions
  async function handleCreateZone(e: FormEvent) {
    e.preventDefault();
    if (existingZoneCodes.has(zoneForm.code.trim().toUpperCase())) {
      setError(`Zone code "${zoneForm.code}" already exists.`);
      return;
    }
    if (existingZoneNames.has(zoneForm.name.trim().toLowerCase())) {
      setError(`Zone "${zoneForm.name}" already exists.`);
      return;
    }
    setSavingZone(true);
    setError(null);
    setSuccess(null);
    try {
      await createZone({ ...zoneForm, hubId: selectedHubId || null });
      setZoneForm(EMPTY_ZONE_FORM);
      setSelectedSubZoneName("");
      setSuccess(`Zone ${zoneForm.code} (${zoneForm.name}) added and assigned to hub successfully.`);
      loadData();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setSavingZone(false);
    }
  }

  async function handleUpdateZone(e: FormEvent) {
    e.preventDefault();
    if (!editingZone) return;
    setSavingZone(true);
    setError(null);
    setSuccess(null);
    try {
      await updateZone(editingZone.id, {
        code: editingZone.code,
        name: editingZone.name,
        active: editingZone.active,
        version: editingZone.version,
      });
      setSuccess(`Zone ${editingZone.code} updated.`);
      setEditingZone(null);
      loadData();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setSavingZone(false);
    }
  }

  async function toggleZoneActive(zone: ZoneAdmin) {
    setError(null);
    setSuccess(null);
    try {
      await updateZone(zone.id, {
        code: zone.code,
        name: zone.name,
        active: !zone.active,
        version: zone.version,
      });
      setSuccess(`Zone ${zone.code} is now ${!zone.active ? "active" : "inactive"}.`);
      loadData();
    } catch (failure) {
      setError(formatApiError(failure));
    }
  }

  async function confirmDeleteZone() {
    if (!zoneToDelete) return;
    setDeletingZone(true);
    setError(null);
    setSuccess(null);
    try {
      await deleteZone(zoneToDelete.id);
      setSuccess(`Zone ${zoneToDelete.code} (${zoneToDelete.name}) has been deleted.`);
      setZones((prev) => (prev ? prev.filter((z) => z.id !== zoneToDelete.id) : prev));
      setZoneToDelete(null);
      loadData();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setDeletingZone(false);
    }
  }

  // Rule actions
  async function handleCreateRule(e: FormEvent) {
    e.preventDefault();
    setSavingRule(true);
    setError(null);
    setSuccess(null);
    try {
      await createPricingRule({
        originZoneId: ruleForm.originZoneId,
        destinationZoneId: ruleForm.destinationZoneId,
        baseFee: parseFloat(ruleForm.baseFee),
        perKgFee: parseFloat(ruleForm.perKgFee),
        smallSizeSurcharge: parseFloat(ruleForm.smallSizeSurcharge),
        mediumSizeSurcharge: parseFloat(ruleForm.mediumSizeSurcharge),
        largeSizeSurcharge: parseFloat(ruleForm.largeSizeSurcharge),
        currency: ruleForm.currency,
        active: true,
      });
      setSuccess("Pricing rule created successfully.");
      setRuleForm(EMPTY_RULE_FORM);
      loadData();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setSavingRule(false);
    }
  }

  async function handleUpdateRule(e: FormEvent) {
    e.preventDefault();
    if (!editingRule) return;
    setSavingRule(true);
    setError(null);
    setSuccess(null);
    try {
      await updatePricingRule(editingRule.id, {
        originZoneId: editingRule.originZoneId,
        destinationZoneId: editingRule.destinationZoneId,
        baseFee: Number(editingRule.baseFee),
        perKgFee: Number(editingRule.perKgFee),
        smallSizeSurcharge: Number(editingRule.smallSizeSurcharge),
        mediumSizeSurcharge: Number(editingRule.mediumSizeSurcharge),
        largeSizeSurcharge: Number(editingRule.largeSizeSurcharge),
        currency: editingRule.currency,
        active: editingRule.active,
        version: editingRule.version,
      });
      setSuccess("Pricing rule updated successfully.");
      setEditingRule(null);
      loadData();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setSavingRule(false);
    }
  }

  async function toggleRuleActive(rule: PricingRuleAdmin) {
    setError(null);
    setSuccess(null);
    try {
      await updatePricingRule(rule.id, {
        originZoneId: rule.originZoneId,
        destinationZoneId: rule.destinationZoneId,
        baseFee: Number(rule.baseFee),
        perKgFee: Number(rule.perKgFee),
        smallSizeSurcharge: Number(rule.smallSizeSurcharge),
        mediumSizeSurcharge: Number(rule.mediumSizeSurcharge),
        largeSizeSurcharge: Number(rule.largeSizeSurcharge),
        currency: rule.currency,
        active: !rule.active,
        version: rule.version,
      });
      setSuccess(`Pricing rule ${rule.originZoneCode} → ${rule.destinationZoneCode} is now ${!rule.active ? "active" : "inactive"}.`);
      loadData();
    } catch (failure) {
      setError(formatApiError(failure));
    }
  }

  // Rate simulator
  async function handleSimulate(e: FormEvent) {
    e.preventDefault();
    if (!simPickup || !simDropoff) return;
    setSimulating(true);
    setSimError(null);
    setQuote(null);
    try {
      const q = await getQuote(simPickup, simDropoff, simSize, simWeightGrams);
      setQuote(q);
    } catch (failure) {
      setSimError(formatApiError(failure));
    } finally {
      setSimulating(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <header className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground">Pricing & Delivery Zones</h2>
          <p className="text-sm text-muted-foreground">
            Manage geographical delivery service areas, fee calculation matrices, and rate simulation (ADM-3).
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={loadData} className="w-fit gap-1.5">
          <RefreshCw className="size-4" /> Refresh
        </Button>
      </header>

      {/* Global Alerts */}
      {error && (
        <div role="alert" className="flex items-start gap-2 rounded-xl border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
          <AlertTriangle className="size-4 shrink-0 mt-0.5" />
          <span>{error}</span>
        </div>
      )}
      {success && (
        <div role="status" className="flex items-center gap-2 rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-3 text-sm text-emerald-700 dark:text-emerald-400">
          <CheckCircle2 className="size-4 shrink-0" />
          <span>{success}</span>
        </div>
      )}

      {/* Tabs */}
      <nav aria-label="Sections" className="flex gap-2 border-b border-border pb-2">
        <button
          type="button"
          onClick={() => setTab("zones")}
          className={`flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
            tab === "zones"
              ? "bg-primary text-primary-foreground shadow-sm"
              : "text-muted-foreground hover:bg-muted hover:text-foreground"
          }`}
        >
          <MapPin className="size-4" />
          Delivery Zones
          {zones && (
            <span className="ml-1 rounded-full bg-primary-foreground/20 px-1.5 py-0.5 text-xs">
              {zones.length}
            </span>
          )}
        </button>

        <button
          type="button"
          onClick={() => setTab("rules")}
          className={`flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
            tab === "rules"
              ? "bg-primary text-primary-foreground shadow-sm"
              : "text-muted-foreground hover:bg-muted hover:text-foreground"
          }`}
        >
          <Coins className="size-4" />
          Pricing Rules
          {rules && (
            <span className="ml-1 rounded-full bg-primary-foreground/20 px-1.5 py-0.5 text-xs">
              {rules.length}
            </span>
          )}
        </button>

        <button
          type="button"
          onClick={() => setTab("simulator")}
          className={`flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
            tab === "simulator"
              ? "bg-primary text-primary-foreground shadow-sm"
              : "text-muted-foreground hover:bg-muted hover:text-foreground"
          }`}
        >
          <Calculator className="size-4" />
          Rate Simulator
        </button>
      </nav>

      {/* TAB 1: DELIVERY ZONES */}
      {tab === "zones" && (
        <div className="space-y-6">
          {/* Hub Network Callout */}
          <div className="flex items-start gap-3 rounded-2xl border border-amber-300/60 bg-amber-50/80 p-4 text-sm text-amber-900 dark:border-amber-900/50 dark:bg-amber-950/30 dark:text-amber-200">
            <Warehouse className="size-5 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
            <div className="space-y-1">
              <p className="font-semibold">Hub Network Prerequisite (ADR 0009)</p>
              <p className="text-xs text-amber-800 dark:text-amber-300">
                Parcels move through branch hubs and a central hub. When adding a new zone, you must also assign a branch hub in{" "}
                <strong>Admin &rarr; Hub Network</strong> and configure pricing rules before deliveries can be booked.
              </p>
            </div>
          </div>

          <div className="grid gap-6 lg:grid-cols-3">
            {/* Zones List */}
            <div className="lg:col-span-2 space-y-4">
              <div className="rounded-2xl border border-border bg-card p-5">
                <h3 className="font-semibold text-foreground mb-4">Configured Delivery Zones</h3>
                {zones === null ? (
                  <div className="h-48 animate-pulse rounded-xl bg-muted" />
                ) : zones.length === 0 ? (
                  <p className="py-8 text-center text-sm text-muted-foreground">No zones configured yet.</p>
                ) : (
                  <div className="divide-y divide-border">
                    {zones.map((zone) => {
                      const assignedHub = zone.hubId
                        ? hubs?.find((h) => h.id === zone.hubId)
                        : servedZoneMap.get(zone.id);
                      const hasBranchHub = Boolean(assignedHub && assignedHub.active);

                      return (
                        <div key={zone.id} className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 py-3.5">
                          <div className="space-y-1">
                            <div className="flex items-center gap-2">
                              <span className="font-mono font-bold uppercase tracking-wider text-sm px-2 py-0.5 rounded bg-muted text-foreground">
                                {zone.code}
                              </span>
                              <span className="font-medium text-foreground">{zone.name}</span>
                              <span
                                className={`text-[11px] font-medium px-2 py-0.5 rounded-full ${
                                  zone.active
                                    ? "bg-emerald-500/15 text-emerald-700 dark:text-emerald-400"
                                    : "bg-muted text-muted-foreground"
                                }`}
                              >
                                {zone.active ? "Active" : "Inactive"}
                              </span>
                            </div>
                            <div className="flex items-center gap-1.5 text-xs">
                              {hasBranchHub ? (
                                <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-medium">
                                  <Warehouse className="size-3.5" /> Served by {assignedHub?.name} ({assignedHub?.code})
                                </span>
                              ) : (
                                <span className="inline-flex items-center gap-1 text-amber-600 dark:text-amber-400 font-medium">
                                  <AlertTriangle className="size-3.5" /> No active hub assigned
                                </span>
                              )}
                            </div>
                          </div>

                          <div className="flex items-center gap-2 self-end sm:self-auto">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => setEditingZone(zone)}
                              className="h-8 gap-1 text-xs"
                            >
                              <Edit2 className="size-3.5" /> Edit
                            </Button>
                            <Button
                              variant={zone.active ? "outline" : "default"}
                              size="sm"
                              onClick={() => toggleZoneActive(zone)}
                              className="h-8 text-xs"
                            >
                              {zone.active ? "Deactivate" : "Activate"}
                            </Button>
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={(e) => {
                                e.stopPropagation();
                                setZoneToDelete(zone);
                              }}
                              className="h-8 gap-1 text-xs text-destructive hover:bg-destructive/10 hover:text-destructive border-destructive/30"
                            >
                              <Trash2 className="size-3.5" />
                              Delete
                            </Button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>

            {/* Add / Edit Zone Form */}
            <div className="space-y-4">
              <div className="rounded-2xl border border-border bg-card p-5">
                <h3 className="font-semibold text-foreground mb-4 flex items-center gap-2">
                  {editingZone ? (
                    <>
                      <Edit2 className="size-4 text-primary" /> Edit Zone
                    </>
                  ) : (
                    <>
                      <Plus className="size-4 text-primary" /> Add New Zone
                    </>
                  )}
                </h3>

                {editingZone ? (
                  <form onSubmit={handleUpdateZone} className="space-y-4">
                    <div className="space-y-1.5">
                      <Label htmlFor="edit-zone-code">Zone Code</Label>
                      <Input
                        id="edit-zone-code"
                        required
                        maxLength={50}
                        value={editingZone.code}
                        onChange={(e) => setEditingZone({ ...editingZone, code: e.target.value.toUpperCase() })}
                        className="font-mono uppercase tracking-wider"
                      />
                    </div>
                    <div className="space-y-1.5">
                      <Label htmlFor="edit-zone-name">Zone Name</Label>
                      <Input
                        id="edit-zone-name"
                        required
                        maxLength={200}
                        value={editingZone.name}
                        onChange={(e) => setEditingZone({ ...editingZone, name: e.target.value })}
                      />
                    </div>
                    <div className="flex items-center gap-2 pt-1">
                      <input
                        type="checkbox"
                        id="edit-zone-active"
                        checked={editingZone.active}
                        onChange={(e) => setEditingZone({ ...editingZone, active: e.target.checked })}
                        className="rounded border-border"
                      />
                      <Label htmlFor="edit-zone-active" className="cursor-pointer text-xs">
                        Active for bookings
                      </Label>
                    </div>

                    <div className="flex gap-2 pt-2">
                      <Button type="submit" disabled={savingZone} className="w-full">
                        {savingZone ? <Loader2 className="size-4 animate-spin" /> : "Save Changes"}
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => setEditingZone(null)}
                        className="w-auto"
                      >
                        Cancel
                      </Button>
                    </div>
                  </form>
                ) : (
                  <form onSubmit={handleCreateZone} className="space-y-4">
                    {/* 1. Hub dropdown */}
                    <div className="space-y-1.5">
                      <Label htmlFor="zone-hub">Hub</Label>
                      <select
                        id="zone-hub"
                        required
                        value={selectedHubId}
                        onChange={(e) => handleHubChange(e.target.value)}
                        className="w-full rounded-md border border-input bg-transparent px-3 py-1.5 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-ring"
                      >
                        <option value="" disabled className="text-muted-foreground bg-background">
                          {(hubs ?? []).length === 0 ? "No hubs available (add a hub first)" : "Select a hub..."}
                        </option>
                        {(hubs ?? []).map((hub) => (
                          <option key={hub.id} value={hub.id} className="text-foreground bg-background">
                            {hub.name} ({hub.code} &bull; {hub.city}){hub.central ? " [Central Hub]" : ""}
                          </option>
                        ))}
                      </select>
                      <p className="text-[11px] text-muted-foreground">Select an existing hub this zone belongs to.</p>
                    </div>

                    {/* 2. Zone Name dropdown */}
                    <div className="space-y-1.5">
                      <Label htmlFor="zone-name-select">Zone Name</Label>
                      <select
                        id="zone-name-select"
                        required
                        disabled={!selectedHubId || availableSubZones.length === 0}
                        value={selectedSubZoneName}
                        onChange={(e) => handleSubZoneChange(e.target.value)}
                        className="w-full rounded-md border border-input bg-transparent px-3 py-1.5 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        <option value="" disabled className="text-muted-foreground bg-background">
                          {!selectedHubId
                            ? "Select a hub first"
                            : availableSubZones.length === 0
                            ? "No zones cataloged for this hub"
                            : "Select a zone / area..."}
                        </option>
                        {availableSubZones.map((sz) => {
                          const isTaken =
                            existingZoneCodes.has(sz.code.toUpperCase()) ||
                            existingZoneNames.has(sz.name.toLowerCase());
                          return (
                            <option
                              key={sz.code}
                              value={sz.name}
                              disabled={isTaken}
                              className={
                                isTaken
                                  ? "text-muted-foreground bg-background opacity-50"
                                  : "text-foreground bg-background"
                              }
                            >
                              {sz.name} {isTaken ? "(Already Added)" : `(${sz.code})`}
                            </option>
                          );
                        })}
                      </select>
                      <p className="text-[11px] text-muted-foreground">
                        {selectedHub
                          ? `Available zones in ${selectedHub.city}. Already added zones cannot be selected.`
                          : "Select a hub above to load its zones."}
                      </p>
                    </div>

                    {/* 3. Zone Code (auto-filled, in third place) */}
                    <div className="space-y-1.5">
                      <Label htmlFor="zone-code">Zone Code</Label>
                      <Input
                        id="zone-code"
                        required
                        maxLength={50}
                        placeholder="e.g. CMB-MRD"
                        value={zoneForm.code}
                        onChange={(e) => setZoneForm({ ...zoneForm, code: e.target.value.toUpperCase() })}
                        className="font-mono uppercase tracking-wider"
                      />
                      <p className="text-[11px] text-muted-foreground">Unique identifier (auto-filled from selected zone).</p>
                    </div>

                    <Button
                      type="submit"
                      disabled={savingZone || !zoneForm.code || !zoneForm.name}
                      className="w-full mt-2"
                    >
                      {savingZone ? <Loader2 className="size-4 animate-spin" /> : "Create Zone"}
                    </Button>
                  </form>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: PRICING RULES MATRIX */}
      {tab === "rules" && (
        <div className="space-y-6">
          <div className="grid gap-6 lg:grid-cols-3">
            {/* Rules Matrix */}
            <div className="lg:col-span-2 space-y-4">
              <div className="rounded-2xl border border-border bg-card p-5">
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h3 className="font-semibold text-foreground">Zone-Pair Pricing Rules</h3>
                    <p className="text-xs text-muted-foreground">Fee calculations between origin and destination zones.</p>
                  </div>
                </div>

                {rules === null ? (
                  <div className="h-60 animate-pulse rounded-xl bg-muted" />
                ) : rules.length === 0 ? (
                  <p className="py-8 text-center text-sm text-muted-foreground">No pricing rules defined yet.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm">
                      <thead className="border-b border-border text-xs text-muted-foreground uppercase">
                        <tr>
                          <th className="py-3 px-2">Origin &rarr; Destination</th>
                          <th className="py-3 px-2">Base Fee</th>
                          <th className="py-3 px-2">Per-Kg Fee</th>
                          <th className="py-3 px-2">Surcharges (S/M/L)</th>
                          <th className="py-3 px-2">Status</th>
                          <th className="py-3 px-2 text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {rules.map((rule) => (
                          <tr key={rule.id} className="hover:bg-muted/40 transition-colors">
                            <td className="py-3 px-2">
                              <div className="flex items-center gap-1.5 font-medium">
                                <span className="font-mono text-xs">{rule.originZoneCode ?? "—"}</span>
                                <ArrowRight className="size-3 text-muted-foreground" />
                                <span className="font-mono text-xs">{rule.destinationZoneCode ?? "—"}</span>
                              </div>
                              <span className="text-[11px] text-muted-foreground">
                                {rule.originZoneName} &rarr; {rule.destinationZoneName}
                              </span>
                            </td>
                            <td className="py-3 px-2 font-mono text-xs">
                              {rule.currency} {Number(rule.baseFee).toFixed(2)}
                            </td>
                            <td className="py-3 px-2 font-mono text-xs">
                              {rule.currency} {Number(rule.perKgFee).toFixed(2)}
                            </td>
                            <td className="py-3 px-2 font-mono text-[11px] text-muted-foreground">
                              +{Number(rule.smallSizeSurcharge).toFixed(0)} / +{Number(rule.mediumSizeSurcharge).toFixed(0)} / +{Number(rule.largeSizeSurcharge).toFixed(0)}
                            </td>
                            <td className="py-3 px-2">
                              <span
                                className={`text-[10px] font-medium px-2 py-0.5 rounded-full ${
                                  rule.active
                                    ? "bg-emerald-500/15 text-emerald-700 dark:text-emerald-400"
                                    : "bg-muted text-muted-foreground"
                                }`}
                              >
                                {rule.active ? "Active" : "Inactive"}
                              </span>
                            </td>
                            <td className="py-3 px-2 text-right">
                              <div className="flex items-center justify-end gap-1.5">
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => setEditingRule(rule)}
                                  className="h-7 w-7 p-0"
                                  title="Edit rule"
                                >
                                  <Edit2 className="size-3.5" />
                                </Button>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => toggleRuleActive(rule)}
                                  className="h-7 px-2 text-xs"
                                >
                                  {rule.active ? "Disable" : "Enable"}
                                </Button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>

            {/* Add / Edit Rule Form */}
            <div className="space-y-4">
              <div className="rounded-2xl border border-border bg-card p-5">
                <h3 className="font-semibold text-foreground mb-4 flex items-center gap-2">
                  {editingRule ? (
                    <>
                      <Edit2 className="size-4 text-primary" /> Edit Pricing Rule
                    </>
                  ) : (
                    <>
                      <Plus className="size-4 text-primary" /> Create Pricing Rule
                    </>
                  )}
                </h3>

                {editingRule ? (
                  <form onSubmit={handleUpdateRule} className="space-y-3.5">
                    <div className="rounded-lg bg-muted p-3 text-xs space-y-1">
                      <span className="text-muted-foreground">Zone Route:</span>
                      <p className="font-medium">
                        {editingRule.originZoneCode} ({editingRule.originZoneName}) &rarr; {editingRule.destinationZoneCode} ({editingRule.destinationZoneName})
                      </p>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div className="space-y-1">
                        <Label htmlFor="edit-base-fee" className="text-xs">Base Fee (LKR)</Label>
                        <Input
                          id="edit-base-fee"
                          type="number"
                          step="0.01"
                          min="0"
                          required
                          value={editingRule.baseFee}
                          onChange={(e) => setEditingRule({ ...editingRule, baseFee: parseFloat(e.target.value) || 0 })}
                        />
                      </div>
                      <div className="space-y-1">
                        <Label htmlFor="edit-per-kg-fee" className="text-xs">Per-Kg Fee (LKR)</Label>
                        <Input
                          id="edit-per-kg-fee"
                          type="number"
                          step="0.01"
                          min="0"
                          required
                          value={editingRule.perKgFee}
                          onChange={(e) => setEditingRule({ ...editingRule, perKgFee: parseFloat(e.target.value) || 0 })}
                        />
                      </div>
                    </div>

                    <div className="grid grid-cols-3 gap-2">
                      <div className="space-y-1">
                        <Label className="text-[11px]">Small (+LKR)</Label>
                        <Input
                          type="number"
                          step="0.01"
                          min="0"
                          value={editingRule.smallSizeSurcharge}
                          onChange={(e) => setEditingRule({ ...editingRule, smallSizeSurcharge: parseFloat(e.target.value) || 0 })}
                        />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-[11px]">Med (+LKR)</Label>
                        <Input
                          type="number"
                          step="0.01"
                          min="0"
                          value={editingRule.mediumSizeSurcharge}
                          onChange={(e) => setEditingRule({ ...editingRule, mediumSizeSurcharge: parseFloat(e.target.value) || 0 })}
                        />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-[11px]">Lrg (+LKR)</Label>
                        <Input
                          type="number"
                          step="0.01"
                          min="0"
                          value={editingRule.largeSizeSurcharge}
                          onChange={(e) => setEditingRule({ ...editingRule, largeSizeSurcharge: parseFloat(e.target.value) || 0 })}
                        />
                      </div>
                    </div>

                    <div className="flex items-center gap-2 pt-1">
                      <input
                        type="checkbox"
                        id="edit-rule-active"
                        checked={editingRule.active}
                        onChange={(e) => setEditingRule({ ...editingRule, active: e.target.checked })}
                        className="rounded border-border"
                      />
                      <Label htmlFor="edit-rule-active" className="cursor-pointer text-xs">
                        Active for quotes
                      </Label>
                    </div>

                    <div className="flex gap-2 pt-2">
                      <Button type="submit" disabled={savingRule} className="w-full">
                        {savingRule ? <Loader2 className="size-4 animate-spin" /> : "Save Changes"}
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => setEditingRule(null)}
                      >
                        Cancel
                      </Button>
                    </div>
                  </form>
                ) : (
                  <form onSubmit={handleCreateRule} className="space-y-3.5">
                    <div className="space-y-1">
                      <Label htmlFor="rule-origin" className="text-xs">Origin Zone</Label>
                      <select
                        id="rule-origin"
                        required
                        value={ruleForm.originZoneId}
                        onChange={(e) => setRuleForm({ ...ruleForm, originZoneId: e.target.value })}
                        className="w-full rounded-md border border-input bg-transparent px-3 py-1.5 text-sm shadow-sm"
                      >
                        <option value="" disabled className="text-muted-foreground">Select origin zone</option>
                        {(zones ?? []).map((z) => (
                          <option key={z.id} value={z.id} className="text-foreground bg-background">
                            {z.code} — {z.name} {!z.active ? "(Inactive)" : ""}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <Label htmlFor="rule-destination" className="text-xs">Destination Zone</Label>
                      <select
                        id="rule-destination"
                        required
                        value={ruleForm.destinationZoneId}
                        onChange={(e) => setRuleForm({ ...ruleForm, destinationZoneId: e.target.value })}
                        className="w-full rounded-md border border-input bg-transparent px-3 py-1.5 text-sm shadow-sm"
                      >
                        <option value="" disabled className="text-muted-foreground">Select destination zone</option>
                        {(zones ?? []).map((z) => (
                          <option key={z.id} value={z.id} className="text-foreground bg-background">
                            {z.code} — {z.name} {!z.active ? "(Inactive)" : ""}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div className="space-y-1">
                        <Label htmlFor="rule-base" className="text-xs">Base Fee (LKR)</Label>
                        <Input
                          id="rule-base"
                          type="number"
                          step="0.01"
                          min="0"
                          required
                          value={ruleForm.baseFee}
                          onChange={(e) => setRuleForm({ ...ruleForm, baseFee: e.target.value })}
                        />
                      </div>
                      <div className="space-y-1">
                        <Label htmlFor="rule-per-kg" className="text-xs">Per-Kg Fee (LKR)</Label>
                        <Input
                          id="rule-per-kg"
                          type="number"
                          step="0.01"
                          min="0"
                          required
                          value={ruleForm.perKgFee}
                          onChange={(e) => setRuleForm({ ...ruleForm, perKgFee: e.target.value })}
                        />
                      </div>
                    </div>

                    <div className="grid grid-cols-3 gap-2">
                      <div className="space-y-1">
                        <Label className="text-[11px]">Small (+LKR)</Label>
                        <Input
                          type="number"
                          step="0.01"
                          min="0"
                          value={ruleForm.smallSizeSurcharge}
                          onChange={(e) => setRuleForm({ ...ruleForm, smallSizeSurcharge: e.target.value })}
                        />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-[11px]">Med (+LKR)</Label>
                        <Input
                          type="number"
                          step="0.01"
                          min="0"
                          value={ruleForm.mediumSizeSurcharge}
                          onChange={(e) => setRuleForm({ ...ruleForm, mediumSizeSurcharge: e.target.value })}
                        />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-[11px]">Lrg (+LKR)</Label>
                        <Input
                          type="number"
                          step="0.01"
                          min="0"
                          value={ruleForm.largeSizeSurcharge}
                          onChange={(e) => setRuleForm({ ...ruleForm, largeSizeSurcharge: e.target.value })}
                        />
                      </div>
                    </div>

                    <Button type="submit" disabled={savingRule} className="w-full mt-2">
                      {savingRule ? <Loader2 className="size-4 animate-spin" /> : "Save Pricing Rule"}
                    </Button>
                  </form>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: RATE SIMULATOR */}
      {tab === "simulator" && (
        <div className="grid gap-6 lg:grid-cols-2">
          {/* Simulation Form */}
          <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
            <div>
              <h3 className="font-semibold text-foreground">Interactive Rate Calculator</h3>
              <p className="text-xs text-muted-foreground">
                Simulate exact pricing quote calculation for customer bookings using active rules.
              </p>
            </div>

            <form onSubmit={handleSimulate} className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="sim-origin">Pickup Zone</Label>
                  <select
                    id="sim-origin"
                    required
                    value={simPickup}
                    onChange={(e) => setSimPickup(e.target.value)}
                    className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm"
                  >
                    {(zones ?? []).map((z) => (
                      <option key={z.id} value={z.id} className="text-foreground bg-background">
                        {z.code} — {z.name} {!z.active ? "(Inactive)" : ""}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="sim-dropoff">Dropoff Zone</Label>
                  <select
                    id="sim-dropoff"
                    required
                    value={simDropoff}
                    onChange={(e) => setSimDropoff(e.target.value)}
                    className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm"
                  >
                    {(zones ?? []).map((z) => (
                      <option key={z.id} value={z.id} className="text-foreground bg-background">
                        {z.code} — {z.name} {!z.active ? "(Inactive)" : ""}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="sim-size">Parcel Size</Label>
                  <select
                    id="sim-size"
                    value={simSize}
                    onChange={(e) => setSimSize(e.target.value as ParcelSize)}
                    className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm"
                  >
                    {PARCEL_SIZES.map((size) => (
                      <option key={size} value={size} className="text-foreground bg-background">
                        {size}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="sim-weight">Weight (grams)</Label>
                  <Input
                    id="sim-weight"
                    type="number"
                    min="1"
                    required
                    value={simWeightGrams}
                    onChange={(e) => setSimWeightGrams(parseInt(e.target.value, 10) || 0)}
                  />
                  <p className="text-[11px] text-muted-foreground">
                    {(simWeightGrams / 1000).toFixed(2)} kg
                  </p>
                </div>
              </div>

              <Button type="submit" disabled={simulating} className="w-full gap-2">
                {simulating ? <Loader2 className="size-4 animate-spin" /> : <Calculator className="size-4" />}
                Calculate Delivery Fee
              </Button>
            </form>

            {simError && (
              <div role="alert" className="flex items-start gap-2 rounded-xl border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                <AlertTriangle className="size-4 shrink-0 mt-0.5" />
                <span>{simError}</span>
              </div>
            )}
          </div>

          {/* Simulation Result */}
          <div className="rounded-2xl border border-border bg-card p-5 flex flex-col justify-between">
            <div>
              <h3 className="font-semibold text-foreground mb-1">Calculation Breakdown</h3>
              <p className="text-xs text-muted-foreground mb-4">
                Formula: Base Fee + (Weight in kg &times; Per-Kg Fee) + Size Surcharge
              </p>

              {quote ? (
                <div className="space-y-4">
                  <div className="rounded-xl bg-primary/10 border border-primary/20 p-4 text-center">
                    <span className="text-xs uppercase tracking-wider text-muted-foreground font-semibold">
                      Total Calculated Fee
                    </span>
                    <p className="text-3xl font-bold text-foreground font-mono mt-1">
                      {quote.currency} {Number(quote.amount).toFixed(2)}
                    </p>
                  </div>

                  <div className="space-y-2.5 rounded-xl border border-border bg-muted/30 p-3.5 text-sm">
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Base Delivery Fee:</span>
                      <span className="font-mono font-medium">{quote.currency} {Number(quote.breakdown.baseFee).toFixed(2)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Weight Fee ({(simWeightGrams / 1000).toFixed(2)} kg):</span>
                      <span className="font-mono font-medium">{quote.currency} {Number(quote.breakdown.weightFee).toFixed(2)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Size Surcharge ({simSize}):</span>
                      <span className="font-mono font-medium">+{quote.currency} {Number(quote.breakdown.sizeSurcharge).toFixed(2)}</span>
                    </div>
                  </div>

                  <div className="text-[11px] text-muted-foreground space-y-0.5 font-mono">
                    <p>Applied Rule ID: {quote.pricingRuleId}</p>
                  </div>
                </div>
              ) : (
                <div className="h-48 flex flex-col items-center justify-center text-center text-muted-foreground border-2 border-dashed border-border rounded-xl p-6">
                  <Calculator className="size-8 mb-2 opacity-40" />
                  <p className="text-sm font-medium">No calculation simulated yet.</p>
                  <p className="text-xs">Select route parameters and click &quot;Calculate Delivery Fee&quot;.</p>
                </div>
              )}
            </div>

            <div className="mt-4 pt-3 border-t border-border text-xs text-muted-foreground">
              <span className="font-medium text-foreground">Note:</span> Customer bookings snapshot these fees at creation. Subsequent rule updates will not affect already created parcels.
            </div>
          </div>
        </div>
      )}

      {/* Delete Zone Confirmation Modal with Statcard */}
      <ConfirmationDialog
        open={zoneToDelete !== null}
        onOpenChange={(open) => !open && setZoneToDelete(null)}
        title="Delete Delivery Zone"
        description="Are you sure you want to delete this delivery zone? Associated pricing rules will also be cleaned up automatically. This action cannot be undone."
        confirmLabel="Delete Zone"
        variant="destructive"
        loading={deletingZone}
        onConfirm={confirmDeleteZone}
        itemSummary={
          zoneToDelete
            ? {
                title: zoneToDelete.name,
                code: zoneToDelete.code,
                badge: zoneToDelete.active ? "Active" : "Inactive",
                badgeVariant: zoneToDelete.active ? "success" : "outline",
                icon: MapPin,
                details: [
                  { label: "Zone Code", value: zoneToDelete.code, badge: true },
                  {
                    label: "Serving Hub",
                    value: zoneToDelete.hubId
                      ? hubs?.find((h) => h.id === zoneToDelete.hubId)?.name || "Assigned Hub"
                      : servedZoneMap.get(zoneToDelete.id)?.name || "None",
                  },
                ],
              }
            : undefined
        }
      />
    </div>
  );
}
