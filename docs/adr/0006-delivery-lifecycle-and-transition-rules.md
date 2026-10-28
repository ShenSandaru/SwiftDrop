# 0006. Delivery lifecycle and transition rules

- Status: Proposed
- Date: 2026-10-28

## Context

[ADR 0003](0003-backend-modules-and-boundaries.md) established the backend modular monolith
boundaries and decided that the `delivery` module alone owns parcel state, status transitions, and
history. It defined the basic lifecycle sequence from the assignment brief:
`CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED / FAILED`.

However, ADR 0003 left several operational and transition rules unresolved:
- The exact allowed transitions between statuses and whether transitions can skip steps.
- The stages from which `FAILED` is permitted and the standardized failure reason codes (D-19).
- Assignment and reassignment rules and their status boundaries.
- Delivery proof requirements for `DELIVERED` (D-12) and how captured proof is handled on failure.
- The handling of retries, cancellations, and dispatcher/admin status overrides.
- Receipt availability before and after delivery (D-13).
- HTTP error mapping conventions for lifecycle and ownership violations.

[ADR 0004](0004-messaging-and-integration-reliability.md) established the transactional outbox
pattern, monotonic sequence numbers per parcel, and the requirement that status changes commit
atomically with outbox domain events.

This ADR decides the delivery lifecycle, transition, and proof rules (D-06, D-12, D-13, D-19) to
unblock implementation of the delivery domain and endpoints.

## Decision

### 1. Status Transition Matrix

The `delivery` module strictly enforces the following state transitions:

| Source Status | Target Status | Authorized Caller | Preconditions & Validation Rules | Outbox Event Published |
|---|---|---|---|---|
| `CREATED` | `PICKED_UP` | Assigned Courier | Courier must be assigned (`assigned_courier_id != null`). | Yes (`parcel.status-changed`) |
| `PICKED_UP` | `IN_TRANSIT` | Assigned Courier | Normal sequential progression. | Yes (`parcel.status-changed`) |
| `PICKED_UP` | `FAILED` | Assigned Courier | Mandatory `failureReasonCode` from standard list. | Yes (`parcel.status-changed`) |
| `IN_TRANSIT` | `OUT_FOR_DELIVERY` | Assigned Courier | Normal sequential progression. | Yes (`parcel.status-changed`) |
| `IN_TRANSIT` | `FAILED` | Assigned Courier | Mandatory `failureReasonCode` from standard list. | Yes (`parcel.status-changed`) |
| `OUT_FOR_DELIVERY` | `DELIVERED` | Assigned Courier | Valid delivery proof (`delivery_proofs` record with non-blank `recipient_name`) must exist for the parcel. | Yes (`parcel.status-changed`) |
| `OUT_FOR_DELIVERY` | `FAILED` | Assigned Courier | Mandatory `failureReasonCode` from standard list. | Yes (`parcel.status-changed`) |
| `DELIVERED` | None | None | Terminal state. No further updates permitted. | No |
| `FAILED` | None | None | Terminal state in v1. No retries, transitions, or reopen. | No |

### 2. Terminal States, Cancellations, and Overrides

- `DELIVERED` and `FAILED` are strictly terminal. Once a parcel reaches either status, no further
  status transitions, cancellations, or edits can occur.
- In v1, there is no retry flow after failure, no parcel cancellation by customer or merchant, and
  no manual status override by dispatchers or administrators.
- If a delivery attempt fails, the parcel terminates as `FAILED`. Subsequent re-delivery attempts or
  follow-up shipments require creating a new parcel delivery request.

### 3. Courier Assignment and Reassignment

- Courier assignment is a separate property on `parcels` (`assigned_courier_id`), not a status, and
  does not emit a status-change event.
- Assignment and reassignment are permitted **only while the parcel is in `CREATED` status**.
- Only dispatchers/admins (`DISPATCHER_ADMIN` role via the `dispatch` module calling
  `delivery.api.ParcelAssignment`) may assign or reassign couriers.
- Attempting to assign or reassign a courier when the parcel is in any status other than `CREATED`
  is rejected with `409 Conflict`.
- Once `PICKED_UP` is reached, the assigned courier is immutable.

### 4. Failure Reason Codes (D-19)

- When transitioning to `FAILED`, a valid `failureReasonCode` is mandatory. Permitted values are
  strictly bounded by `com.swiftdrop.delivery.api.FailureReason`:
  - `RECIPIENT_UNAVAILABLE`: Recipient was not present at the drop-off location.
  - `ADDRESS_NOT_FOUND`: Drop-off address could not be located.
  - `REFUSED`: Recipient rejected acceptance of the parcel.
  - `DAMAGED`: Parcel package was damaged in transit and cannot be delivered.
  - `OTHER`: Unforeseen delivery impediment.
- The reason code is stored in `parcel_status_history` and included in the `parcel.status-changed`
  event payload.
- Any optional free-text operational notes are stored in `parcel_status_history` only and are
  strictly excluded from events, webhooks, and public tracking to protect personal data.

### 5. Delivery Proof Workflow and Failure Handling (D-12)

- Proof of delivery requires a non-blank `recipient_name` (personal data) and allows an optional
  photographic image (JPEG or PNG, $\le 2\text{ MB}$, stored as `BYTEA` in `delivery_proofs`).
