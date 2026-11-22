import type { CourierParcel } from "@/lib/delivery";

export interface CourierHistorySummary {
  totalCompletedTasks: number;
  delivered: number;
  failedAttempts: number;
  failedDeliveries: number;
  otherOutcomes: number;
}

export function searchCompletedTasks(tasks: CourierParcel[], query: string): CourierParcel[] {
  const searchTerm = query.trim().toLowerCase();
  if (!searchTerm) return tasks;

  return tasks.filter((parcel) => parcel.trackingNumber.toLowerCase().includes(searchTerm));
}

export function summarizeCompletedTasks(parcels: CourierParcel[]): CourierHistorySummary {
  const completed = parcels.filter((parcel) => parcel.taskDone);
  const delivered = completed.filter((parcel) => parcel.status === "DELIVERED").length;
  const failedAttempts = completed.filter((parcel) => parcel.status === "DELIVERY_ATTEMPT_FAILED").length;
  const failedDeliveries = completed.filter((parcel) => parcel.status === "FAILED").length;

  return {
    totalCompletedTasks: completed.length,
    delivered,
    failedAttempts,
    failedDeliveries,
    otherOutcomes: completed.length - delivered - failedAttempts - failedDeliveries,
  };
}
