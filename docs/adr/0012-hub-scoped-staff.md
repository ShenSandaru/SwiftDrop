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
- User IDs are read through `identity.api`; there is no cross-module foreign key (ADR 0003). Only
  `DISPATCHER` and `COURIER` accounts can be given a hub.
- Admins (`ADMIN`) manage the rows. Deleting a hub removes its rows.

### 2. Scope of a staff member

| Caller | Scope |
|---|---|
| `ADMIN` | Everything |
| Dispatcher with hubs | Parcels of those hubs (§3) |
| Dispatcher with no row | **Nothing** |

A dispatcher without a hub sees and does nothing, so a forgotten assignment is visible at once
instead of silently granting country-wide access. Only admins work everywhere. A country-wide
"head office" dispatcher was considered and left out (see "Alternatives considered").

Deployment: every existing dispatcher sees an empty portal until an admin gives them a hub. A first
migration marked existing dispatchers as head office and a second one removed those rows again; a
second migration rather than an edit, because a database that ran the first must not see its
checksum change.

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
(ADR 0006) are visible to admins only.

- Dispatch lists show the parcels the caller may act on or read. For a parcel they may only read,
  no actions are offered.
- Hub scans, line-haul, return, failure, cancellation and courier assignment by a dispatcher who is
  not in the responsible hub are refused with `403 Forbidden`. The backend enforces this; the
  filtered lists are a convenience.

### 4. Couriers

- A courier with hubs can be given a task only for a parcel whose responsible hub is one of them:
  the pickup branch for a pickup, the destination hub for a delivery or return.
- A courier with no hub can be assigned only by an admin.
- A hub dispatcher's courier list and workload counts show only couriers who share a hub with them.
- What a courier sees does not change: only their own tasks, as before.

### 5. API

All admin endpoints are `ADMIN` only: they decide who sees which parcels.

- `GET /api/v1/hubs/staff`: every enabled dispatcher and courier by name, with their `hubIds`.
  Someone at no hub shows an empty list, so the admin page can list who still needs a hub. Emails
  are not returned.
- `PUT /api/v1/hubs/{hubId}/staff/{userId}` and `DELETE` of the same path: add to or remove from a
  hub. Adding is idempotent, and `404` for an unknown hub or user, `409` for an inactive hub or a
  user who is not a dispatcher or courier.
- `delivery.api` gains the caller on the dispatch list queries, and a small directory that tells
  `dispatch` who shares a hub with the caller.

### 6. What does not change

Statuses, transitions and events (ADR 0009), customer and merchant access, and public tracking are
unchanged. This ADR replaces only the sentence of ADR 0009 §6 that lets any dispatcher scan at any
hub.

## Alternatives considered

- **A hub ID on the user in `identity`.** Rejected: `identity` would store an ID of a module it
  does not depend on, one hub per user would be the natural limit, and hubs already live in
  `delivery`.
- **Unassigned dispatchers keep country-wide access.** Rejected: access would be granted by
  omission.
- **A "head office" scope for dispatchers.** Tried and removed. It is all or nothing, so a regional
  manager would see every district, it can act and not only watch, and grants and removals leave no
  history. Admins cover the country-wide desk. A read-only supervisor or regional scopes can come
  with a later ADR if a customer needs them.
- **A separate `HUB_STAFF` role** (ADR 0009, deferred). Still deferred: the scope answers "where",
  and the existing roles answer "what".
- **Hiding other hubs' parcels as `404`.** Rejected for staff: a dispatcher who follows a link to
  another hub's parcel should learn that it is not theirs. Customers and couriers keep `404`.

## Consequences

- A branch sees only its own work, and personal data is limited to the hubs on a parcel's route.
- Admins must assign new dispatchers and couriers to hubs; an unassigned dispatcher sees an empty
  portal until they do.
- `dispatch` passes the caller to `delivery` for lists and assignments.
- Scans are still made one parcel at a time; batches and manifests are left to a later ADR.