- The submission uses a **two-step workflow**:
  1. The assigned courier uploads proof via `PUT /api/v1/parcels/{id}/proof` while the parcel is
     `OUT_FOR_DELIVERY`.
  2. The assigned courier transitions status via
     `POST /api/v1/parcels/{id}/status-changes` with `toStatus="DELIVERED"`.
- **Atomic validation guarantee:** The `DELIVERED` transition must verify the existence of valid proof
  for the same parcel within the exact database transaction that updates the status, increments the
  sequence, writes history, and appends the outbox event. Attempting `toStatus="DELIVERED"` without
  valid existing proof is rejected with `409 Conflict`.
- **Handling of proof on subsequent failure:** If proof was uploaded during `OUT_FOR_DELIVERY` but the
  courier subsequently transitions the parcel to `FAILED` (e.g. dispute before handover), the proof
  record is invalidated or retained strictly as an uncompleted attempt. A parcel in `FAILED` status
  is never marked as delivered, and its proof cannot be used to justify completion.
- **Privacy and protection:** Proof data (`recipient_name`, photo) is private operational data. It is
  readable only by the parcel owner (customer/merchant), the assigned courier, and dispatchers/admins
  via authenticated endpoints. It is **never** included in `parcel.status-changed` event payloads,
  webhooks, or public tracking endpoints.

### 6. Receipt Behavior (D-13)

- A receipt (`GET /api/v1/parcels/{id}/receipt`) is accessible to the owning customer and
  dispatchers/admins immediately after parcel creation as an Order/Booking Receipt.
- The receipt reflects the current parcel status:
  - **Before delivery (`CREATED` through `OUT_FOR_DELIVERY`):** Acts as booking confirmation,
    showing tracking number, creation timestamp, addresses, size, weight, fee breakdown, and current
    status (`deliveredAt` and completion proof remain `null`).
  - **Upon `DELIVERED`:** Automatically includes completion details (`deliveredAt`, recipient name).
  - **Upon `FAILED`:** Reflects that the delivery attempt was terminated as failed.
- Calling the receipt endpoint for a non-existent or non-owned parcel returns `404 Not Found`.

### 7. HTTP Error Mapping Conventions

Endpoints in the `delivery` module follow standard RFC 9457 Problem Details error mappings:

- **`400 Bad Request`**: Malformed JSON syntax, missing structurally required fields in the request
  body, or unrecognized enum constants (e.g. invalid status name or unsupported failure reason).
- **`404 Not Found`**: Returned when the parcel ID does not exist, **or** when the authenticated
  caller is not entitled to access or update the parcel. Specifically, if a courier who is not the
  currently assigned courier attempts to change status or upload proof, the API returns `404 Not Found`
  to prevent parcel existence probing and courier assignment enumeration.
- **`409 Conflict`**: Returned when an authenticated, authorized caller submits a structurally valid
  request that violates a domain lifecycle precondition, business rule, or optimistic concurrency check:
  - Transition not permitted by the state machine (e.g. `CREATED → DELIVERED` or `CREATED → IN_TRANSIT`).
  - Attempting any transition on a terminal parcel (`DELIVERED` or `FAILED`).
  - Transitioning to `PICKED_UP` without an assigned courier.
  - Transitioning to `FAILED` without a failure reason.
  - Transitioning to `DELIVERED` without previously captured delivery proof.
  - Attempting courier assignment or reassignment after `CREATED` status.
  - Concurrent update collision detected by aggregate `@Version`.

### 8. Transactional Guarantees and Events

- Every status transition executes in a single database transaction in `delivery`:
  1. The `Parcel` row is updated and locked via optimistic locking (`@Version`).
  2. A new entry is appended to `parcel_status_history` with the incremented sequence, actor ID, and
     timestamp.
  3. A new row is appended to `parcel_outbox_events` with the `parcel.status-changed` payload.
- Upon successful transaction commit, an in-process application event is published for immediate
  dispatch to the `tracking` module's Server-Sent Events (SSE) connections.
- The asynchronous outbox relay forwards the event to RabbitMQ for `notification` and `merchant`
  webhook consumers as defined in ADR 0004.

## Alternatives Considered

- **Allowing `FAILED` from `CREATED`:** Rejected. A parcel that has not yet been picked up cannot
  experience a delivery failure; it must either be reassigned or cancelled.
- **Allowing customer or merchant cancellations in v1:** Deferred. Adds complex outbox cancellation
  cascades across dispatch and couriers. Deferred to a post-v1 ADR.
- **Single atomic multipart delivery endpoint:** Combining proof upload and status transition into a
  single request (`POST /api/v1/parcels/{id}/delivered-with-proof`). Rejected in favor of uniform status
  transition JSON bodies across all lifecycle stages.
- **Returning `403 Forbidden` for non-assigned couriers:** Rejected. In accordance with project security
  rules, returning 403 leaks whether a parcel exists and whether it is assigned. `404 Not Found` ensures
  complete resource privacy.

## Consequences

- The state machine for parcels is deterministic, strictly forward-progressing, and resilient to
  concurrency issues.
- `delivery` maintains strict encapsulation over all lifecycle operations and tables.
- Couriers have clear, validated steps for completing deliveries and recording failures.
- Delivery proof and personal data remain private and decoupled from event publishing.
- Receipt generation is available immediately without blocking on delivery completion.

