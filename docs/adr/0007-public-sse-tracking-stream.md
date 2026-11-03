# 0007. Public SSE tracking stream

- Status: Proposed
- Date: 2026-11-03

## Context

The brief requires real-time parcel tracking (SYS-1, IMP-5, CUS-4) and a public tracking page
reachable with only a tracking number (CUS-6). [ADR 0001](0001-modular-monolith-in-a-monorepo.md)
decided Server-Sent Events (Spring MVC `SseEmitter`) for real-time updates, and
[ADR 0003](0003-backend-modules-and-boundaries.md) gave live connections to the `tracking` module,
which keeps no history and depends only on `delivery`.

How streams are authenticated is open (contract decision D-14, architecture §8). The browser's
native `EventSource` API cannot send an `Authorization` header, so a token-protected stream would
need the token in the URL (leaks into logs and history), cookies (the API has none), or a
polyfill library (a new dependency).

The public, unauthenticated read `GET /api/v1/tracked-parcels/{trackingNumber}` already exists and
returns only status, cities and status timestamps (D-16). [ADR 0006](0006-delivery-lifecycle-and-transition-rules.md)
publishes an in-process `ParcelStatusChanged` event after each committed status change, and
[ADR 0004](0004-messaging-and-integration-reliability.md) keeps personal data out of events.

## Decision

1. **One public stream per tracking number, no authentication:**
   `GET /api/v1/tracked-parcels/{trackingNumber}/events` (`text/event-stream`). Logged-in views
   (customer detail page) use the same stream. There are no authenticated streams in v1.
2. **The stream carries the same data as the public tracking view, never more:**
   - `snapshot` on connect: tracking number, status, pickup and drop-off city, status timeline.
   - `status` after each committed change: `status`, `occurredAt`, `sequence`.
   - No names, phones, addresses, notes, failure reasons, proof, owner or courier IDs.
3. **Unknown or malformed tracking numbers get 404** before a stream opens, the same answer as the
   public read, so numbers cannot be probed through the stream.
4. **`tracking` receives changes in-process** through `@TransactionalEventListener` (after commit) and
   pushes them to open streams. A missed push is repaired by the client reconnecting, which sends a
   fresh snapshot. `tracking` stores nothing.
5. **Connection lifecycle:** heartbeat comment every 25 s; server timeout 30 min (the browser
   reconnects); the stream is completed after `DELIVERED` or `FAILED`.
6. **Resource limits:** at most 20 open streams per tracking number and 1,000 in total; beyond that
   the request is refused with 503. Streams live in memory of the single backend instance.
7. The existing security rule permitting `GET /api/v1/tracked-parcels/**` covers the stream.

## Alternatives considered

- **Token in the query string:** works with `EventSource`, but tokens end up in server logs, proxies
  and browser history.
- **Authenticated stream through a fetch-based SSE client:** needs a new frontend dependency or
  hand-written stream parsing, and still has nothing more to show than the public view.
- **Polling the public read:** allowed by the brief, but SSE was already chosen in ADR 0001.
- **WebSockets / STOMP:** excluded by ADR 0001 without a verified requirement.
- **Fan-out through RabbitMQ for several backend instances:** unnecessary for one instance; revisit
  if the backend is scaled out.

## Consequences

- The tracking page and the customer detail page share one simple stream; `EventSource` reconnects
  by itself.
- Anyone holding a tracking number sees status changes as they happen, exactly as they could by
  polling the public read; no extra information is exposed.
- Open streams hold a servlet async request each; the limits bound the cost, and the public
  endpoints still have no rate limiting (known risk).
- With more than one backend instance, a client only receives changes committed on its instance
  until reconnecting; scaling out needs a new decision.
- Resolves D-14 when accepted; architecture §8 and the contract's D-14 entry are updated then.
