/**
 * The parcel's journey as five stages (ADR 0009 §2). Hub statuses fold into the stage they belong
 * to, so the route line and the timeline stay the same length however many hubs or attempts a
 * parcel passes through. A journey may end early: cancelled, failed, or sent back to the sender.
 */
import type { ParcelStatus } from "@/lib/delivery";

/** The five stages, each named by its main status. */
export const STAGES: ParcelStatus[] = ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"];

const STAGE_OF: Partial<Record<ParcelStatus, number>> = {
  CREATED: 0,
  PICKED_UP: 1,
  AT_HUB: 2,
  IN_TRANSIT: 2,
  OUT_FOR_DELIVERY: 3,
  DELIVERY_ATTEMPT_FAILED: 3,
  DELIVERED: 4,
};

/** How a journey ended, if it left the normal path. */
export type JourneyEnd = "CANCELLED" | "FAILED" | "RETURN" | null;

export function journeyEnd(status: ParcelStatus): JourneyEnd {
  if (status === "CANCELLED" || status === "FAILED") {
    return status;
  }
  return status === "RETURNING" || status === "RETURNED" ? "RETURN" : null;
}

/** The stage a status belongs to, or -1 for statuses off the normal path. */
export function stageOf(status: ParcelStatus): number {
  return STAGE_OF[status] ?? -1;
}

/** The furthest stage reached along the normal path, from the history. */
export function furthestStage(history: { status: ParcelStatus }[]): number {
  return history.reduce((furthest, entry) => Math.max(furthest, stageOf(entry.status)), 0);
}

/** A problem step: shown in red on the route and in the timeline. */
export function isProblem(status: ParcelStatus): boolean {
  return status === "FAILED" || status === "CANCELLED" || status === "DELIVERY_ATTEMPT_FAILED";
}
