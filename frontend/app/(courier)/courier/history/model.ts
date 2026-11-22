import type { CourierParcel, CourierTask, ParcelStatus } from "@/lib/delivery";

export type HistorySortOrder = "UPDATED_DESC" | "UPDATED_ASC" | "TRACKING_ASC" | "TRACKING_DESC";

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

export function filterTasksByStatus(tasks: CourierParcel[], status: ParcelStatus | "ALL"): CourierParcel[] {
  return status === "ALL" ? tasks : tasks.filter((parcel) => parcel.status === status);
}

export function filterTasksByType(tasks: CourierParcel[], task: CourierTask | "ALL"): CourierParcel[] {
  return task === "ALL" ? tasks : tasks.filter((parcel) => parcel.task === task);
}

export function sortHistoryTasks(tasks: CourierParcel[], order: HistorySortOrder): CourierParcel[] {
  return [...tasks].sort((left, right) => {
    let comparison: number;
    switch (order) {
      case "UPDATED_DESC":
        comparison = Date.parse(right.updatedAt) - Date.parse(left.updatedAt);
        break;
      case "UPDATED_ASC":
        comparison = Date.parse(left.updatedAt) - Date.parse(right.updatedAt);
        break;
      case "TRACKING_ASC":
        comparison = left.trackingNumber.localeCompare(right.trackingNumber, undefined, { numeric: true });
        break;
      case "TRACKING_DESC":
        comparison = right.trackingNumber.localeCompare(left.trackingNumber, undefined, { numeric: true });
        break;
    }

    return comparison || left.id.localeCompare(right.id);
  });
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
