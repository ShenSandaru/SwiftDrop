"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Printer } from "lucide-react";
import { Button } from "@/components/ui/button";
import { RouteHeading } from "@/components/delivery/route-heading";
import { StatusBadge } from "@/components/delivery/status-badge";
import { Ticket } from "@/components/delivery/ticket";
import { FAILURE_REASON_LABELS, SIZE_LABELS, formatDateTime, formatMoney, formatWeight } from "@/components/delivery/format";
import { formatApiError } from "@/lib/api";
import { getReceipt, type Address, type Receipt } from "@/lib/delivery";

/**
 * Printable receipt (CUS-5, D-13): a booking confirmation until delivery, then proof of completion.
 * "Save as PDF" uses the browser's print dialog, so no PDF library is needed.
 */
export default function ReceiptPage() {
  const { id } = useParams<{ id: string }>();
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getReceipt(id).then(setReceipt).catch((failure) => setError(formatApiError(failure)));
  }, [id]);

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      {/* Only the ticket is printed */}
      <style>{`@media print {
        body * { visibility: hidden; }
        #receipt, #receipt * { visibility: visible; }
        #receipt { position: absolute; inset: 0 auto auto 0; width: 100%; box-shadow: none; }
      }`}</style>

      <div className="flex items-center justify-between print:hidden">
        <Link href={`/customer/parcels/${id}`} className="inline-flex items-center gap-1.5 text-sm font-semibold text-muted-foreground hover:text-foreground">
          <ArrowLeft className="size-4" aria-hidden="true" /> Back to parcel
        </Link>
        <Button onClick={() => window.print()} disabled={!receipt}>
          <Printer aria-hidden="true" /> Print / Save as PDF
        </Button>
      </div>

      {error && <p className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">{error}</p>}
      {!receipt && !error && <div className="h-96 animate-pulse rounded-2xl border border-border bg-muted" />}

      {receipt && (
        <Ticket
          id="receipt"
          stub={
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div>
                <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Total paid</p>
                <p className="text-3xl font-extrabold text-primary">{formatMoney(receipt.fee)}</p>
              </div>
              <div className="text-right text-xs text-muted-foreground">
                <p>SwiftDrop · island-wide delivery</p>
                <p className="font-mono tracking-wider">{receipt.trackingNumber}</p>
              </div>
            </div>
          }
        >
          <div className="space-y-6">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">
                  {receipt.status === "DELIVERED" ? "Delivery receipt" : "Booking receipt"}
                </p>
                <p className="text-sm text-muted-foreground">Issued {formatDateTime(receipt.createdAt)}</p>
              </div>
              <StatusBadge status={receipt.status} />
            </div>

            <RouteHeading from={receipt.pickup.city} to={receipt.dropoff.city} trackingNumber={receipt.trackingNumber} />

            <div className="grid gap-4 sm:grid-cols-2">
              <Party title="From" address={receipt.pickup} />
              <Party title="To" address={receipt.dropoff} />
            </div>

            <dl className="grid grid-cols-2 gap-4 rounded-xl bg-muted p-4 sm:grid-cols-3">
              <Item label="Parcel" value={`${SIZE_LABELS[receipt.size]} · ${formatWeight(receipt.weightGrams)}`} />
              <Item label="Contents" value={receipt.description ?? "—"} />
              {receipt.deliveredAt && <Item label="Delivered" value={formatDateTime(receipt.deliveredAt)} />}
              {receipt.recipientName && <Item label="Received by" value={receipt.recipientName} />}
              {receipt.failedAt && <Item label="Delivery failed" value={formatDateTime(receipt.failedAt)} />}
              {receipt.failureReason && <Item label="Reason" value={FAILURE_REASON_LABELS[receipt.failureReason]} />}
            </dl>
          </div>
        </Ticket>
      )}
    </div>
  );
}

function Party({ title, address }: { title: string; address: Address }) {
  return (
    <div className="space-y-1">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{title}</p>
      <p className="font-semibold text-foreground">{address.contactName}</p>
      <p className="text-sm text-muted-foreground">
        {address.line1}
        {address.line2 && <>, {address.line2}</>}, {address.city}
        {address.postalCode && ` ${address.postalCode}`}
      </p>
      <p className="text-sm text-muted-foreground">{address.phone}</p>
    </div>
  );
}

function Item({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{label}</dt>
      <dd className="truncate text-sm font-bold text-foreground">{value}</dd>
    </div>
  );
}
