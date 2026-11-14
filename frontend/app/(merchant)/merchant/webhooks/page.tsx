"use client";

import React, { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { formatApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import {
  fetchWebhookEndpoints,
  createWebhookEndpoint,
  updateWebhookEndpoint,
  deleteWebhookEndpoint,
  rotateWebhookSecret,
  type WebhookEndpoint,
} from "@/lib/merchant";
import { PARCEL_STATUSES, type ParcelStatus } from "@/lib/delivery";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "cn";
import {
  AlertCircle,
  AlertTriangle,
  Check,
  CheckCircle2,
  Copy,
  Globe,
  KeyRound,
  Lock,
  Plus,
  RefreshCw,
  Trash2,
  Webhook,
} from "lucide-react";

const MAX_ENDPOINTS = 5;

export default function MerchantWebhooksPage() {
  const { token } = useAuth();

  const [endpoints, setEndpoints] = useState<WebhookEndpoint[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Dialog State: Create
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createUrl, setCreateUrl] = useState("");
  const [createDesc, setCreateDesc] = useState("");
  const [createEvents, setCreateEvents] = useState<ParcelStatus[]>([
    "CREATED",
    "PICKED_UP",
    "IN_TRANSIT",
    "OUT_FOR_DELIVERY",
    "DELIVERED",
    "FAILED",
  ]);
  const [isSubmittingCreate, setIsSubmittingCreate] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Dialog State: Edit
  const [editingEndpoint, setEditingEndpoint] = useState<WebhookEndpoint | null>(null);
  const [editUrl, setEditUrl] = useState("");
  const [editDesc, setEditDesc] = useState("");
  const [editEvents, setEditEvents] = useState<ParcelStatus[]>([]);
  const [editEnabled, setEditEnabled] = useState(true);
  const [isSubmittingEdit, setIsSubmittingEdit] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  // Dialog State: Delete
  const [deletingEndpoint, setDeletingEndpoint] = useState<WebhookEndpoint | null>(null);
  const [isSubmittingDelete, setIsSubmittingDelete] = useState(false);

  // Dialog State: Rotate Secret Confirmation
  const [rotatingEndpoint, setRotatingEndpoint] = useState<WebhookEndpoint | null>(null);
  const [isSubmittingRotate, setIsSubmittingRotate] = useState(false);

  // Dialog State: Show-Once Secret Modal
  const [showSecretModal, setShowSecretModal] = useState<{
    secret: string;
    endpointUrl: string;
    title: string;
  } | null>(null);
  const [copiedSecret, setCopiedSecret] = useState(false);

  const handleRefresh = useCallback(async () => {
    if (!token) return;
    setIsLoading(true);
    setErrorMessage(null);

    try {
      const list = await fetchWebhookEndpoints(token);
      setEndpoints(list || []);
    } catch (err: unknown) {
      setErrorMessage(formatApiError(err));
    } finally {
      setIsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    let ignore = false;
    if (!token) return;

    fetchWebhookEndpoints(token)
      .then((list) => {
        if (ignore) return;
        setEndpoints(list || []);
        setErrorMessage(null);
      })
      .catch((err: unknown) => {
        if (ignore) return;
        setErrorMessage(formatApiError(err));
      })
      .finally(() => {
        if (!ignore) setIsLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [token]);

  // Open Create Dialog
  function openCreateDialog() {
    setCreateUrl("");
    setCreateDesc("");
    setCreateEvents([
      "CREATED",
      "PICKED_UP",
      "IN_TRANSIT",
      "OUT_FOR_DELIVERY",
      "DELIVERED",
      "FAILED",
    ]);
    setCreateError(null);
    setIsCreateOpen(true);
  }

  // Handle Create Submit
  async function handleCreate(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!token || isSubmittingCreate) return;

    const trimmedUrl = createUrl.trim();
    if (!trimmedUrl.startsWith("https://")) {
      setCreateError("Webhook target URL must use the HTTPS protocol (e.g. https://api.yourdomain.com/webhooks).");
      return;
    }

    if (createEvents.length === 0) {
      setCreateError("Please select at least one delivery lifecycle event to subscribe to.");
      return;
    }

    setIsSubmittingCreate(true);
    setCreateError(null);

    try {
      const res = await createWebhookEndpoint(
        {
          url: trimmedUrl,
          description: createDesc.trim() || undefined,
          events: createEvents,
        },
        token
      );

      setIsCreateOpen(false);
      await handleRefresh();

      // Show the raw secret immediately in the show-once modal
      setShowSecretModal({
        secret: res.secret,
        endpointUrl: res.url,
        title: "New Webhook Endpoint Configured",
      });
    } catch (err: unknown) {
      setCreateError(formatApiError(err));
    } finally {
      setIsSubmittingCreate(false);
    }
  }

  // Open Edit Dialog
  function openEditDialog(endpoint: WebhookEndpoint) {
    setEditingEndpoint(endpoint);
    setEditUrl(endpoint.url);
    setEditDesc(endpoint.description || "");
    setEditEvents([...endpoint.events]);
    setEditEnabled(endpoint.enabled);
    setEditError(null);
  }

  // Handle Edit Submit
  async function handleEdit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!editingEndpoint || !token || isSubmittingEdit) return;

    const trimmedUrl = editUrl.trim();
    if (!trimmedUrl.startsWith("https://")) {
      setEditError("Webhook target URL must use the HTTPS protocol.");
      return;
    }

    if (editEvents.length === 0) {
      setEditError("Please select at least one delivery lifecycle event.");
      return;
    }

    setIsSubmittingEdit(true);
    setEditError(null);

    try {
      await updateWebhookEndpoint(
        editingEndpoint.id,
        {
          url: trimmedUrl,
          description: editDesc.trim() || undefined,
          events: editEvents,
          enabled: editEnabled,
        },
        token
      );

      setEditingEndpoint(null);
      await handleRefresh();
    } catch (err: unknown) {
      setEditError(formatApiError(err));
    } finally {
      setIsSubmittingEdit(false);
    }
  }

  // Toggle Endpoint Enabled
  async function handleToggleEnabled(endpoint: WebhookEndpoint) {
    if (!token) return;
    try {
      await updateWebhookEndpoint(endpoint.id, { enabled: !endpoint.enabled }, token);
      await handleRefresh();
    } catch (err: unknown) {
      setErrorMessage(formatApiError(err));
    }
  }

  // Handle Delete
  async function handleDelete() {
    if (!deletingEndpoint || !token || isSubmittingDelete) return;
    setIsSubmittingDelete(true);

    try {
      await deleteWebhookEndpoint(deletingEndpoint.id, token);
      setDeletingEndpoint(null);
      await handleRefresh();
    } catch (err: unknown) {
      setErrorMessage(formatApiError(err));
    } finally {
      setIsSubmittingDelete(false);
    }
  }

  // Handle Secret Rotation
  async function handleRotateSecret() {
    if (!rotatingEndpoint || !token || isSubmittingRotate) return;
    setIsSubmittingRotate(true);

    try {
      const res = await rotateWebhookSecret(rotatingEndpoint.id, token);
      const url = rotatingEndpoint.url;
      setRotatingEndpoint(null);
      await handleRefresh();

      // Show the newly generated secret in the show-once modal
      setShowSecretModal({
        secret: res.secret,
        endpointUrl: url,
        title: "Webhook Signing Secret Rotated",
      });
    } catch (err: unknown) {
      setErrorMessage(formatApiError(err));
      setRotatingEndpoint(null);
    } finally {
      setIsSubmittingRotate(false);
    }
  }

  function toggleEventSelection(
    event: ParcelStatus,
    currentList: ParcelStatus[],
    setter: (list: ParcelStatus[]) => void
  ) {
    if (currentList.includes(event)) {
      setter(currentList.filter((e) => e !== event));
    } else {
      setter([...currentList, event]);
    }
  }

  const reachedMax = endpoints.length >= MAX_ENDPOINTS;

  return (
    <div className="space-y-6 pb-12">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">
            Webhook Subscriptions & Security
          </h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            Configure secure HTTPS endpoints to receive automated, HMAC-SHA256 signed event notifications.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={isLoading}
            className="text-xs"
          >
            <RefreshCw className={cn("size-3.5 mr-1.5", isLoading && "animate-spin")} />
            Refresh
          </Button>

          <Button
            size="sm"
            onClick={openCreateDialog}
            disabled={reachedMax || isLoading}
            className="text-xs font-semibold gap-1.5"
          >
            <Plus className="size-4" />
            Add Webhook Endpoint
          </Button>
        </div>
      </div>

      {/* Global Error Message */}
      {errorMessage && (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <AlertCircle className="size-4 shrink-0" />
            <span>{errorMessage}</span>
          </div>
          <Button variant="outline" size="sm" onClick={handleRefresh} className="text-xs">
            Retry
          </Button>
        </div>
      )}

      {/* Endpoint Limit Indicator */}
      <div className="flex items-center justify-between p-3.5 rounded-xl border border-border/80 bg-muted/20 text-xs text-muted-foreground">
        <div className="flex items-center gap-2">
          <Globe className="size-4 text-primary" />
          <span>
            Configured Endpoints: <span className="font-semibold text-foreground">{endpoints.length}</span> of {MAX_ENDPOINTS} allowed
          </span>
        </div>
        <Link href="/merchant/deliveries" className="text-primary hover:underline font-medium inline-flex items-center gap-1">
          View Delivery Audit Log &rarr;
        </Link>
      </div>

      {/* Loading Skeleton */}
      {isLoading && (
        <div className="flex items-center justify-center min-h-[300px]">
          <div className="flex flex-col items-center gap-2 text-muted-foreground">
            <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            <p className="text-xs">Loading configured webhook endpoints...</p>
          </div>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && endpoints.length === 0 && (
        <Card className="rounded-xl border-dashed border-border bg-card p-12 text-center">
          <div className="size-12 rounded-full bg-muted/50 text-muted-foreground flex items-center justify-center mx-auto mb-3">
            <Webhook className="size-6" />
          </div>
          <h2 className="text-base font-semibold text-foreground">No Webhook Endpoints Configured</h2>
          <p className="text-xs text-muted-foreground max-w-sm mx-auto mt-1 mb-4">
            Connect your store or backend server to receive live parcel status updates directly via signed HTTPS webhooks.
          </p>
          <Button size="sm" onClick={openCreateDialog} className="text-xs font-semibold gap-1.5">
            <Plus className="size-4" />
            Create First Webhook Endpoint
          </Button>
        </Card>
      )}

      {/* Endpoints List */}
      {!isLoading && endpoints.length > 0 && (
        <div className="grid grid-cols-1 gap-4">
          {endpoints.map((endpoint) => (
            <Card
              key={endpoint.id}
              className={cn(
                "rounded-xl border-border bg-card shadow-xs p-5 transition-all",
                !endpoint.enabled && "opacity-60 bg-muted/20"
              )}
            >
              <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                <div className="space-y-2 min-w-0 flex-1">
                  <div className="flex items-center gap-2.5 flex-wrap">
                    <span className="font-mono text-sm font-bold text-foreground break-all">
                      {endpoint.url}
                    </span>
                    {endpoint.enabled ? (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                        <CheckCircle2 className="size-3" />
                        Enabled
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-muted text-muted-foreground border border-border">
                        Disabled
                      </span>
                    )}
                  </div>

                  {endpoint.description && (
                    <p className="text-xs text-muted-foreground">{endpoint.description}</p>
                  )}

                  {/* Secret Hint & Security */}
                  <div className="flex items-center gap-3 text-xs text-muted-foreground pt-1 flex-wrap">
                    <span className="inline-flex items-center gap-1 font-mono bg-muted/60 px-2 py-0.5 rounded text-[11px]">
                      <Lock className="size-3 text-primary" />
                      Signing Secret: whsec_••••••••{endpoint.secretHint}
                    </span>
                    <span>Created: {new Date(endpoint.createdAt).toLocaleDateString()}</span>
                  </div>

                  {/* Subscribed Events Pills */}
                  <div className="flex items-center gap-1 flex-wrap pt-1">
                    <span className="text-[11px] font-medium text-muted-foreground mr-1">Events:</span>
                    {endpoint.events.map((ev) => (
                      <span
                        key={ev}
                        className="px-2 py-0.5 rounded bg-muted/80 text-foreground font-semibold text-[10px]"
                      >
                        {ev}
                      </span>
                    ))}
                  </div>
                </div>

                {/* Endpoint Controls */}
                <div className="flex items-center gap-2 self-start lg:self-center shrink-0 pt-2 lg:pt-0 border-t lg:border-t-0 border-border/40 w-full lg:w-auto justify-end">
                  {/* Enable / Disable Toggle */}
                  <Button
                    variant="outline"
                    size="xs"
                    onClick={() => handleToggleEnabled(endpoint)}
                    className="text-xs"
                  >
                    {endpoint.enabled ? "Disable" : "Enable"}
                  </Button>

                  {/* Edit Button */}
                  <Button
                    variant="outline"
                    size="xs"
                    onClick={() => openEditDialog(endpoint)}
                    className="text-xs"
                  >
                    Edit
                  </Button>

                  {/* Rotate Secret Button */}
                  <Button
                    variant="outline"
                    size="xs"
                    onClick={() => setRotatingEndpoint(endpoint)}
                    className="text-xs text-amber-600 dark:text-amber-400 hover:text-amber-700 gap-1"
                  >
                    <KeyRound className="size-3" />
                    Rotate Secret
                  </Button>

                  {/* Delete Button */}
                  <Button
                    variant="ghost"
                    size="xs"
                    onClick={() => setDeletingEndpoint(endpoint)}
                    className="text-xs text-destructive hover:bg-destructive/10"
                  >
                    <Trash2 className="size-3.5" />
                  </Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* =========================================================================
          Create Webhook Dialog Modal
         ========================================================================= */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <Card className="w-full max-w-lg rounded-xl bg-card border-border shadow-xl p-6 space-y-4 max-h-[90vh] overflow-y-auto">
            <div>
              <h2 className="text-base font-bold text-foreground">Configure Webhook Endpoint</h2>
              <p className="text-xs text-muted-foreground mt-0.5">
                Register an HTTPS destination to receive cryptographically signed order delivery updates.
              </p>
            </div>

            <form onSubmit={handleCreate} className="space-y-4">
              {createError && (
                <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-xs text-destructive">
                  {createError}
                </div>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="createUrl">Endpoint URL *</Label>
                <Input
                  id="createUrl"
                  type="url"
                  placeholder="https://yourdomain.com/api/swiftdrop/webhook"
                  value={createUrl}
                  onChange={(e) => setCreateUrl(e.target.value)}
                  required
                  className="font-mono text-xs"
                />
                <p className="text-[11px] text-muted-foreground">
                  Must be public HTTPS (port 443). Private IP ranges (RFC 1918) and local addresses are blocked for SSRF protection.
                </p>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="createDesc">Description (Optional)</Label>
                <Input
                  id="createDesc"
                  placeholder="e.g. Production Shopify Fulfillment Webhook"
                  value={createDesc}
                  onChange={(e) => setCreateDesc(e.target.value)}
                  className="text-xs"
                />
              </div>

              {/* Subscribed Events Checklist */}
              <div className="space-y-2">
                <Label>Subscribed Delivery Lifecycle Events *</Label>
                <div className="grid grid-cols-2 gap-2 p-3 rounded-lg border border-border/80 bg-muted/20 text-xs">
                  {PARCEL_STATUSES.map((status) => {
                    const isChecked = createEvents.includes(status);
                    return (
                      <label
                        key={status}
                        className="flex items-center gap-2 cursor-pointer hover:text-foreground select-none"
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() =>
                            toggleEventSelection(status, createEvents, setCreateEvents)
                          }
                          className="size-3.5 rounded border-input text-primary focus:ring-primary"
                        />
                        <span className="font-mono text-[11px]">{status}</span>
                      </label>
                    );
                  })}
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/50">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setIsCreateOpen(false)}
                  disabled={isSubmittingCreate}
                >
                  Cancel
                </Button>
                <Button type="submit" size="sm" disabled={isSubmittingCreate} className="font-semibold">
                  {isSubmittingCreate ? "Creating..." : "Save Endpoint"}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* =========================================================================
          Edit Webhook Dialog Modal
         ========================================================================= */}
      {editingEndpoint && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <Card className="w-full max-w-lg rounded-xl bg-card border-border shadow-xl p-6 space-y-4 max-h-[90vh] overflow-y-auto">
            <div>
              <h2 className="text-base font-bold text-foreground">Edit Webhook Endpoint</h2>
              <p className="text-xs text-muted-foreground mt-0.5">
                Update subscription URL, description, or event triggers.
              </p>
            </div>

            <form onSubmit={handleEdit} className="space-y-4">
              {editError && (
                <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-xs text-destructive">
                  {editError}
                </div>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="editUrl">Endpoint URL *</Label>
                <Input
                  id="editUrl"
                  type="url"
                  value={editUrl}
                  onChange={(e) => setEditUrl(e.target.value)}
                  required
                  className="font-mono text-xs"
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="editDesc">Description</Label>
                <Input
                  id="editDesc"
                  value={editDesc}
                  onChange={(e) => setEditDesc(e.target.value)}
                  className="text-xs"
                />
              </div>

              {/* Status Toggle */}
              <div className="flex items-center gap-2 pt-1">
                <input
                  type="checkbox"
                  id="editEnabled"
                  checked={editEnabled}
                  onChange={(e) => setEditEnabled(e.target.checked)}
                  className="size-3.5 rounded border-input text-primary focus:ring-primary"
                />
                <Label htmlFor="editEnabled" className="text-xs cursor-pointer font-medium">
                  Enable webhook deliveries to this endpoint
                </Label>
              </div>

              {/* Subscribed Events */}
              <div className="space-y-2">
                <Label>Subscribed Delivery Lifecycle Events *</Label>
                <div className="grid grid-cols-2 gap-2 p-3 rounded-lg border border-border/80 bg-muted/20 text-xs">
                  {PARCEL_STATUSES.map((status) => {
                    const isChecked = editEvents.includes(status);
                    return (
                      <label
                        key={status}
                        className="flex items-center gap-2 cursor-pointer hover:text-foreground select-none"
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() =>
                            toggleEventSelection(status, editEvents, setEditEvents)
                          }
                          className="size-3.5 rounded border-input text-primary focus:ring-primary"
                        />
                        <span className="font-mono text-[11px]">{status}</span>
                      </label>
                    );
                  })}
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/50">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setEditingEndpoint(null)}
                  disabled={isSubmittingEdit}
                >
                  Cancel
                </Button>
                <Button type="submit" size="sm" disabled={isSubmittingEdit} className="font-semibold">
                  {isSubmittingEdit ? "Saving..." : "Update Endpoint"}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* =========================================================================
          Delete Confirmation Modal
         ========================================================================= */}
      {deletingEndpoint && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <Card className="w-full max-w-md rounded-xl bg-card border-border shadow-xl p-6 space-y-4">
            <div className="flex items-start gap-3">
              <div className="size-10 rounded-full bg-destructive/10 text-destructive flex items-center justify-center shrink-0">
                <Trash2 className="size-5" />
              </div>
              <div className="space-y-1">
                <h2 className="text-base font-bold text-foreground">Remove Webhook Endpoint?</h2>
                <p className="text-xs text-muted-foreground leading-relaxed">
                  Are you sure you want to delete <span className="font-mono font-semibold text-foreground">{deletingEndpoint.url}</span>?
                  Pending status notifications will no longer be dispatched to this destination.
                </p>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/50">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setDeletingEndpoint(null)}
                disabled={isSubmittingDelete}
              >
                Cancel
              </Button>
              <Button
                variant="destructive"
                size="sm"
                onClick={handleDelete}
                disabled={isSubmittingDelete}
              >
                {isSubmittingDelete ? "Deleting..." : "Delete Endpoint"}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {/* =========================================================================
          Rotate Secret Confirmation Modal
         ========================================================================= */}
      {rotatingEndpoint && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <Card className="w-full max-w-md rounded-xl bg-card border-border shadow-xl p-6 space-y-4">
            <div className="flex items-start gap-3">
              <div className="size-10 rounded-full bg-amber-500/10 text-amber-600 dark:text-amber-400 flex items-center justify-center shrink-0">
                <AlertTriangle className="size-5" />
              </div>
              <div className="space-y-1">
                <h2 className="text-base font-bold text-foreground">Rotate Signing Secret?</h2>
                <p className="text-xs text-muted-foreground leading-relaxed">
                  Rotating the signing secret will invalidate the previous key immediately. You will need to update
                  your server&apos;s webhook signature verification code with the new secret right away.
                </p>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/50">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setRotatingEndpoint(null)}
                disabled={isSubmittingRotate}
              >
                Cancel
              </Button>
              <Button
                size="sm"
                onClick={handleRotateSecret}
                disabled={isSubmittingRotate}
                className="bg-amber-600 hover:bg-amber-700 text-white font-semibold"
              >
                {isSubmittingRotate ? "Rotating..." : "Rotate Secret Now"}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {/* =========================================================================
          Show-Once Secret Modal (Immediate Feedback & Warning)
         ========================================================================= */}
      {showSecretModal && (
        <div className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4">
          <Card className="w-full max-w-lg rounded-xl bg-card border-border shadow-2xl p-6 space-y-5 animate-in fade-in-50 zoom-in-95">
            <div className="space-y-1">
              <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 mb-1">
                <CheckCircle2 className="size-3.5" />
                Action Completed
              </div>
              <h2 className="text-lg font-bold text-foreground">{showSecretModal.title}</h2>
              <p className="text-xs text-muted-foreground font-mono truncate">
                Endpoint: {showSecretModal.endpointUrl}
              </p>
            </div>

            {/* Warning Callout */}
            <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 space-y-1.5 text-xs text-amber-900 dark:text-amber-300">
              <div className="flex items-center gap-2 font-bold">
                <AlertTriangle className="size-4 shrink-0" />
                <span>Save this signing secret now — it will never be displayed again!</span>
              </div>
              <p className="leading-relaxed opacity-90">
                SwiftDrop signs every outbound HTTP webhook payload using HMAC-SHA256 with this secret. Store it
                securely in your environment variables.
              </p>
            </div>

            {/* Secret Display & Copy Box */}
            <div className="space-y-2">
              <Label className="text-xs font-semibold">HMAC-SHA256 Signing Secret</Label>
              <div className="flex items-center gap-2">
                <Input
                  readOnly
                  value={showSecretModal.secret}
                  className="font-mono text-xs bg-muted/50 select-all"
                />
                <Button
                  size="sm"
                  onClick={() => {
                    navigator.clipboard.writeText(showSecretModal.secret);
                    setCopiedSecret(true);
                    setTimeout(() => setCopiedSecret(false), 2000);
                  }}
                  className="gap-1.5 shrink-0"
                >
                  {copiedSecret ? (
                    <>
                      <Check className="size-3.5" />
                      Copied!
                    </>
                  ) : (
                    <>
                      <Copy className="size-3.5" />
                      Copy Secret
                    </>
                  )}
                </Button>
              </div>
            </div>

            <div className="pt-2 border-t border-border/50 flex justify-end">
              <Button
                onClick={() => setShowSecretModal(null)}
                className="w-full sm:w-auto font-semibold"
              >
                I Have Saved My Secret Safely
              </Button>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}
