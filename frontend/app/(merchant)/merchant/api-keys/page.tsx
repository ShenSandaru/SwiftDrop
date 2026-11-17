"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  KeyRound,
  Plus,
  Copy,
  Check,
  AlertTriangle,
  Trash2,
  Eye,
  RefreshCw,
  HelpCircle,
  CheckCircle2,
  ArrowRight,
  ShieldCheck,
} from "lucide-react";
import { useAuth } from "@/lib/auth-context";
import { formatApiError } from "@/lib/api";
import {
  fetchApiKeys,
  createApiKey,
  revokeApiKey,
  type ApiKeyItem,
  type CreatedApiKeyResponse,
} from "@/lib/merchant";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "cn";

export default function MerchantApiKeysPage() {
  const { token } = useAuth();

  const [keys, setKeys] = useState<ApiKeyItem[]>([]);
  const [filter, setFilter] = useState<"ALL" | "ACTIVE" | "REVOKED">("ALL");
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [reloadTrigger, setReloadTrigger] = useState(0);

  // Create key modal state
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [keyLabel, setKeyLabel] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Show-once new key modal state
  const [createdKeyData, setCreatedKeyData] = useState<CreatedApiKeyResponse | null>(null);
  const [hasCopied, setHasCopied] = useState(false);

  // Revoke confirmation modal state
  const [revokingKey, setRevokingKey] = useState<ApiKeyItem | null>(null);
  const [isRevoking, setIsRevoking] = useState(false);
  const [revokeError, setRevokeError] = useState<string | null>(null);

  // View details modal state
  const [detailsKey, setDetailsKey] = useState<ApiKeyItem | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchApiKeys(token)
      .then((data) => {
        if (!cancelled) {
          setKeys(data);
          setErrorMessage(null);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setErrorMessage(formatApiError(err));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [token, reloadTrigger]);

  const loadKeys = () => {
    setIsLoading(true);
    setReloadTrigger((prev) => prev + 1);
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!keyLabel.trim()) {
      setCreateError("Please enter a name for your API key.");
      return;
    }

    setIsCreating(true);
    setCreateError(null);

    try {
      const result = await createApiKey({ label: keyLabel.trim() }, token);
      setKeyLabel("");
      setIsCreateOpen(false);
      setCreatedKeyData(result);
      setHasCopied(false);
      loadKeys();
    } catch (err) {
      setCreateError(formatApiError(err));
    } finally {
      setIsCreating(false);
    }
  };

  const handleCopyNewKey = async () => {
    if (!createdKeyData?.apiKey) return;
    try {
      await navigator.clipboard.writeText(createdKeyData.apiKey);
      setHasCopied(true);
      setTimeout(() => setHasCopied(false), 2500);
    } catch {
      // fallback
    }
  };

  const handleRevokeConfirm = async () => {
    if (!revokingKey) return;
    setIsRevoking(true);
    setRevokeError(null);

    try {
      await revokeApiKey(revokingKey.id, token);
      setRevokingKey(null);
      loadKeys();
    } catch (err) {
      setRevokeError(formatApiError(err));
    } finally {
      setIsRevoking(false);
    }
  };

  const activeCount = keys.filter((k) => k.active).length;
  const filteredKeys = keys.filter((k) => {
    if (filter === "ACTIVE") return k.active;
    if (filter === "REVOKED") return !k.active;
    return true;
  });

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="rounded-3xl border border-border bg-card p-6 sm:p-8 space-y-4 shadow-xs">
        <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
          <div className="space-y-2 max-w-2xl">
            <div className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-primary">
              <KeyRound className="size-4" />
              <span>API Keys</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-foreground tracking-tight">
              Securely connect your online store to SwiftDrop
            </h1>
            <p className="text-sm text-muted-foreground leading-relaxed">
              API keys allow your online store to securely communicate with SwiftDrop. You need an API key to connect your store and automatically send customer orders to SwiftDrop.
            </p>
          </div>

          <Button
            onClick={() => {
              setKeyLabel("");
              setCreateError(null);
              setIsCreateOpen(true);
            }}
            className="shrink-0 text-xs font-semibold h-10 px-4"
          >
            <Plus className="size-4 mr-1.5" />
            Create New API Key
          </Button>
        </div>
      </div>

      {/* Your API Keys Section */}
      <div className="space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border/60 pb-3">
          <div>
            <h2 className="text-xl font-bold text-foreground">Your API Keys</h2>
            <p className="text-xs text-muted-foreground">
              Create and manage the keys used by your online stores.
            </p>
          </div>

          <div className="flex items-center gap-2.5">
            <span className="text-xs font-medium px-2.5 py-1 rounded-full bg-primary/10 text-primary">
              {activeCount} Active {activeCount === 1 ? "Key" : "Keys"}
            </span>

            <Button
              variant="outline"
              size="sm"
              onClick={loadKeys}
              disabled={isLoading}
              className="text-xs h-8 px-2.5"
            >
              <RefreshCw className={cn("size-3.5 mr-1", isLoading && "animate-spin")} />
              Refresh
            </Button>
          </div>
        </div>

        {/* Filter Tabs */}
        <div className="flex flex-wrap gap-2">
          {[
            { id: "ALL", label: `All Keys (${keys.length})` },
            { id: "ACTIVE", label: `Active (${activeCount})` },
            { id: "REVOKED", label: `Revoked (${keys.length - activeCount})` },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setFilter(tab.id as "ALL" | "ACTIVE" | "REVOKED")}
              className={cn(
                "px-3 py-1.5 rounded-lg text-xs font-medium transition-colors",
                filter === tab.id
                  ? "bg-primary text-primary-foreground shadow-xs"
                  : "bg-muted/40 text-muted-foreground hover:bg-muted hover:text-foreground"
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Global Error Banner */}
        {errorMessage && (
          <div className="rounded-xl border border-destructive/20 bg-destructive/10 p-4 text-xs text-destructive flex items-center justify-between gap-3">
            <span>{errorMessage}</span>
            <Button size="sm" variant="outline" onClick={loadKeys} className="text-xs h-7">
              Try Again
            </Button>
          </div>
        )}

        {/* Keys Table Card */}
        <Card className="rounded-2xl border-border bg-card shadow-xs overflow-hidden">
          {isLoading ? (
            <div className="p-12 text-center text-sm text-muted-foreground space-y-2">
              <RefreshCw className="size-5 mx-auto animate-spin text-primary" />
              <p>Loading your API keys...</p>
            </div>
          ) : filteredKeys.length === 0 ? (
            <div className="p-12 text-center space-y-3">
              <div className="size-12 rounded-full bg-muted/60 flex items-center justify-center mx-auto text-muted-foreground">
                <KeyRound className="size-6" />
              </div>
              <div className="space-y-1">
                <p className="text-base font-semibold text-foreground">No API keys found</p>
                <p className="text-xs text-muted-foreground max-w-sm mx-auto">
                  {filter === "ALL"
                    ? "You haven't generated any API keys yet. Create your first key to connect your shop."
                    : `No API keys match the filter "${filter.toLowerCase()}".`}
                </p>
              </div>
              {filter === "ALL" && (
                <Button
                  size="sm"
                  onClick={() => setIsCreateOpen(true)}
                  className="text-xs font-semibold h-8"
                >
                  <Plus className="size-3.5 mr-1.5" />
                  Create Your First Key
                </Button>
              )}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-muted/40 border-b border-border/60 text-muted-foreground font-semibold uppercase tracking-wider">
                  <tr>
                    <th className="py-3 px-4">Key Name</th>
                    <th className="py-3 px-4">Key</th>
                    <th className="py-3 px-4">Created Date</th>
                    <th className="py-3 px-4">Last Used</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/40">
                  {filteredKeys.map((k) => (
                    <tr key={k.id} className="hover:bg-muted/20 transition-colors">
                      {/* Key Name */}
                      <td className="py-3.5 px-4 font-semibold text-foreground">
                        <div className="flex items-center gap-2">
                          <KeyRound className="size-4 text-muted-foreground shrink-0" />
                          <span>{k.label}</span>
                        </div>
                      </td>

                      {/* Masked Key */}
                      <td className="py-3.5 px-4 font-mono text-muted-foreground">
                        <span className="px-2 py-1 rounded bg-muted/60 border border-border/60 text-[11px]">
                          {k.keyPrefix}••••••••••••••••
                        </span>
                      </td>

                      {/* Created Date */}
                      <td className="py-3.5 px-4 text-muted-foreground">
                        {new Date(k.createdAt).toLocaleDateString(undefined, {
                          year: "numeric",
                          month: "short",
                          day: "numeric",
                        })}
                      </td>

                      {/* Last Used */}
                      <td className="py-3.5 px-4 text-muted-foreground">
                        {k.lastUsedAt
                          ? new Date(k.lastUsedAt).toLocaleDateString(undefined, {
                              year: "numeric",
                              month: "short",
                              day: "numeric",
                            })
                          : "Never used"}
                      </td>

                      {/* Status */}
                      <td className="py-3.5 px-4">
                        {k.active ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                            <span className="size-1.5 rounded-full bg-emerald-500" />
                            Active
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-muted text-muted-foreground border border-border">
                            <span className="size-1.5 rounded-full bg-muted-foreground" />
                            Revoked
                          </span>
                        )}
                      </td>

                      {/* Available Actions */}
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {/* View details */}
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setDetailsKey(k)}
                            className="text-xs h-7 px-2"
                            title="View details"
                          >
                            <Eye className="size-3.5 mr-1" />
                            Details
                          </Button>

                          {/* Revoke key */}
                          {k.active && (
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => {
                                setRevokeError(null);
                                setRevokingKey(k);
                              }}
                              className="text-xs h-7 px-2 text-rose-600 dark:text-rose-400 hover:text-rose-700 hover:bg-rose-500/10"
                              title="Revoke key"
                            >
                              <Trash2 className="size-3.5 mr-1" />
                              Revoke
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>

      {/* Need Help? Store Connection Guide Section */}
      <div className="rounded-2xl border border-border/80 bg-muted/20 p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="space-y-1">
          <h3 className="text-base font-bold text-foreground flex items-center gap-2">
            <HelpCircle className="size-4 text-primary" />
            Need Help?
          </h3>
          <p className="text-xs text-muted-foreground">
            If you are connecting your store for the first time, follow our <b>Store Connection Guide</b>.
          </p>
        </div>

        <Link
          href="/merchant"
          className="inline-flex items-center gap-1.5 text-xs font-semibold px-4 py-2.5 rounded-xl border border-input bg-background hover:bg-accent hover:text-accent-foreground transition-colors shrink-0"
        >
          View Setup Guide <ArrowRight className="size-3.5" />
        </Link>
      </div>

      {/* MODAL 1: Create New API Key Modal */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <Card className="w-full max-w-md rounded-2xl border-border bg-card shadow-xl p-6 space-y-5">
            <div className="space-y-1">
              <h3 className="text-lg font-bold text-foreground">Create New API Key</h3>
              <p className="text-xs text-muted-foreground">
                Give your store permission to connect to SwiftDrop.
              </p>
            </div>

            <form onSubmit={handleCreateSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="keyName" className="text-xs font-semibold text-foreground">
                  Give your key a name
                </Label>
                <p className="text-[11px] text-muted-foreground">
                  Choose a name that helps you remember which store uses this key.
                </p>
                <Input
                  id="keyName"
                  value={keyLabel}
                  onChange={(e) => setKeyLabel(e.target.value)}
                  placeholder="Example: My Online Store"
                  maxLength={100}
                  className="text-xs h-9"
                  autoFocus
                />
              </div>

              {createError && (
                <div className="rounded-lg border border-destructive/20 bg-destructive/10 p-3 text-xs text-destructive">
                  {createError}
                </div>
              )}

              <div className="flex items-center justify-end gap-2 pt-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIsCreateOpen(false)}
                  disabled={isCreating}
                  className="text-xs h-8"
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  disabled={isCreating || !keyLabel.trim()}
                  className="text-xs h-8 px-4 font-semibold"
                >
                  {isCreating ? "Creating Key..." : "Create Key"}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* MODAL 2: Your API Key Is Ready! (Show-Once Display) */}
      {createdKeyData && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <Card className="w-full max-w-lg rounded-2xl border-border bg-card shadow-xl p-6 space-y-5">
            <div className="space-y-1 text-center sm:text-left">
              <div className="size-10 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center mb-2">
                <CheckCircle2 className="size-6" />
              </div>
              <h3 className="text-xl font-extrabold text-foreground tracking-tight">
                Your API Key Is Ready!
              </h3>
              <p className="text-xs text-muted-foreground">
                Your new key <b>&quot;{createdKeyData.label}&quot;</b> has been created successfully.
              </p>
            </div>

            {/* Key Display & Copy Box */}
            <div className="space-y-2">
              <Label className="text-xs font-bold text-foreground">Your API Key</Label>
              <div className="flex items-center gap-2">
                <div className="flex-1 p-3 rounded-xl border border-border bg-muted/60 font-mono text-xs text-foreground break-all select-all">
                  {createdKeyData.apiKey}
                </div>
                <Button
                  type="button"
                  size="sm"
                  onClick={handleCopyNewKey}
                  className="h-10 px-3.5 shrink-0 text-xs font-semibold"
                >
                  {hasCopied ? (
                    <>
                      <Check className="size-4 mr-1.5 text-emerald-400 stroke-[3]" />
                      Copied!
                    </>
                  ) : (
                    <>
                      <Copy className="size-4 mr-1.5" />
                      Copy API Key
                    </>
                  )}
                </Button>
              </div>
            </div>

            {/* Important Security Callout */}
            <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 space-y-2 text-xs text-amber-900 dark:text-amber-200">
              <p className="font-bold flex items-center gap-1.5">
                <AlertTriangle className="size-4 text-amber-600 dark:text-amber-400 shrink-0" />
                Important Security Notice:
              </p>
              <ul className="list-disc space-y-1 pl-5">
                <li>Copy and save your key somewhere safe.</li>
                <li>You will not be able to view the full key again after leaving this page.</li>
                <li>Never share your key publicly or place it in client-side code.</li>
                <li>If you lose your key, create a new one.</li>
              </ul>
            </div>

            <div className="pt-2 flex justify-end">
              <Button
                type="button"
                onClick={() => setCreatedKeyData(null)}
                className="text-xs h-9 px-5 font-semibold"
              >
                I Have Saved My Key
              </Button>
            </div>
          </Card>
        </div>
      )}

      {/* MODAL 3: Revoke API Key Confirmation Modal */}
      {revokingKey && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <Card className="w-full max-w-md rounded-2xl border-border bg-card shadow-xl p-6 space-y-4">
            <div className="flex items-start gap-3">
              <div className="size-10 rounded-full bg-rose-500/10 text-rose-600 dark:text-rose-400 flex items-center justify-center shrink-0">
                <AlertTriangle className="size-5" />
              </div>
              <div className="space-y-1">
                <h3 className="text-base font-bold text-foreground">Revoke API Key</h3>
                <p className="text-xs text-muted-foreground">
                  Are you sure you want to revoke this key?
                </p>
              </div>
            </div>

            <div className="p-3 rounded-xl bg-muted/50 border border-border text-xs space-y-1">
              <p className="font-semibold text-foreground">
                Key Name: <span className="font-normal text-muted-foreground">{revokingKey.label}</span>
              </p>
              <p className="font-mono text-[11px] text-muted-foreground">
                Prefix: {revokingKey.keyPrefix}••••••••
              </p>
            </div>

            <div className="text-xs text-muted-foreground space-y-2 leading-relaxed">
              <p>
                Once revoked, the connected store will no longer be able to send orders to SwiftDrop using this key.
              </p>
              <p>
                If your store is still using this key, create a replacement and update your store before revoking the old one.
              </p>
            </div>

            {revokeError && (
              <div className="rounded-lg border border-destructive/20 bg-destructive/10 p-3 text-xs text-destructive">
                {revokeError}
              </div>
            )}

            <div className="flex items-center justify-end gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setRevokingKey(null)}
                disabled={isRevoking}
                className="text-xs h-8"
              >
                Cancel
              </Button>
              <Button
                type="button"
                variant="destructive"
                onClick={handleRevokeConfirm}
                disabled={isRevoking}
                className="text-xs h-8 px-4 font-semibold"
              >
                {isRevoking ? "Revoking..." : "Revoke Key"}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {/* MODAL 4: View Details Modal */}
      {detailsKey && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <Card className="w-full max-w-md rounded-2xl border-border bg-card shadow-xl p-6 space-y-5">
            <div className="flex items-center justify-between border-b border-border/60 pb-3">
              <div className="flex items-center gap-2">
                <KeyRound className="size-5 text-primary" />
                <h3 className="text-base font-bold text-foreground">API Key Details</h3>
              </div>
              {detailsKey.active ? (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                  Active
                </span>
              ) : (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-muted text-muted-foreground border border-border">
                  Revoked
                </span>
              )}
            </div>

            <div className="space-y-3 text-xs">
              <div className="flex justify-between py-1.5 border-b border-border/40">
                <span className="text-muted-foreground font-medium">Key Name:</span>
                <span className="font-semibold text-foreground">{detailsKey.label}</span>
              </div>

              <div className="flex justify-between py-1.5 border-b border-border/40">
                <span className="text-muted-foreground font-medium">Key Mask:</span>
                <span className="font-mono text-muted-foreground">{detailsKey.keyPrefix}••••••••••••••••</span>
              </div>

              <div className="flex justify-between py-1.5 border-b border-border/40">
                <span className="text-muted-foreground font-medium">Created Date:</span>
                <span className="text-foreground">{new Date(detailsKey.createdAt).toLocaleString()}</span>
              </div>

              <div className="flex justify-between py-1.5 border-b border-border/40">
                <span className="text-muted-foreground font-medium">Last Used:</span>
                <span className="text-foreground">
                  {detailsKey.lastUsedAt ? new Date(detailsKey.lastUsedAt).toLocaleString() : "Never used"}
                </span>
              </div>

              {detailsKey.revokedAt && (
                <div className="flex justify-between py-1.5 border-b border-border/40 text-rose-600 dark:text-rose-400">
                  <span className="font-medium">Revoked At:</span>
                  <span>{new Date(detailsKey.revokedAt).toLocaleString()}</span>
                </div>
              )}
            </div>

            <div className="rounded-xl border border-primary/20 bg-primary/5 p-3.5 space-y-1 text-xs">
              <p className="font-semibold text-foreground flex items-center gap-1.5">
                <ShieldCheck className="size-3.5 text-primary" />
                Integration Usage:
              </p>
              <p className="text-muted-foreground">
                Send this key in your server&apos;s requests in the <code className="font-mono font-bold text-foreground">X-Api-Key</code> header.
              </p>
            </div>

            <div className="flex justify-end pt-2">
              <Button
                type="button"
                onClick={() => setDetailsKey(null)}
                className="text-xs h-8 px-4"
              >
                Close
              </Button>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}
