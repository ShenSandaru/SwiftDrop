/** The courier's view of a stop: what the task is, where to go, which attempt (ADR 0009 §5). */
import type { CourierParcel, CourierTask, ParcelStatus } from "@/lib/delivery";

/** Steps that hand the parcel over and so need proof (ADR 0006 §5, ADR 0009 §3). */
export const HANDOVERS: ParcelStatus[] = ["DELIVERED", "RETURNED"];
/** Steps that end the stop without a handover; reached from the parcel page. */
export const SETBACKS: ParcelStatus[] = ["FAILED", "DELIVERY_ATTEMPT_FAILED"];

export const TASK_LABELS: Record<CourierTask, string> = {
  PICKUP: "Pickup",
  DELIVERY: "Delivery",
  RETURN: "Return",
};

/** Where the courier goes for this task, and how the stop is headed. */
export function stopOf(parcel: CourierParcel) {
  if (parcel.task === "PICKUP") {
    return { heading: "Pick up from", place: parcel.pickup };
  }
  if (parcel.task === "RETURN") {
    return { heading: "Return to sender", place: parcel.pickup };
  }
  return {
    heading: parcel.status === "AT_HUB" ? "Collect at the hub, deliver to" : "Deliver to",
    place: parcel.dropoff,
  };
}

/** The attempt this stop is, counting the one about to start when the parcel is still at the hub. */
export function attemptOf(parcel: CourierParcel): number | null {
  if (parcel.task !== "DELIVERY" || parcel.taskDone) {
    return null;
  }
  return parcel.status === "OUT_FOR_DELIVERY" ? parcel.deliveryAttempts : parcel.deliveryAttempts + 1;
}
