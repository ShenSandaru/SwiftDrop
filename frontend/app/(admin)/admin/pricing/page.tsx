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
  Scale,
  Sparkles,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ThemedSelect } from "@/components/ui/select";
import { formatApiError } from "@/lib/api";
import { getQuote, type ParcelSize, PARCEL_SIZES, type PriceQuote } from "@/lib/delivery";
import {
  createPricingRule,
  listAdminZones,
  listPricingRules,
  updatePricingRule,
  type PricingRuleAdmin,
  type ZoneAdmin,
} from "@/lib/pricing";
import { cn } from "cn";

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
  const [tab, setTab] = useState<"rules" | "simulator">("rules");
  const [zones, setZones] = useState<ZoneAdmin[] | null>(null);
  const [rules, setRules] = useState<PricingRuleAdmin[] | null>(null);

  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

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
    Promise.all([listAdminZones(), listPricingRules()])
      .then(([loadedZones, loadedRules]) => {
        setZones(loadedZones);
        setRules(loadedRules);
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
    const interval = setInterval(() => {
      loadData();
    }, 10000);

    function onFocus() {
      loadData();
    }
    window.addEventListener("focus", onFocus);

    return () => {
      clearInterval(interval);
      window.removeEventListener("focus", onFocus);
    };
  }, [loadData]);

  // Options for ThemedSelect
  const zoneSelectOptions = useMemo(() => {
    return (zones ?? []).map((z) => ({
      value: z.id,
      label: `${z.name} (${z.code})`,
      badge: z.code,
      disabled: !z.active,
      description: z.active ? "Active delivery area" : "Inactive zone",
    }));
  }, [zones]);

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

  const parcelSizeOptions = useMemo(() => {
    return PARCEL_SIZES.map((sz) => ({
      value: sz,
      label: `${sz.charAt(0) + sz.slice(1).toLowerCase()} Parcel`,
      badge: sz,
      description:
        sz === "SMALL"
          ? "Documents, small envelopes up to 1kg"
          : sz === "MEDIUM"
          ? "Shoeboxes, standard packages up to 5kg"
          : "Large cartons, bulkier cargo up to 30kg",
    }));
  }, []);

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
      setRuleForm(EMPTY_RULE_FORM);
      setSuccess("Pricing rule created successfully.");
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
        baseFee: editingRule.baseFee,
        perKgFee: editingRule.perKgFee,
        smallSizeSurcharge: editingRule.smallSizeSurcharge,
        mediumSizeSurcharge: editingRule.mediumSizeSurcharge,
        largeSizeSurcharge: editingRule.largeSizeSurcharge,
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
        baseFee: rule.baseFee,
        perKgFee: rule.perKgFee,
        smallSizeSurcharge: rule.smallSizeSurcharge,
        mediumSizeSurcharge: rule.mediumSizeSurcharge,
        largeSizeSurcharge: rule.largeSizeSurcharge,
        currency: rule.currency,
        active: !rule.active,
        version: rule.version,
      });
      setSuccess(`Rule is now ${!rule.active ? "active" : "inactive"}.`);
      loadData();
    } catch (failure) {
      setError(formatApiError(failure));
    }
  }

  // Rate simulator action
  async function handleSimulate(e: FormEvent) {
    e.preventDefault();
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

  const activeRulesCount = useMemo(() => {
    return (rules ?? []).filter((r) => r.active).length;
  }, [rules]);

  const averageBaseFee = useMemo(() => {
    if (!rules || rules.length === 0) return "0.00";
    const sum = rules.reduce((acc, r) => acc + r.baseFee, 0);
    return (sum / rules.length).toFixed(2);
  }, [rules]);

  return (
    <div className="space-y-6">
      {/* Top Operations Header & Quick Status (Matches Dispatcher Fleet UI) */}
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-card p-4 shadow-xs sm:flex-row sm:items-center sm:justify-between sm:p-5">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1.5 font-bold text-primary">
              <Coins className="size-3.5" aria-hidden="true" />
              Rate &amp; Fee Management
            </span>
            <span aria-hidden="true">&middot;</span>
            <span className="text-[11px] text-muted-foreground">
              Currency: LKR
            </span>
          </div>
          <p className="text-sm text-muted-foreground">
            Configure geographical pricing matrices, weight fees, parcel size surcharges, and simulate checkout fees.
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

      {/* KPI Statcards */}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="flex items-center justify-between rounded-xl border border-border bg-card p-4 shadow-xs">
          <div className="space-y-1">
            <p className="text-xs font-medium text-muted-foreground">Total Pricing Rules</p>
            <p className="text-2xl font-bold font-mono text-foreground">{rules ? rules.length : "—"}</p>
            <p className="text-[11px] text-muted-foreground">{activeRulesCount} active zone-pair rules</p>
          </div>
          <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <Coins className="size-5" />
          </div>
        </div>

        <div className="flex items-center justify-between rounded-xl border border-border bg-card p-4 shadow-xs">
          <div className="space-y-1">
            <p className="text-xs font-medium text-muted-foreground">Average Base Fee</p>
            <p className="text-2xl font-bold font-mono text-foreground">
              LKR {averageBaseFee}
            </p>
            <p className="text-[11px] text-muted-foreground">Across all defined routes</p>
          </div>
          <div className="flex size-10 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
            <Scale className="size-5" />
          </div>
        </div>

        <div className="flex items-center justify-between rounded-xl border border-border bg-card p-4 shadow-xs">
          <div className="space-y-1">
            <p className="text-xs font-medium text-muted-foreground">Service Zones</p>
            <p className="text-2xl font-bold font-mono text-foreground">{zones ? zones.length : "—"}</p>
            <p className="text-[11px] text-muted-foreground">Available for routing matrices</p>
          </div>
          <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <MapPin className="size-5" />
          </div>
        </div>

        <div className="flex items-center justify-between rounded-xl border border-border bg-card p-4 shadow-xs">
          <div className="space-y-1">
            <p className="text-xs font-medium text-muted-foreground">Rule Coverage</p>
            <p className="text-2xl font-bold font-mono text-foreground">
              {rules && rules.length > 0 ? `${Math.round((activeRulesCount / rules.length) * 100)}%` : "0%"}
            </p>
            <p className="text-[11px] text-muted-foreground">{activeRulesCount} of {rules?.length ?? 0} active</p>
          </div>
          <div className="flex size-10 items-center justify-center rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400">
            <Sparkles className="size-5" />
          </div>
        </div>
      </div>

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

      {/* Themed Tabs Bar */}
      <div className="border-b border-border">
        <nav aria-label="Sections" className="flex gap-2 overflow-x-auto pb-px [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          <button
            type="button"
            onClick={() => setTab("rules")}
            className={cn(
              "flex items-center gap-2 border-b-2 px-4 py-2.5 text-xs font-semibold transition-colors",
              tab === "rules"
                ? "border-primary text-primary font-bold"
                : "border-transparent text-muted-foreground hover:border-border hover:text-foreground"
            )}
          >
            <Coins className="size-4" />
            Pricing Rules
            {rules && (
              <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-bold text-primary">
                {rules.length}
              </span>
            )}
          </button>

          <button
            type="button"
            onClick={() => setTab("simulator")}
            className={cn(
              "flex items-center gap-2 border-b-2 px-4 py-2.5 text-xs font-semibold transition-colors",
              tab === "simulator"
                ? "border-primary text-primary font-bold"
                : "border-transparent text-muted-foreground hover:border-border hover:text-foreground"
            )}
          >
            <Calculator className="size-4" />
            Rate Simulator
          </button>
        </nav>
      </div>

      {/* TAB 1: PRICING RULES MATRIX */}
      {tab === "rules" && (
        <div className="space-y-6">
          <div className="grid gap-6 lg:grid-cols-3">
            {/* Rules Matrix */}
            <div className="lg:col-span-2 space-y-4">
              <div className="rounded-2xl border border-border bg-card p-5 shadow-xs">
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h3 className="font-semibold text-foreground">Zone-Pair Pricing Rules</h3>
                    <p className="text-xs text-muted-foreground">Fee calculations between origin and destination zones.</p>
                  </div>
                  <span className="text-xs text-muted-foreground font-medium">
                    {rules?.length ?? 0} total rules
                  </span>
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
                            <td className="py-3 px-2 font-medium">
                              <span className="font-mono text-xs font-bold text-primary">{rule.originZoneCode || "Zone"}</span>
                              <span className="mx-1 text-muted-foreground">&rarr;</span>
                              <span className="font-mono text-xs font-bold text-primary">{rule.destinationZoneCode || "Zone"}</span>
                              <div className="text-[11px] text-muted-foreground truncate max-w-[180px]">
                                {rule.originZoneName} to {rule.destinationZoneName}
                              </div>
                            </td>
                            <td className="py-3 px-2 font-mono">{rule.currency} {rule.baseFee.toFixed(2)}</td>
                            <td className="py-3 px-2 font-mono">+{rule.perKgFee.toFixed(2)}/kg</td>
                            <td className="py-3 px-2 text-xs text-muted-foreground">
                              {rule.smallSizeSurcharge.toFixed(0)} / {rule.mediumSizeSurcharge.toFixed(0)} / {rule.largeSizeSurcharge.toFixed(0)}
                            </td>
                            <td className="py-3 px-2">
                              <span
                                className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${
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
                                  className="h-7 px-2 text-xs"
                                >
                                  <Edit2 className="size-3" />
                                </Button>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => toggleRuleActive(rule)}
                                  className="h-7 px-2 text-xs"
                                >
                                  {rule.active ? "Deactivate" : "Activate"}
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
              <div className="rounded-2xl border border-border bg-card p-5 shadow-xs">
                <h3 className="font-semibold text-foreground mb-4 flex items-center gap-2">
                  {editingRule ? (
                    <>
                      <Edit2 className="size-4 text-primary" /> Edit Pricing Rule
                    </>
                  ) : (
                    <>
                      <Plus className="size-4 text-primary" /> Add Pricing Rule
                    </>
                  )}
                </h3>

                {editingRule ? (
                  <form onSubmit={handleUpdateRule} className="space-y-3.5">
                    <div className="text-xs font-medium text-foreground pb-1 border-b border-border">
                      Route: {editingRule.originZoneName} &rarr; {editingRule.destinationZoneName}
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div className="space-y-1">
                        <Label htmlFor="edit-base-fee" className="text-xs">Base Fee</Label>
                        <Input
                          id="edit-base-fee"
                          type="number"
                          step="0.01"
                          required
                          value={editingRule.baseFee}
                          onChange={(e) => setEditingRule({ ...editingRule, baseFee: parseFloat(e.target.value) || 0 })}
                        />
                      </div>
                      <div className="space-y-1">
                        <Label htmlFor="edit-per-kg" className="text-xs">Per-Kg Fee</Label>
                        <Input
                          id="edit-per-kg"
                          type="number"
                          step="0.01"
                          required
                          value={editingRule.perKgFee}
                          onChange={(e) => setEditingRule({ ...editingRule, perKgFee: parseFloat(e.target.value) || 0 })}
                        />
                      </div>
                    </div>

                    <div className="space-y-1">
                      <Label className="text-xs">Size Surcharges (Small / Medium / Large)</Label>
                      <div className="grid grid-cols-3 gap-2">
                        <Input
                          type="number"
                          step="0.01"
                          placeholder="Small"
                          value={editingRule.smallSizeSurcharge}
                          onChange={(e) => setEditingRule({ ...editingRule, smallSizeSurcharge: parseFloat(e.target.value) || 0 })}
                        />
                        <Input
                          type="number"
                          step="0.01"
                          placeholder="Medium"
                          value={editingRule.mediumSizeSurcharge}
                          onChange={(e) => setEditingRule({ ...editingRule, mediumSizeSurcharge: parseFloat(e.target.value) || 0 })}
                        />
                        <Input
                          type="number"
                          step="0.01"
                          placeholder="Large"
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
                        className="rounded border-border text-primary focus:ring-primary"
                      />
                      <Label htmlFor="edit-rule-active" className="cursor-pointer text-xs">
                        Active for quotes
                      </Label>
                    </div>

                    <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2 pt-2">
                      <Button type="submit" disabled={savingRule} className="sm:flex-1">
                        {savingRule ? <Loader2 className="size-4 animate-spin mr-1.5" /> : null}
                        Save Changes
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => setEditingRule(null)}
                        className="shrink-0"
                      >
                        Cancel
                      </Button>
                    </div>
                  </form>
                ) : (
                  <form onSubmit={handleCreateRule} className="space-y-3.5">
                    <div className="space-y-1.5">
                      <Label htmlFor="rule-origin" className="text-xs">Origin Zone</Label>
                      <ThemedSelect
                        id="rule-origin"
                        value={ruleForm.originZoneId}
                        onChange={(val) => setRuleForm({ ...ruleForm, originZoneId: val })}
                        options={zoneSelectOptions}
                        placeholder="Select origin zone..."
                      />
                    </div>

                    <div className="space-y-1.5">
                      <Label htmlFor="rule-dest" className="text-xs">Destination Zone</Label>
                      <ThemedSelect
                        id="rule-dest"
                        value={ruleForm.destinationZoneId}
                        onChange={(val) => setRuleForm({ ...ruleForm, destinationZoneId: val })}
                        options={zoneSelectOptions}
                        placeholder="Select destination zone..."
                      />
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div className="space-y-1">
                        <Label htmlFor="create-base-fee" className="text-xs">Base Fee (LKR)</Label>
                        <Input
                          id="create-base-fee"
                          type="number"
                          step="0.01"
                          required
                          value={ruleForm.baseFee}
                          onChange={(e) => setRuleForm({ ...ruleForm, baseFee: e.target.value })}
                        />
                      </div>
                      <div className="space-y-1">
                        <Label htmlFor="create-per-kg" className="text-xs">Per-Kg Fee (LKR)</Label>
                        <Input
                          id="create-per-kg"
                          type="number"
                          step="0.01"
                          required
                          value={ruleForm.perKgFee}
                          onChange={(e) => setRuleForm({ ...ruleForm, perKgFee: e.target.value })}
                        />
                      </div>
                    </div>

                    <div className="space-y-1">
                      <Label className="text-xs">Size Surcharges (Small / Medium / Large)</Label>
                      <div className="grid grid-cols-3 gap-2">
                        <Input
                          type="number"
                          step="0.01"
                          placeholder="Small"
                          value={ruleForm.smallSizeSurcharge}
                          onChange={(e) => setRuleForm({ ...ruleForm, smallSizeSurcharge: e.target.value })}
                        />
                        <Input
                          type="number"
                          step="0.01"
                          placeholder="Medium"
                          value={ruleForm.mediumSizeSurcharge}
                          onChange={(e) => setRuleForm({ ...ruleForm, mediumSizeSurcharge: e.target.value })}
                        />
                        <Input
                          type="number"
                          step="0.01"
                          placeholder="Large"
                          value={ruleForm.largeSizeSurcharge}
                          onChange={(e) => setRuleForm({ ...ruleForm, largeSizeSurcharge: e.target.value })}
                        />
                      </div>
                    </div>

                    <Button
                      type="submit"
                      disabled={savingRule || !ruleForm.originZoneId || !ruleForm.destinationZoneId}
                      className="w-full mt-2"
                    >
                      {savingRule ? <Loader2 className="size-4 animate-spin mr-1.5" /> : <Plus className="size-4 mr-1.5" />}
                      Create Pricing Rule
                    </Button>
                  </form>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: RATE SIMULATOR */}
      {tab === "simulator" && (
        <div className="grid gap-6 lg:grid-cols-2">
          {/* Simulation Inputs */}
          <div className="rounded-2xl border border-border bg-card p-5 space-y-4 shadow-xs">
            <h3 className="font-semibold text-foreground flex items-center gap-2">
              <Calculator className="size-4 text-primary" /> Delivery Fee Calculator
            </h3>
            <p className="text-xs text-muted-foreground">
              Simulate customer checkout quotes against currently active pricing rules.
            </p>

            <form onSubmit={handleSimulate} className="space-y-4">
              <div className="grid sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="sim-pickup" className="text-xs">Pickup Zone</Label>
                  <ThemedSelect
                    id="sim-pickup"
                    value={simPickup}
                    onChange={(val) => setSimPickup(val)}
                    options={activeZoneSelectOptions}
                    placeholder="Select origin..."
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="sim-dropoff" className="text-xs">Dropoff Zone</Label>
                  <ThemedSelect
                    id="sim-dropoff"
                    value={simDropoff}
                    onChange={(val) => setSimDropoff(val)}
                    options={activeZoneSelectOptions}
                    placeholder="Select destination..."
                  />
                </div>
              </div>

              <div className="grid sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="sim-size" className="text-xs">Parcel Size</Label>
                  <ThemedSelect
                    id="sim-size"
                    value={simSize}
                    onChange={(val) => setSimSize(val as ParcelSize)}
                    options={parcelSizeOptions}
                    searchable={false}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="sim-weight" className="text-xs">Weight (grams)</Label>
                  <Input
                    id="sim-weight"
                    type="number"
                    min={1}
                    max={100000}
                    required
                    value={simWeightGrams}
                    onChange={(e) => setSimWeightGrams(parseInt(e.target.value, 10) || 0)}
                  />
                  <p className="text-[10px] text-muted-foreground">{(simWeightGrams / 1000).toFixed(2)} kg</p>
                </div>
              </div>

              <Button
                type="submit"
                disabled={simulating || !simPickup || !simDropoff}
                className="w-full gap-2"
              >
                {simulating ? <Loader2 className="size-4 animate-spin" /> : <Calculator className="size-4" />}
                Calculate Delivery Fee
              </Button>
            </form>
          </div>

          {/* Simulation Output Card */}
          <div className="rounded-2xl border border-border bg-card p-5 flex flex-col justify-between shadow-xs">
            <div>
              <h3 className="font-semibold text-foreground mb-4">Calculation Breakdown</h3>

              {simError && (
                <div className="rounded-xl border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                  <p className="font-semibold text-xs">Rate Calculation Error</p>
                  <p className="text-xs mt-1">{simError}</p>
                </div>
              )}

              {quote ? (
                <div className="space-y-4">
                  <div className="flex items-baseline justify-between border-b border-border pb-3">
                    <span className="text-sm text-muted-foreground">Final Total Fee</span>
                    <span className="text-2xl font-bold font-mono text-emerald-600 dark:text-emerald-400">
                      {quote.currency} {quote.amount}
                    </span>
                  </div>

                  <div className="space-y-2 text-xs">
                    <div className="flex justify-between text-muted-foreground">
                      <span>Base Rate</span>
                      <span className="font-mono text-foreground">{quote.currency} {quote.breakdown.baseFee}</span>
                    </div>
                    <div className="flex justify-between text-muted-foreground">
                      <span>Weight Fee ({(simWeightGrams / 1000).toFixed(2)} kg)</span>
                      <span className="font-mono text-foreground">+{quote.currency} {quote.breakdown.weightFee}</span>
                    </div>
                    <div className="flex justify-between text-muted-foreground">
                      <span>Size Surcharge ({simSize})</span>
                      <span className="font-mono text-foreground">+{quote.currency} {quote.breakdown.sizeSurcharge}</span>
                    </div>
                  </div>

                  <div className="rounded-xl bg-muted/50 p-3 text-xs space-y-1">
                    <div className="flex items-center gap-1 font-medium text-foreground">
                      <span>{zones?.find((z) => z.id === simPickup)?.name || "Origin Zone"}</span>
                      <ArrowRight className="size-3 text-muted-foreground" />
                      <span>{zones?.find((z) => z.id === simDropoff)?.name || "Destination Zone"}</span>
                    </div>
                    <p className="text-[10px] text-muted-foreground">Pricing rule matched: {quote.pricingRuleId}</p>
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
    </div>
  );
}
