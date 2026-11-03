"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { AlertTriangle, ArrowLeft, Camera, CheckCircle2, Loader2, MapPin, Phone, X } from "lucide-react";
import { cn } from "cn";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RouteHeading } from "@/components/delivery/route-heading";
import { RouteProgress } from "@/components/delivery/route-progress";
import { StatusBadge } from "@/components/delivery/status-badge";
import { StatusTimeline } from "@/components/delivery/status-timeline";
import { FAILURE_REASON_LABELS, STATUS_LABELS } from "@/components/delivery/format";
import { formatApiError } from "@/lib/api";
import {
  FAILURE_REASONS,
  MAX_PHOTO_BYTES,
  changeStatus,
  getParcel,
  isFinalStatus,
  listCourierParcels,
  uploadProof,
  type Address,
  type CourierParcel,
  type FailureReason,
  type ParcelDetail,
  type ParcelStatus,
} from "@/lib/delivery";

const TEXTAREA_CLASS =
  "w-full rounded-lg border border-input bg-transparent px-2.5 py-2 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm dark:bg-input/30";

const NEXT_LABELS: Partial<Record<ParcelStatus, string>> = {
  PICKED_UP: "Mark picked up",
  IN_TRANSIT: "Start transit",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Mark delivered",
};

export default function CourierParcelPage() {
  const { id } = useParams<{ id: string }>();
  const [detail, setDetail] = useState<ParcelDetail | null>(null);
  const [today, setToday] = useState<CourierParcel | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  // The detail has the journey (with notes); today's list adds next steps and the proof flag
  const load = useCallback(() => {
    Promise.all([getParcel(id), listCourierParcels()])
      .then(([parcel, run]) => {
        setDetail(parcel);
        setToday(run.find((item) => item.id === id) ?? null);
      })
      .catch((failure: { status?: number }) => {
        if (failure?.status === 404) {
          setNotFound(true);
        } else {
          setError(formatApiError(failure));
        }
      });
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  if (notFound) {
    return (
      <div className="mx-auto max-w-xl space-y-4">
        <BackLink />
        <p className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          This delivery isn&apos;t assigned to you.
        </p>
      </div>
    );
  }
  if (!detail) {
    return (
      <div className="mx-auto max-w-xl space-y-4">
        <BackLink />
        {error ? (
          <p className="rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{error}</p>
        ) : (
          <div className="h-72 animate-pulse rounded-3xl border border-border bg-muted" />
        )}
      </div>
    );
  }

  const next = today?.nextStatuses.find((status) => status !== "FAILED");
  const canFail = today?.nextStatuses.includes("FAILED") ?? false;

  return (
    <div className="mx-auto max-w-xl space-y-5">
      <BackLink />

      <section className="space-y-5 rounded-3xl border border-border bg-card p-5">
        <div className="flex items-start justify-between gap-3">
          <RouteHeading from={detail.pickup.city} to={detail.dropoff.city} trackingNumber={detail.trackingNumber}
            className="min-w-0 flex-1" />
          <StatusBadge status={detail.status} />
        </div>
        <RouteProgress status={detail.status} history={detail.history} compact />
      </section>

      {!isFinalStatus(detail.status) && today && (
        <NowPanel key={detail.status} detail={detail} next={next} canFail={canFail}
          proofCaptured={today.proofCaptured} onChanged={load} />
      )}
      {isFinalStatus(detail.status) && (
        <p className={cn(
          "flex items-center gap-2 rounded-2xl p-4 text-sm font-semibold",
          detail.status === "DELIVERED" ? "bg-primary/10 text-primary" : "bg-destructive/10 text-destructive",
        )}>
          {detail.status === "DELIVERED" ? <CheckCircle2 className="size-5" aria-hidden="true" /> : <AlertTriangle className="size-5" aria-hidden="true" />}
          {detail.status === "DELIVERED" ? "Delivered. This stop is complete." : "Marked as failed. This stop is closed."}
        </p>
      )}

      <div className="grid gap-3 sm:grid-cols-2">
        <Contact title="Pick up from" address={detail.pickup} />
        <Contact title="Deliver to" address={detail.dropoff} />
      </div>

      <section className="rounded-2xl border border-border bg-card p-5">
        <h2 className="mb-4 text-sm font-bold uppercase tracking-wider text-muted-foreground">Journey log</h2>
        <StatusTimeline entries={detail.history} />
      </section>
    </div>
  );
}

/** Exactly one next action, sized for a thumb; reset whenever the status changes (keyed by status). */
function NowPanel({ detail, next, canFail, proofCaptured, onChanged }: {
  detail: ParcelDetail;
  next: ParcelStatus | undefined;
  canFail: boolean;
  proofCaptured: boolean;
  onChanged: () => void;
}) {
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [failing, setFailing] = useState(false);

  async function act(target: ParcelStatus, reason?: FailureReason) {
    setBusy(true);
    setError(null);
    try {
      await changeStatus(detail.id, target, reason, note);
      onChanged();
    } catch (failure) {
      setError(formatApiError(failure));
      setBusy(false);
    }
  }

  return (
    <section className="space-y-4 rounded-3xl border-2 border-primary/30 bg-accent/50 p-5">
      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">Now</p>

      {failing ? (
        <FailurePanel note={note} setNote={setNote} busy={busy} onCancel={() => setFailing(false)}
          onConfirm={(reason) => act("FAILED", reason)} />
      ) : next === "DELIVERED" ? (
        <ProofStep parcelId={detail.id} proofCaptured={proofCaptured} busy={busy} note={note} setNote={setNote}
          onProofSaved={onChanged} onDeliver={() => act("DELIVERED")} />
      ) : next ? (
        <div className="space-y-3">
          <p className="text-lg font-bold text-foreground">
            {next === "PICKED_UP" ? `Collect the parcel from ${detail.pickup.contactName}` : STATUS_LABELS[next]}
          </p>
          <NoteField note={note} setNote={setNote} />
          <Button size="lg" className="h-12 w-full text-base" disabled={busy} onClick={() => act(next)}>
            {busy && <Loader2 className="animate-spin" aria-hidden="true" />}
            {NEXT_LABELS[next] ?? STATUS_LABELS[next]}
          </Button>
        </div>
      ) : null}

      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}

      {canFail && !failing && (
        <button type="button" onClick={() => setFailing(true)}
          className="w-full text-center text-sm font-semibold text-destructive underline-offset-4 hover:underline">
          Couldn&apos;t deliver?
        </button>
      )}
    </section>
  );
}

function ProofStep({ parcelId, proofCaptured, busy, note, setNote, onProofSaved, onDeliver }: {
  parcelId: string;
  proofCaptured: boolean;
  busy: boolean;
  note: string;
  setNote: (note: string) => void;
  onProofSaved: () => void;
  onDeliver: () => void;
}) {
  const [recipient, setRecipient] = useState("");
  const [photo, setPhoto] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => () => {
    if (preview) {
      URL.revokeObjectURL(preview);
    }
  }, [preview]);

  function choose(file: File | undefined) {
    setError(null);
    if (!file) {
      return;
    }
    // Checked again by the backend, which also looks at the file's bytes
    if (!["image/jpeg", "image/png"].includes(file.type)) {
      setError("Please use a JPEG or PNG photo.");
      return;
    }
    if (file.size > MAX_PHOTO_BYTES) {
      setError("The photo is larger than 2 MB. Take it at a lower resolution and try again.");
      return;
    }
    setPhoto(file);
    setPreview(URL.createObjectURL(file));
  }

  async function save(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await uploadProof(parcelId, recipient.trim(), photo);
      onProofSaved();
    } catch (failure) {
      setError(formatApiError(failure));
    } finally {
      setSaving(false);
    }
  }

  if (proofCaptured) {
    return (
      <div className="space-y-3">
        <p className="flex items-center gap-2 text-lg font-bold text-foreground">
          <CheckCircle2 className="size-5 text-primary" aria-hidden="true" /> Proof saved
        </p>
        <NoteField note={note} setNote={setNote} />
        <Button size="lg" className="h-12 w-full text-base" disabled={busy} onClick={onDeliver}>
          {busy && <Loader2 className="animate-spin" aria-hidden="true" />} Mark delivered
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={save} className="space-y-3">
      <p className="text-lg font-bold text-foreground">Capture proof of delivery</p>
      <div className="space-y-1">
        <Label htmlFor="recipient">Received by</Label>
        <Input id="recipient" required maxLength={200} autoComplete="off" value={recipient}
          onChange={(event) => setRecipient(event.target.value)} placeholder="Recipient's name" className="h-11" />
      </div>

      <input ref={input} type="file" accept="image/jpeg,image/png" capture="environment" className="sr-only"
        id="photo" onChange={(event) => choose(event.target.files?.[0])} />
      {preview ? (
        <div className="relative overflow-hidden rounded-xl border border-border">
          {/* eslint-disable-next-line @next/next/no-img-element -- local preview of the photo being uploaded */}
          <img src={preview} alt="Photo to upload" className="aspect-[4/3] w-full object-cover" />
          <button type="button" aria-label="Remove photo"
            onClick={() => { setPhoto(null); setPreview(null); if (input.current) input.current.value = ""; }}
            className="absolute right-2 top-2 inline-flex size-8 items-center justify-center rounded-full bg-background/90 text-foreground shadow">
            <X className="size-4" aria-hidden="true" />
          </button>
        </div>
      ) : (
        <label htmlFor="photo"
          className="flex h-28 cursor-pointer flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed border-primary/40 bg-card text-sm font-semibold text-primary hover:bg-primary/5">
          <Camera className="size-6" aria-hidden="true" />
          Take a photo <span className="text-xs font-normal text-muted-foreground">(optional, JPEG or PNG, up to 2 MB)</span>
        </label>
      )}

      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      <Button type="submit" size="lg" className="h-12 w-full text-base" disabled={saving || !recipient.trim()}>
        {saving && <Loader2 className="animate-spin" aria-hidden="true" />} Save proof
      </Button>
    </form>
  );
}

