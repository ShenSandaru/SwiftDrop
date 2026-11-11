# 0009. Hub network, delivery attempts, returns and cancellation

- Status: Accepted
- Date: 2026-11-10 (Accepted: 2026-11-10)

## Context

[ADR 0006](0006-delivery-lifecycle-and-transition-rules.md) defines a deliberately small v1
lifecycle: `CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`, with `FAILED` as a
terminal state after any single failed attempt. It explicitly deferred retries and cancellation to
a later ADR (§2, "Alternatives considered"). [ADR 0008](0008-dispatch-assignment-history-and-handoff.md)
added separate pickup and delivery assignments with a handoff after `PICKED_UP`.

Domestic couriers in Sri Lanka (Koombiyo, Domex, Pronto and others) work differently from this
model, and the difference is visible in a demo:

- **Hub network.** Parcels are accepted at the origin **branch**, taken by evening line-haul trucks
  to a **central sorting hub in Colombo**, carried overnight to the **destination branch**, and
  delivered from there by a rider. Parcels within one area never leave their branch. SwiftDrop
  has no notion of where a parcel is between pickup and delivery; `IN_TRANSIT` covers everything.
- **Repeated attempts.** A failed attempt (recipient unreachable, "come tomorrow") is
  **rescheduled**; couriers typically try two or three times (UPS and DPD three, InPost two).
  SwiftDrop ends the parcel's life after one failure.
- **Return to sender.** After the last attempt, or a refusal, the parcel is returned to the sender
  ("Pending return" → "Return received"). With cash on delivery, refusals are frequent, so returns
  are a normal daily flow. SwiftDrop has no return path.
- **Cancellation.** A booking that has not been collected can be cancelled. SwiftDrop cannot cancel.

Cash on delivery is equally common in Sri Lanka but involves the merchant module and settlement of
money; it is left to a separate ADR (see "Consequences").

## Decision

### 1. Hubs

- `delivery` owns a **hub** table: ID, unique code, name, city, an optional **zone ID** (unique; the
  pricing zone the hub serves as its branch) and a **central** flag. Exactly one hub is central
  (enforced by a partial unique index); it may also serve a zone, as Colombo does.
- Every zone a parcel is booked for must be served by a branch hub. Booking a parcel whose pickup
  or drop-off zone has no hub is rejected with `409 Conflict` ("zone not served"). Zone IDs are read
  through `pricing.api`; there is no cross-module foreign key (ADR 0003).
- Admins (`ADMIN`) create and edit hubs. Hubs are never deleted while parcels reference them; they
  can be deactivated, which stops new bookings for their zone.
- **Route.** The origin hub is the branch of the pickup zone and the destination hub the branch of
  the drop-off zone. If they differ and neither is central, the route is origin → central →
  destination; otherwise origin → destination; if they are the same hub, the route is that hub only.
  The route is fixed when the parcel is picked up and stored on the parcel.
- Hubs are not personal data. Hub names and cities may appear in events and public tracking.

### 2. Statuses

| Status | Meaning | Terminal |
|---|---|---|
| `CREATED` | Booked, waiting for pickup | No |
| `CANCELLED` | Cancelled before pickup | **Yes** |
| `PICKED_UP` | Collected by the pickup courier | No |
| `AT_HUB` | Scanned in at a hub of the route; the parcel records **which** hub | No |
| `IN_TRANSIT` | On a line-haul leg from one hub of the route to the next | No |
| `OUT_FOR_DELIVERY` | With a delivery courier, on the way to the recipient | No |
| `DELIVERY_ATTEMPT_FAILED` | An attempt failed; the parcel goes back to the destination hub | No |
| `DELIVERED` | Handed to the recipient with proof | **Yes** |
| `RETURNING` | Being returned to the sender | No |
| `RETURNED` | Handed back to the sender with proof | **Yes** |
| `FAILED` | Cannot be delivered or returned (lost, damaged beyond use) | **Yes** |

