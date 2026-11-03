/**
 * Display helpers for delivery data. Times are shown in Sri Lanka time, matching the backend's
 * business time zone; money is shown as the backend's exact string, never recomputed.
 */
import type { FailureReason, Money, ParcelSize, ParcelStatus } from "@/lib/delivery";

export const STATUS_LABELS: Record<ParcelStatus, string> = {
  CREATED: "Booked",
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
  FAILED: "Delivery failed",
};

export const FAILURE_REASON_LABELS: Record<FailureReason, string> = {
  RECIPIENT_UNAVAILABLE: "Recipient unavailable",
  ADDRESS_NOT_FOUND: "Address not found",
  REFUSED: "Refused by recipient",
  DAMAGED: "Parcel damaged",
  OTHER: "Other reason",
};

export const SIZE_LABELS: Record<ParcelSize, string> = {
  SMALL: "Small",
  MEDIUM: "Medium",
  LARGE: "Large",
};

const DATE_TIME = new Intl.DateTimeFormat("en-GB", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Asia/Colombo",
});

const DATE = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeZone: "Asia/Colombo" });

const SHORT_DATE = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", timeZone: "Asia/Colombo" });
const TIME = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", timeZone: "Asia/Colombo" });

export function formatDateTime(iso: string | null | undefined): string {
  return iso ? DATE_TIME.format(new Date(iso)) : "—";
}

export function formatDate(iso: string | null | undefined): string {
  return iso ? DATE.format(new Date(iso)) : "—";
}

/** "3 Nov · 12:15", short enough to sit under a route stop on one line. */
export function formatShortDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return "—";
  }
  const date = new Date(iso);
  return `${SHORT_DATE.format(date)} · ${TIME.format(date)}`;
}

export function formatMoney(money: Money | null | undefined): string {
  return money ? `${money.currency} ${money.amount}` : "—";
}

/** 800 → "800 g", 2000 → "2 kg", 2500 → "2.5 kg". */
export function formatWeight(grams: number): string {
  if (grams < 1000) {
    return `${grams} g`;
  }
  // At most 2 decimals, without trailing zeros
  return `${Number((grams / 1000).toFixed(2))} kg`;
}