function FailurePanel({ note, setNote, busy, onCancel, onConfirm }: {
  note: string;
  setNote: (note: string) => void;
  busy: boolean;
  onCancel: () => void;
  onConfirm: (reason: FailureReason) => void;
}) {
  const [reason, setReason] = useState<FailureReason | null>(null);

  return (
    <div className="space-y-3">
      <p className="text-lg font-bold text-destructive">Why couldn&apos;t you deliver?</p>
      <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Failure reason">
        {FAILURE_REASONS.map((option) => (
          <button key={option} type="button" role="radio" aria-checked={reason === option}
            onClick={() => setReason(option)}
            className={cn(
              "min-h-14 rounded-xl border-2 p-3 text-left text-sm font-semibold transition-colors",
              reason === option ? "border-destructive bg-destructive/10 text-destructive" : "border-border bg-card text-foreground hover:border-destructive/40",
              option === "OTHER" && "col-span-2",
            )}>
            {FAILURE_REASON_LABELS[option]}
          </button>
        ))}
      </div>
      <NoteField note={note} setNote={setNote} />
      <div className="grid grid-cols-2 gap-2">
        <Button type="button" variant="outline" size="lg" className="h-12" onClick={onCancel} disabled={busy}>Back</Button>
        <Button type="button" variant="destructive" size="lg" className="h-12" disabled={!reason || busy}
          onClick={() => reason && onConfirm(reason)}>
          {busy && <Loader2 className="animate-spin" aria-hidden="true" />} Mark failed
        </Button>
      </div>
    </div>
  );
}