The meaning of `FAILED` narrows: it is now an exception state set by a dispatcher, no longer the
result of an ordinary failed attempt.

### 3. Transition matrix

| From | To | Caller | Rules |
|---|---|---|---|
| `CREATED` | `CANCELLED` | Owner (customer or merchant), dispatcher, admin | Closes the active pickup task as `CANCELLED` |
| `CREATED` | `PICKED_UP` | Pickup courier | As ADR 0008; fixes the parcel's route |
| `PICKED_UP` | `AT_HUB` | Dispatcher | Hub must be the route's origin hub |
| `AT_HUB` | `IN_TRANSIT` | Dispatcher | Only if the current hub is not the destination; records the next hub |
| `IN_TRANSIT` | `AT_HUB` | Dispatcher | Hub must be the next hub of the route |
| `AT_HUB` | `OUT_FOR_DELIVERY` | Delivery courier | Current hub is the destination; a `DELIVERY` task is active (§5) |
| `OUT_FOR_DELIVERY` | `DELIVERED` | Delivery courier | Proof required (ADR 0006 §5) |
| `OUT_FOR_DELIVERY` | `DELIVERY_ATTEMPT_FAILED` | Delivery courier | Failure reason required (§4); attempt count + 1; completes the delivery task |
| `DELIVERY_ATTEMPT_FAILED` | `AT_HUB` | Dispatcher | Hub must be the destination hub |
| `AT_HUB` | `RETURNING` | Dispatcher | Parcel is at the destination hub after at least one failed attempt, **and** the attempt limit is reached or the last reason was `REFUSED` or `ADDRESS_NOT_FOUND` |
| `RETURNING` | `RETURNED` | Return courier | A `RETURN` task is active; return proof (name of the person at the sender's address) required |
| any non-terminal except `CREATED` | `FAILED` | Dispatcher, admin | Reason `DAMAGED` or `OTHER` with a note; closes any active task |

- The **attempt limit is 3** (`swiftdrop.delivery.max-delivery-attempts`, default 3, bounds 1 to 5).
  After a failed attempt below the limit with a retryable reason, the dispatcher assigns a new
  delivery task and the next `OUT_FOR_DELIVERY` starts attempt *n* + 1.
- Every other transition, and any transition from a terminal status, is rejected with
  `409 Conflict` (ADR 0006 §7 error conventions are unchanged).
- Scanning a parcel into the wrong hub is a `409`; a parcel can never skip a hub of its route.

### 4. Failure reasons

The `FailureReason` codes of ADR 0006 §4 stay. Retry behaviour per reason:

| Reason | Effect |
|---|---|
| `RECIPIENT_UNAVAILABLE` | Retried until the attempt limit |
| `OTHER` | Retried until the attempt limit |
| `REFUSED` | Returned at once |
| `ADDRESS_NOT_FOUND` | Returned at once |
| `DAMAGED` | Not a delivery attempt: the dispatcher sets `FAILED` |

### 5. Courier tasks (supersedes the matching parts of ADR 0008)

- Task types: `PICKUP`, `DELIVERY` and **`RETURN`**. Task states: `ACTIVE`, `COMPLETED`, `REASSIGNED`
  and **`CANCELLED`**.
- **Pickup** tasks as ADR 0008 §4, including **reassignment while `CREATED`**, which closes the
  previous task as `REASSIGNED` and opens a new one. (The current code refuses this; it is brought
  in line.)
- **Delivery** tasks may be assigned only while the parcel is `AT_HUB` at its destination hub with
  no active task; one delivery task per attempt. A delivery task may be reassigned before the
  courier sets `OUT_FOR_DELIVERY`.
- **Return** tasks may be assigned only while the parcel is `RETURNING` with no active task.
- Hub scans (`AT_HUB`, `IN_TRANSIT`) are not courier tasks and need no assignment.

### 6. Who scans at hubs

Hub scans are performed by the **`DISPATCHER`** role (and `ADMIN`), through `dispatch` calling a new
`delivery.api` method. In this version any dispatcher may scan at any hub; binding dispatchers to a
hub is a possible later refinement.

### 7. Events, tracking and receipts

- Every transition writes history and an outbox `parcel.status-changed` event as today (ADR 0004,
  ADR 0006 §8).
- The event data gains two **optional** fields: `hubCode` (for `AT_HUB` and `IN_TRANSIT`, the hub
  reached or left) and `attempt` (for `OUT_FOR_DELIVERY` and `DELIVERY_ATTEMPT_FAILED`). Adding
  optional fields and status values keeps `schemaVersion` 1; consumers must ignore status values
  they do not act on. No consumer exists yet, so no consumer breaks.
- Public tracking (ADR 0007) shows the route with hub names, the current hub, attempt numbers and
  the return. It still shows no personal data.
- The receipt reflects attempts, `RETURNED` and `CANCELLED` in addition to ADR 0006 §6.

### 8. What does not change

ADR 0006 §5 (proof workflow), §7 (error mapping, 404 for non-owners and non-assigned couriers) and
§8 (one transaction per transition, outbox, after-commit tracking event), and ADR 0004 (outbox
relay, at-least-once delivery) apply unchanged to the new statuses.

This ADR supersedes ADR 0006 §1 to §3 and the assignment rules of ADR 0008 §4 and §5. Everything
else in those ADRs remains in force.

## Alternatives considered

- **Separate `AT_ORIGIN_HUB` and `AT_DESTINATION_HUB` statuses.** Rejected: fixed names cannot
  describe a route through the central hub, and they would have to change again with any new hub.
  One `AT_HUB` status plus the recorded hub covers every route.
- **A single "at hub" status without hub records.** Rejected: tracking could not say where the
  parcel is, and nothing could enforce that a parcel follows its route.
- **A new `HUB_STAFF` role.** Deferred: more realistic, but it needs identity changes and new
  screens. Dispatchers scan for now.
- **Unlimited retries, or two attempts.** Three attempts matches the most common carrier practice;
  the limit is configurable within bounds.
- **Cash on delivery in this ADR.** Deferred to its own ADR with the merchant module owners, because
  it adds money handling, courier cash reconciliation and merchant settlement.

## Consequences

- SwiftDrop's tracking matches what Sri Lankan customers know from real couriers: branch, Colombo
  hub, destination branch, out for delivery, rescheduled, returning.
- Failed attempts no longer end a parcel; returns and cancellations become possible.
- **Work for other modules** (to agree in review):
  - `dispatch` (Member 7): hub scan screen; delivery assignment from the destination hub; return
    tasks; the assignment reassignment fix.
  - `notification` (Member 9): ADR 0004 §8 notification rules extended to
    `DELIVERY_ATTEMPT_FAILED`, `RETURNING`, `RETURNED` and `CANCELLED` (follow-up to ADR 0004).
  - `merchant` (Members 4/5): webhooks carry the new statuses; returns matter to merchants.
  - `pricing` / reports (Member 8): reports on attempts, returns and cancellations.
  - Admin: hub management screen; every zone needs a branch hub before parcels can be booked for it.
- New `delivery` migrations: the hub table, the parcel's route, current hub and attempt count,
  hub ID on history entries, the new task type and state, and widened status check constraints.
  Existing parcels keep their history; parcels in flight when the migration runs keep the old
  rules until they finish (to be detailed in the implementation PR).
- **Follow-up ADRs:** cash on delivery; optionally hub-bound dispatchers and a `HUB_STAFF` role.
- Sources for the courier practice above: carrier help pages for UPS, DPD and InPost on attempt
  counts; Koombiyo, Domex, Pronto and Citypak service and tracking descriptions; and a SLIIT study
  on cash on delivery refusals in Sri Lankan e-commerce.
