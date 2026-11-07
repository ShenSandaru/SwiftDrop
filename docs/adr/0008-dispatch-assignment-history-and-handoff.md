# 0008. Dispatch assignment history and courier handoff

- Status: Accepted
- Date: 2026-11-03

## Context

The original delivery model stores one `assigned_courier_id` and permits assignment only while a
parcel is `CREATED`. It cannot represent a completed pickup task followed by a separate delivery
task, and a count of enabled courier accounts cannot establish operational availability.

The dispatcher needs to assign real courier accounts to pickup and delivery tasks, keep immutable
assignment history, and prevent a courier from holding two active tasks even when assignment
requests race. The accepted modular-monolith boundary requires dispatch to decide eligibility and
delivery to own parcel state and persistence. The existing status workflow and courier APIs must
remain the only way parcel status changes.

## Decision

1. `delivery` owns the parcel assignment-history table and assignment records because they are
   transactional parcel state. `dispatch` owns assignment orchestration, courier eligibility, and
   dispatcher HTTP endpoints. Cross-module access remains through `delivery.api` and
   `identity.api`; no module accesses another module's repositories or entities.
2. The assignment history stores parcel ID, courier user ID, task type (`PICKUP` or `DELIVERY`),
   assignment state (`ACTIVE`, `COMPLETED`, or `REASSIGNED`), assignment and completion times, and
   dispatcher user ID. Legacy rows may have a null dispatcher ID because their original dispatcher
   cannot be established.
3. `parcels.assigned_courier_id` and `assigned_at` represent only the current active task. Both are
   cleared when a task completes. They may be null while a parcel is `CREATED`, during the
   `PICKED_UP` handoff, or after a terminal status.
4. A pickup assignment is allowed only while the parcel is `CREATED`; reassignment in that stage
   closes the prior history row as `REASSIGNED`. The assigned pickup courier remains authorized to
   perform the existing `CREATED → PICKED_UP` transition. That transition completes the pickup
   history row and releases the courier in the same transaction.
5. A delivery assignment is allowed only after pickup completion, while the parcel is `PICKED_UP`
   and has no current courier. The delivery courier then performs the existing sequential status
   transitions and proof workflow. Reassignment after delivery work has started is not supported
   in this version. `DELIVERED` or `FAILED` completes the active task and clears the current courier
   in the same transaction.
6. PostgreSQL partial unique indexes allow at most one `ACTIVE` history row per parcel and per
   courier. Assignment rows, parcel updates, and task completion commit atomically. A uniqueness
   collision is returned as `409 Conflict`; it is not silently retried.
7. Dispatch checks that the requested user exists, has the `COURIER` role, and is enabled using
   `identity.api.UserDirectory`. The delivery database constraint is authoritative for concurrent
   task availability; UI filtering is only a convenience.
8. Dispatcher endpoints require `DISPATCHER_ADMIN`. Courier status/proof endpoints remain
   courier-owned and unchanged at the HTTP boundary.

This ADR supersedes the assignment ownership and reassignment provisions in ADR 0003 and the
courier assignment provisions in ADR 0006. All other decisions in those ADRs remain in force.

## Alternatives considered

- Keeping assignment history in `dispatch` would require delivery to call dispatch when a courier
  completes pickup, reversing the accepted dependency graph or making release eventually
  consistent. That is not selected.
- Reusing one current courier for the whole parcel cannot release the pickup courier and assign a
  different delivery courier.
- Treating every enabled courier as available is not authoritative and is unsafe under concurrent
  requests.

## Consequences

- A new delivery-owned migration backfills assignment state from existing current assignments.
  Historical dispatcher identity is unknown and remains null.
- A new migration fails with a clear error if existing nonterminal parcels already give one courier
  multiple active tasks; operators must resolve that data before applying the uniqueness index.
- `delivery.api` adds stage-aware assignment and queue/history queries. `dispatch` exposes
  authenticated endpoints and the frontend uses them instead of presenting unsupported actions.
- The database migration and delivery lifecycle tests must be run against PostgreSQL before
  deployment. Existing databases with legacy assignments need migration preflight review.