function NoteField({ note, setNote }: { note: string; setNote: (note: string) => void }) {
  return (
    <div className="space-y-1">
      <Label htmlFor="note">Note <span className="text-muted-foreground">(optional, only staff see it)</span></Label>
      <textarea id="note" rows={2} maxLength={500} value={note} onChange={(event) => setNote(event.target.value)}
        className={TEXTAREA_CLASS} />
    </div>
  );
}

function Contact({ title, address }: { title: string; address: Address }) {
  return (
    <section className="space-y-1 rounded-2xl border border-border bg-card p-4">
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">{title}</p>
      <p className="font-semibold text-foreground">{address.contactName}</p>
      <p className="flex items-start gap-1.5 text-sm text-muted-foreground">
        <MapPin className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
        <span>
          {address.line1}{address.line2 && `, ${address.line2}`}, <span className="capitalize">{address.city}</span>
        </span>
      </p>
      <a href={`tel:${address.phone}`} className={cn(buttonVariants({ variant: "outline" }), "mt-2 h-10 w-full")}>
        <Phone aria-hidden="true" /> {address.phone}
      </a>
    </section>
  );
}

function BackLink() {
  return (
    <Link href="/courier" className="inline-flex items-center gap-1.5 text-sm font-semibold text-muted-foreground hover:text-foreground">
      <ArrowLeft className="size-4" aria-hidden="true" /> Today&apos;s run
    </Link>
  );
}
