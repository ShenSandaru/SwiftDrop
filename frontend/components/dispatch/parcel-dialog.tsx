"use client";

import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import Link from "next/link";
import { AlertCircle, Info, Loader2, MapPin, UserRound, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/delivery/status-badge";
import { StatusTimeline } from "@/components/delivery/status-timeline";
import { formatDateTime, formatWeight, SIZE_LABELS, STATUS_LABELS } from "@/components/delivery/format";
import { formatApiError, type UserResponse } from "@/lib/api";
import type { ParcelDetail } from "@/lib/delivery";
import type {
  DispatchAssignment,
  DispatchAssignmentType,
  DispatchParcelItem,
} from "@/lib/dispatch";
import {
  assignDispatchCourier,
  getDispatchAssignmentHistory,
  getDispatchParcelDetails,
} from "@/lib/dispatch";

interface ParcelDialogProps {
  parcel: DispatchParcelItem;
  mode: "details" | "assignment";
  assignmentType: DispatchAssignmentType | null;
  couriers: UserResponse[];
  workloads: Record<string, number> | null;
  couriersLoading: boolean;
  onAssigned: (assignment: DispatchAssignment) => void;
  onClose: () => void;
}

function addressLines(address: ParcelDetail["pickup"]): string[] {
  return [
    address.contactName,
    [address.line1, address.line2].filter(Boolean).join(", "),
    [address.city, address.postalCode].filter(Boolean).join(" "),
    address.phone,
  ].filter(Boolean);
}

type ParcelDetailResult = {
  parcelId: string;
  detail: ParcelDetail | null;
  error: string | null;
};

type AssignmentHistoryResult = {
  parcelId: string;
  assignments: DispatchAssignment[];
  error: string | null;
};

export function ParcelDialog({
  parcel,
  mode,
  assignmentType,
  couriers,
  workloads,
  couriersLoading,
  onAssigned,
  onClose,
}: ParcelDialogProps) {
  const [detailResult, setDetailResult] = useState<ParcelDetailResult | null>(null);
  const [historyResult, setHistoryResult] = useState<AssignmentHistoryResult | null>(null);
  const [selectedCourierId, setSelectedCourierId] = useState("");
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const dialogRef = useRef<HTMLDivElement | null>(null);
  const courierById = useMemo(
    () => new Map(couriers.map((courier) => [courier.id, courier.fullName])),
    [couriers],
  );
  const eligibleCouriers = couriers.filter((courier) => courier.enabled);

  useEffect(() => {
    let active = true;
    getDispatchParcelDetails(parcel.id)
      .then((loaded) => {
        if (active) {
          setDetailResult({ parcelId: parcel.id, detail: loaded, error: null });
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setDetailResult({
            parcelId: parcel.id,
            detail: null,
            error: formatApiError(failure),
          });
        }
      });

    return () => {
      active = false;
    };
  }, [parcel.id]);

  useEffect(() => {
    let active = true;
    getDispatchAssignmentHistory(parcel.id)
      .then((loaded) => {
        if (active) {
          setHistoryResult({
            parcelId: parcel.id,
            assignments: loaded.assignments,
            error: null,
          });
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setHistoryResult({
            parcelId: parcel.id,
            assignments: [],
            error: formatApiError(failure),
          });
        }
      });

    return () => {
      active = false;
    };
  }, [parcel.id]);

  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const focusable = dialogRef.current?.querySelector<HTMLElement>(
      'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
    );
    focusable?.focus();

    return () => {
      document.body.style.overflow = originalOverflow;
      previousFocus?.focus();
    };
  }, []);

  const currentDetailResult = detailResult?.parcelId === parcel.id ? detailResult : null;
  const detail = currentDetailResult?.detail ?? null;
  const error = currentDetailResult?.error ?? null;
  const isLoading = currentDetailResult === null;
  const currentHistoryResult = historyResult?.parcelId === parcel.id ? historyResult : null;
  const history = currentHistoryResult?.assignments ?? [];
  const historyError = currentHistoryResult?.error ?? null;
  const historyLoading = currentHistoryResult === null;

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      onClose();
      return;
    }

    if (event.key !== "Tab" || !dialogRef.current) {
      return;
    }

    const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
      'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
    );
    if (focusable.length === 0) {
      event.preventDefault();
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  const isAssignment = mode === "assignment";
  const assignmentLabel = assignmentType === "DELIVERY" ? "delivery" : "pickup";
  const canAssign = detail !== null
    && assignmentType !== null
    && (assignmentType === "PICKUP"
      ? detail.status === "CREATED"
      : detail.status === "PICKED_UP" && detail.assignedCourierId === null);
  const titleId = isAssignment ? "courier-assignment-title" : "parcel-details-title";

  async function submitAssignment() {
    if (!detail || !assignmentType || !selectedCourierId || !canAssign || isSubmitting) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const assigned = await assignDispatchCourier(detail.id, selectedCourierId, assignmentType);
      onAssigned(assigned);
    } catch (failure: unknown) {
      setSubmitError(formatApiError(failure));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 p-3 backdrop-blur-xs sm:p-6"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onKeyDown={handleKeyDown}
        className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-xl border border-border bg-card shadow-lg"
      >
        <div className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-border bg-card px-5 py-4">
          <div className="min-w-0">
            <p className="text-xs font-semibold uppercase tracking-wider text-primary">
              {isAssignment ? `${assignmentLabel} dispatch` : "Parcel"}
            </p>
            <h2 id={titleId} className="mt-1 text-lg font-bold text-foreground">
              {isAssignment ? `Assign ${assignmentLabel} courier` : "Parcel details"}
            </h2>
            <p className="mt-1 font-mono text-xs text-muted-foreground">{parcel.trackingNumber}</p>
          </div>
          <Button type="button" variant="ghost" size="icon" aria-label="Close dialog" onClick={onClose}>
            <X aria-hidden="true" />
          </Button>
        </div>

        <div className="space-y-5 p-5">
          {isLoading && (
            <div className="flex items-center justify-center gap-2 rounded-lg border border-border bg-muted/40 p-8 text-sm text-muted-foreground" role="status">
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Loading parcel details…
            </div>
          )}

          {error && (
            <div className="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive" role="alert">
              <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              <span>{error}</span>
            </div>
          )}

          {detail && (
            <>
              {isAssignment && (
                <div className="flex items-start gap-3 rounded-lg border border-primary/20 bg-accent p-4 text-sm">
                  <Info className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
                  <div>
                    <p className="font-semibold text-foreground">
                      {assignmentType === "PICKUP" ? "Pickup assignment" : "Delivery assignment"}
                    </p>
                    <p className="mt-1 text-muted-foreground">
                      Assignment is validated by the backend against the current parcel stage.
                    </p>
                  </div>
                </div>
              )}

              <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-background p-4">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Order source</p>
                  <p className="mt-1 font-semibold text-foreground">
                    {detail.source === "CUSTOMER" ? "Customer booking" : "Merchant order"}
                  </p>
                  {detail.merchantOrderReference && (
                    <p className="mt-1 text-xs text-muted-foreground">Reference: {detail.merchantOrderReference}</p>
                  )}
                  <p className="mt-2 text-xs text-muted-foreground">Parcel ID: {detail.id}</p>
                </div>
                <StatusBadge status={detail.status} />
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <AddressCard title="Pickup location" lines={addressLines(detail.pickup)} />
                <AddressCard title="Delivery destination" lines={addressLines(detail.dropoff)} />
              </div>

              <div className="grid gap-3 sm:grid-cols-3">
                <InfoTile label="Parcel size" value={SIZE_LABELS[detail.size]} />
                <InfoTile label="Weight" value={formatWeight(detail.weightGrams)} />
                <InfoTile
                  label="Current courier"
                  value={detail.assignedCourierId
                    ? courierById.get(detail.assignedCourierId) ?? detail.assignedCourierId
                    : "Not assigned"}
                  mono={Boolean(detail.assignedCourierId)}
                />
              </div>

              {isAssignment && (
                <section className="space-y-3">
                  <div>
                    <h3 className="text-sm font-bold text-foreground">Courier selection</h3>
                    <p className="mt-1 text-xs text-muted-foreground">
                      Select an enabled courier to assign this parcel. Active workload counts are displayed for guidance.
                    </p>
                  </div>
                  {couriersLoading ? (
                    <div className="flex items-center gap-2 rounded-lg border border-border bg-muted/40 p-4 text-sm text-muted-foreground" role="status">
                      <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                      Loading courier availability…
                    </div>
                  ) : eligibleCouriers.length === 0 ? (
                    <p className="rounded-lg border border-dashed border-border p-4 text-sm text-muted-foreground">
                      No enabled couriers found. Create or enable courier accounts first.
                    </p>
                  ) : (
                    <ul className="divide-y divide-border rounded-lg border border-border">
                      {eligibleCouriers.map((courier) => (
                        <li key={courier.id}>
                          <label className="flex cursor-pointer items-center justify-between gap-3 p-3 has-[:checked]:bg-accent">
                            <span className="flex min-w-0 items-center gap-2">
                              <UserRound className="size-4 shrink-0 text-primary" aria-hidden="true" />
                              <span className="min-w-0">
                                <span className="block truncate text-sm font-medium text-foreground">{courier.fullName}</span>
                                <span className="block truncate font-mono text-[11px] text-muted-foreground">{courier.id}</span>
                              </span>
                            </span>
                            <span className="flex shrink-0 items-center gap-3 text-xs">
                              <span className="rounded-full bg-muted px-2.5 py-0.5 font-medium text-muted-foreground">
                                {workloads !== null
                                  ? `${workloads[courier.id] ?? 0} active`
                                  : "Enabled"}
                              </span>
                              <input
                                type="radio"
                                name="courier"
                                value={courier.id}
                                checked={selectedCourierId === courier.id}
                                onChange={() => setSelectedCourierId(courier.id)}
                                aria-label={`Select ${courier.fullName}`}
                              />
                            </span>
                          </label>
                        </li>
                      ))}
                    </ul>
                  )}
                  {!canAssign && detail && (
                    <p className="rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
                      This parcel is not eligible for a {assignmentLabel} assignment in its current state.
                    </p>
                  )}
                  {submitError && (
                    <p className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive" role="alert">
                      {submitError}
                    </p>
                  )}
                </section>
              )}

              <section className="space-y-3">
                <div>
                  <h3 className="text-sm font-bold text-foreground">Assignment history</h3>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Pickup and delivery task assignments recorded by dispatch.
                  </p>
                </div>
                {historyError && (
                  <p className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive" role="alert">
                    {historyError}
                  </p>
                )}
                {historyLoading && (
                  <div className="flex items-center gap-2 rounded-lg border border-border bg-muted/40 p-3 text-sm text-muted-foreground" role="status">
                    <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                    Loading assignment history…
                  </div>
                )}
                {!historyLoading && !historyError && history.length === 0 && (
                  <p className="rounded-lg border border-dashed border-border p-4 text-sm text-muted-foreground">
                    No courier assignments have been recorded for this parcel.
                  </p>
                )}
                {history.length > 0 && (
                  <ol className="space-y-2">
                    {history.map((assignment) => (
                      <li key={assignment.id} className="flex flex-col gap-1 rounded-lg border border-border bg-background p-3 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <p className="text-sm font-semibold text-foreground">
                            {assignment.type} · {courierById.get(assignment.courierId) ?? assignment.courierId}
                          </p>
                          <p className="mt-1 text-xs text-muted-foreground">
                            Assigned {formatDateTime(assignment.assignedAt)}
                            {assignment.completedAt ? ` · Completed ${formatDateTime(assignment.completedAt)}` : ""}
                          </p>
                        </div>
                        <span className="w-fit rounded-full border border-border bg-muted px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                          {assignment.status}
                        </span>
                      </li>
                    ))}
                  </ol>
                )}
              </section>

              {!isAssignment && (
                <section className="space-y-3">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <h3 className="text-sm font-bold text-foreground">Status timeline</h3>
                      <p className="mt-1 text-xs text-muted-foreground">
                        Updated {formatDateTime(detail.updatedAt)}
                      </p>
                    </div>
                    <Link
                      href={`/track?code=${encodeURIComponent(detail.trackingNumber)}`}
                      className="text-xs font-semibold text-primary underline-offset-4 hover:underline"
                    >
                      Open tracking
                    </Link>
                  </div>
                  <div className="rounded-lg border border-border bg-background p-4">
                    <StatusTimeline entries={detail.history} />
                  </div>
                  <p className="text-xs text-muted-foreground">
                    Status labels: {detail.history.map((entry) => STATUS_LABELS[entry.status]).join(" · ")}
                  </p>
                </section>
              )}
            </>
          )}
        </div>

        <div className="flex flex-col-reverse gap-2 border-t border-border bg-muted/30 p-4 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={onClose}>
            {isAssignment ? "Cancel" : "Close"}
          </Button>
          {isAssignment && (
            <Button
              type="button"
              onClick={submitAssignment}
              disabled={!canAssign || !selectedCourierId || couriersLoading || isSubmitting}
            >
              {isSubmitting && <Loader2 className="animate-spin" aria-hidden="true" />}
              Confirm {assignmentLabel} assignment
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

function AddressCard({ title, lines }: { title: string; lines: string[] }) {
  return (
    <section className="rounded-lg border border-border bg-background p-4">
      <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        <MapPin className="size-3.5 text-primary" aria-hidden="true" />
        {title}
      </h3>
      <p className="mt-2 space-y-0.5 text-sm text-foreground">
        {lines.map((line, index) => (
          <span key={`${line}-${index}`} className="block break-words">
            {line}
          </span>
        ))}
      </p>
    </section>
  );
}

function InfoTile({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="min-w-0 rounded-lg border border-border bg-background p-3">
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className={`mt-1 break-words text-sm font-medium text-foreground ${mono ? "font-mono text-xs" : ""}`}>
        {value}
      </p>
    </div>
  );
}
