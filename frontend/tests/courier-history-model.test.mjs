// Runtime validation coverage for courier history filtering, sorting, and summary behavior.
import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  filterTasksByStatus,
  filterTasksByType,
  searchCompletedTasks,
  sortHistoryTasks,
  summarizeCompletedTasks,
} from "../app/(courier)/courier/history/model.ts";

const parcels = [
  {
    id: "ten",
    trackingNumber: "TRK-10",
    status: "DELIVERED",
    task: "DELIVERY",
    taskDone: true,
    updatedAt: "2025-03-02T10:00:00Z",
  },
  {
    id: "two",
    trackingNumber: "TRK-2",
    status: "PICKED_UP",
    task: "PICKUP",
    taskDone: true,
    updatedAt: "2025-03-01T10:00:00Z",
  },
  {
    id: "one",
    trackingNumber: "TRK-1",
    status: "DELIVERY_ATTEMPT_FAILED",
    task: "DELIVERY",
    taskDone: true,
    updatedAt: "2025-03-03T10:00:00Z",
  },
  {
    id: "three",
    trackingNumber: "TRK-3",
    status: "FAILED",
    task: "RETURN",
    taskDone: true,
    updatedAt: "2025-03-04T10:00:00Z",
  },
  {
    id: "active",
    trackingNumber: "TRK-4",
    status: "IN_TRANSIT",
    task: "DELIVERY",
    taskDone: false,
    updatedAt: "2025-03-05T10:00:00Z",
  },
];

const completedParcels = parcels.filter((parcel) => parcel.taskDone);

describe("courier history model", () => {
  it("counts completed task outcomes without including active tasks", () => {
    assert.deepEqual(summarizeCompletedTasks(parcels), {
      totalCompletedTasks: 4,
      delivered: 1,
      failedAttempts: 1,
      failedDeliveries: 1,
      otherOutcomes: 1,
    });
  });

  it("searches tracking numbers case-insensitively and ignores surrounding whitespace", () => {
    assert.deepEqual(searchCompletedTasks(completedParcels, "  trk-2 "), [parcels[1]]);
    assert.deepEqual(searchCompletedTasks(completedParcels, "  "), completedParcels);
  });

  it("filters by exact parcel status or returns all records", () => {
    assert.deepEqual(filterTasksByStatus(completedParcels, "PICKED_UP"), [parcels[1]]);
    assert.deepEqual(filterTasksByStatus(completedParcels, "ALL"), completedParcels);
  });

  it("filters by courier task type or returns all records", () => {
    assert.deepEqual(filterTasksByType(completedParcels, "RETURN"), [parcels[3]]);
    assert.deepEqual(filterTasksByType(completedParcels, "ALL"), completedParcels);
  });

  it("sorts dates and tracking numbers without mutating its input", () => {
    const originalOrder = completedParcels.map((parcel) => parcel.id);

    assert.deepEqual(
      sortHistoryTasks(completedParcels, "UPDATED_DESC").map((parcel) => parcel.id),
      ["three", "one", "ten", "two"]
    );
    assert.deepEqual(
      sortHistoryTasks(completedParcels, "UPDATED_ASC").map((parcel) => parcel.id),
      ["two", "ten", "one", "three"]
    );
    assert.deepEqual(
      sortHistoryTasks(completedParcels, "TRACKING_ASC").map((parcel) => parcel.trackingNumber),
      ["TRK-1", "TRK-2", "TRK-3", "TRK-10"]
    );
    assert.deepEqual(
      sortHistoryTasks(completedParcels, "TRACKING_DESC").map((parcel) => parcel.trackingNumber),
      ["TRK-10", "TRK-3", "TRK-2", "TRK-1"]
    );
    assert.deepEqual(completedParcels.map((parcel) => parcel.id), originalOrder);
  });
});
