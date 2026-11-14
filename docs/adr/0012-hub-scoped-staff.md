# 0012. Hub-scoped dispatchers and couriers

- Status: Proposed
- Date: 2026-11-14

## Context

[ADR 0009](0009-hub-network-delivery-attempts-and-returns.md) §6 lets any dispatcher scan at any
hub and notes that "binding dispatchers to a hub is a possible later refinement". Today every
dispatcher sees every parcel in the country, with the sender's and recipient's details one click
away, and may assign any courier to any parcel. A branch dispatcher works through parcels of every
other branch, can act on them by mistake, and sees personal data they have no reason to see.

## Decision

### 1. Home hubs

- `delivery` owns a **hub staff** table: a user ID, a hub ID, who created the row and when. A user
  may belong to several hubs (relief staff, a courier covering two towns).
- A row without a hub marks the user as **head office**: a dispatcher who works country-wide.
- User IDs are read through `identity.api`; there is no cross-module foreign key (ADR 0003). Only
  `DISPATCHER` and `COURIER` accounts can be given a hub, and only a `DISPATCHER` can be head office.
- Admins (`ADMIN`) manage the rows. Deleting a hub removes its rows.

### 2. Scope of a staff member

| Caller | Scope |
|---|---|
| `ADMIN` | Everything |
| Head office dispatcher | Everything |
| Dispatcher with hubs | Parcels of those hubs (§3) |
| Dispatcher with no row | **Nothing** |

A dispatcher without a hub sees and does nothing, so a forgotten assignment is visible at once
instead of silently granting country-wide access. The migration marks every existing `DISPATCHER`
as head office, so nothing changes on the day this is deployed; an admin then narrows them.

### 3. Which parcels a hub handles

Exactly one hub is **responsible** for an unfinished parcel, and may act on it:

| Parcel | Responsible hub |
|---|---|
| `CREATED` | The branch of the pickup zone |
| `PICKED_UP` | Origin hub |
| `AT_HUB` | Current hub |
| `IN_TRANSIT` | The hub it travels to |
| `OUT_FOR_DELIVERY`, `DELIVERY_ATTEMPT_FAILED`, `RETURNING` | Destination hub |
| Final statuses | None |

Every hub on the parcel's stored route may **read** it at any time, so the sending hub can follow
a parcel it dispatched and both ends can see how it finished. Parcels picked up without a route
(ADR 0006) are visible to head office and admins only.

- Dispatch lists show the parcels the caller may act on or read. For a parcel they may only read,
  no actions are offered.
- Hub scans, line-haul, return, failure, cancellation and courier assignment by a dispatcher who is
  not in the responsible hub are refused with `403 Forbidden`. The backend enforces this; the
  filtered lists are a convenience.

### 4. Couriers

- A courier with hubs can be given a task only for a parcel whose responsible hub is one of them:
  the pickup branch for a pickup, the destination hub for a delivery or return.
- A courier with no hub can be assigned only by an admin or head office.
- A hub dispatcher's courier list and workload counts show only couriers who share a hub with them.
- What a courier sees does not change: only their own tasks, as before.

### 5. API

- `GET /api/v1/hubs/staff` (`ADMIN`): every assignment, with `hubId` empty for head office.
- `PUT` and `DELETE /api/v1/hubs/{hubId}/staff/{userId}` (`ADMIN`): add to or remove from a hub.
- `PUT` and `DELETE /api/v1/hubs/head-office-staff/{userId}` (`ADMIN`): head office.
- `GET /api/v1/dispatch/scope` (`DISPATCHER`, `ADMIN`): the caller's own scope, for the portal.
- `delivery.api` gains the caller on the dispatch list queries and on courier assignment, and a
  small directory that tells `dispatch` which users share a hub with the caller.

### 6. What does not change

Statuses, transitions and events (ADR 0009), customer and merchant access, and public tracking are
unchanged. This ADR replaces only the sentence of ADR 0009 §6 that lets any dispatcher scan at any
hub.

## Alternatives considered

- **A hub ID on the user in `identity`.** Rejected: `identity` would store an ID of a module it
  does not depend on, one hub per user would be the natural limit, and hubs already live in
  `delivery`.
- **Unassigned dispatchers keep country-wide access.** Rejected: access would be granted by
  omission. The explicit head office row gives the same result on purpose.
- **A separate `HUB_STAFF` role** (ADR 0009, deferred). Still deferred: the scope answers "where",
  and the existing roles answer "what".
- **Hiding other hubs' parcels as `404`.** Rejected for staff: a dispatcher who follows a link to
  another hub's parcel should learn that it is not theirs. Customers and couriers keep `404`.

## Consequences

- A branch sees only its own work, and personal data is limited to the hubs on a parcel's route.
- Admins must assign new dispatchers and couriers to hubs; an unassigned dispatcher sees an empty
  portal until they do.
- `dispatch` passes the caller to `delivery` for lists and assignments.
- The delivery migration reads `user_roles` once to mark existing dispatchers as head office. It
  adds no foreign key and no later dependency on `identity` tables.
- Scans are still made one parcel at a time; batches and manifests are left to a later ADR.
