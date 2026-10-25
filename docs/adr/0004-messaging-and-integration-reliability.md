# 0004. Messaging and integration reliability

- Status: Accepted
- Date: 2026-10-25

## Context

The assignment requires at least one asynchronous integration pattern for status-change events that
trigger notifications and merchant webhooks (SYS-4), a notification service that consumes status
events (SYS-5), merchant webhooks with retry on failure (IMP-4, MER-3), at least one test of the
event/integration flow (TST-1) and demos of a merchant receiving a webhook and of a notification
triggered by a status change (DEM-2, DEM-4); see [docs/project-requirements.md](../project-requirements.md).

Existing decisions this ADR builds on:

- [ADR 0001](0001-modular-monolith-in-a-monorepo.md): one modular monolith; RabbitMQ through Spring
  AMQP carries asynchronous work.
- [ADR 0002](0002-technology-and-version-baseline.md): RabbitMQ 4.3, Spring AMQP 4.1 and Spring
  Boot-managed versions; only Spring Boot and springdoc are pinned.
- [ADR 0003](0003-backend-modules-and-boundaries.md): `delivery` owns `Parcel` status and history
  and is the only publisher of status-change events; `notification` and `merchant` consume them
  through RabbitMQ and may call `delivery`'s `api`; `tracking` may receive them in-process; side
  effects never block or roll back the status change. ADR 0003 left the reliability design to this
  ADR.
- Project rules: no secrets or personal data in logs; merchant API credentials and webhook secrets
  are stored hashed or encrypted; authorization is enforced on the backend by role and ownership.

Nothing described here is implemented. The current configuration only connects to RabbitMQ
(`backend/src/main/resources/application.properties`); publisher confirms, queues and exchanges are
not configured yet, and the local RabbitMQ (`compose.yaml`) runs the management image without extra
plugins. The problems to solve:

- **Dual write.** A status change is written to PostgreSQL and an event must reach RabbitMQ. The two
  cannot be committed atomically. Publishing after the commit loses the event if the process
  crashes or the broker is unavailable in between; publishing before or inside the transaction can
  announce a change that is then rolled back.
- **Duplicates and order.** Any reliable delivery between these systems is at-least-once, so
  consumers and merchants will see duplicates, and retries can reorder events.
- **Failures outside our control.** Merchant endpoints can be slow, down, wrong or malicious; a
  consumer can hit a bug or a malformed message.

Statements about RabbitMQ and library behavior are marked **(documented)** when they come from the
RabbitMQ 4.3 documentation or from inspecting the local library jars, and are listed under
[To validate before or during implementation](#to-validate-before-or-during-implementation) until
they have been checked against the project's broker and code.

## Decision

Sections 1 to 8 are the architectural decisions. The numbers in section 9 are initial defaults
that can be tuned through configuration without a new ADR. None of this is implemented yet.

### 1. Guarantee and principles

- End-to-end delivery is **at-least-once** for every status change that has committed, under the
  conditions stated in section 3. Nothing in SwiftDrop promises exactly-once delivery.
- Each consumer applies a parcel's events **in sequence order, each exactly once in effect**
  (section 4), even though messages may arrive duplicated, late or out of order. Webhook receivers
  must tolerate duplicates and out-of-order requests.
- No required side effect is silently skipped: an event that cannot be applied is retried,
  deferred, reconciled or dead-lettered, and every one of those states is visible in metrics.
- Business transactions and side effects stay separate: a status change commits on its own;
  notifications and webhooks happen afterwards and their failures never affect it.

### 2. Event contract

- **Name:** `parcel.status-changed`. Event types are lower-case, `<aggregate>.<past-tense-verb>`.
- **Format:** JSON (Jackson 3), defined as a record in `delivery`'s `api` package. AMQP message
  properties: `message-id` = event ID, `type` = event type, `content-type` = `application/json`,
  `timestamp` = occurrence time, persistent delivery mode.
- **Envelope:** `eventId` (UUID, generated when the event is created), `eventType`,
  `schemaVersion` (integer, starts at 1), `occurredAt` (UTC, ISO 8601), `producer` (`delivery`).
  Using UUIDs for event IDs does not decide the ID type of entities, which stays open (ADR 0003).
- **Data:** parcel ID, tracking number, previous status, new status, the per-parcel `sequence`
  (section 4), the parcel's source (customer or merchant), the owning customer or merchant ID, the
  merchant's order reference if any, and a failure-reason **code** when the new status is `FAILED`.
- **No personal data in events:** no names, addresses, phone numbers, free-text notes or proof.
  Consumers that need such data fetch it through the owning module's `api`.
- **Versioning:** adding optional fields keeps the version; consumers ignore unknown fields.
  Removing, renaming or changing the meaning of a field requires a new `schemaVersion`, published
  alongside the old one until all consumers have moved, and is reviewed like an `api` change.
- **Authoritative copy:** each status-history entry stores the event ID and sequence of the event
  it produced, so `delivery` can rebuild the identical event (same ID and data) from history at any
  time, also after the outbox row has been deleted.

### 3. Publishing: transactional outbox in `delivery`

- The status change, its history entry and an **outbox row** with the serialized event are written
  in the same database transaction. The outbox table is owned by `delivery`. If the transaction
  rolls back, no event exists; if it commits, the event is exactly as durable as the status change.
- A **relay** in `delivery`'s infrastructure (a scheduled poller) publishes unpublished rows with
  **publisher confirms** and the **mandatory** flag. A positive confirm without a return counts as
  success; a negative confirm, a returned (unroutable) message or a confirm timeout counts as a
  failure. Rows are marked published only after success. Relay ordering and failure handling are in
  section 4.
- **What this guarantees:** a committed status change is eventually published at least once, as
  long as the database keeps the outbox row, the relay eventually runs, and the broker eventually
  accepts the message. It does **not** make delivery atomic: if the process crashes, or the
  database update fails, after the confirm but before the mark is committed, the relay publishes
  the same event (same event ID) again. Consumers treat it as a duplicate (section 4).
- **What it does not cover:** loss of the database itself, an outbox row deleted before it was
  published (rows are never deleted while unpublished), a failure that never clears (the relay keeps
  retrying and alerts, section 4), or messages lost inside RabbitMQ after it confirmed them (for
  example a lost broker volume; on the single local node quorum queues give no replication).
- `tracking` receives an in-process event after commit. A missed live push is acceptable because
  `tracking` sends the current state from `delivery` when a client connects or reconnects.

| Failure | Effect with the outbox |
|---|---|
| Crash before the commit | No status change and no event; consistent |
| Crash after the commit, before publishing | The row stays unpublished; the relay publishes it after restart |
| Broker unavailable, or a queue full (`reject-publish`) | Publishing fails; rows accumulate and are published later. Status changes keep working. |
| Negative confirm, return or confirm timeout | The row stays unpublished and is retried with backoff; if the broker did accept it, the retry is a duplicate |
| Crash after the confirm, before the mark commits | The affected rows of that poll are published again with the same event IDs; consumers treat them as duplicates |
| Accepted by one bound queue, rejected by another | The publisher sees a failure; the retry duplicates the message in the queue that accepted it |
| A row that keeps failing | Only that parcel's later rows wait; other parcels continue (section 4); the row is never dropped |

### 4. Ordering

#### 4.1 Assigning sequence numbers

- The parcel row holds the last sequence number. A status change locks the parcel row (pessimistic
  lock or optimistic version check, as `delivery` implements updates), increments the number, and
  stores it on the history entry and in the outbox row in the same transaction. A unique constraint
  on parcel ID and sequence makes a duplicate number impossible. `CREATED` has sequence 1.
- Because changes to one parcel are serialized by that lock, a later change of the same parcel
  commits after the earlier one and gets a larger outbox ID. An event with sequence *n* therefore
  implies that sequences 1 to *n*−1 of that parcel exist in `delivery`'s history.

#### 4.2 Relay ordering and failure behavior

- **One relay at a time:** each poll runs in one transaction that first takes a transaction-scoped
  PostgreSQL advisory lock (`pg_try_advisory_xact_lock`). If another instance holds it, the poll is
  skipped. The lock is released when the transaction ends, also after a crash, so no stale lock can
  block the relay.
- **Per-parcel order without global blocking:** the poll reads unpublished rows in outbox-ID order
  and handles them one by one. When a row fails, or is still waiting for its retry time, its parcel
  is marked blocked **for the rest of that poll** and that parcel's later rows are skipped; rows of
  other parcels continue. Marks are committed with the poll's transaction, so a crash before the
  commit republishes that poll's rows (duplicates). This keeps per-parcel publication order in the
  normal case with only a set of parcel IDs in memory, and needs no parallelism.
- **Broker-wide failures stop the poll:** a connection or channel failure, or a confirm timeout,
  ends the poll at once, because every following row would fail the same way. The next poll starts
  again from the oldest unpublished row.
- **Per-row retry:** each outbox row records its publish attempts, the last failure category and a
  next attempt time with exponential backoff (section 9). Rows are never dropped or skipped
  permanently by the relay.
- **Detection and alerting:** metrics for the number of unpublished rows, the age of the oldest
  one, rows above the attempt alert threshold, and the time of the last completed poll. Crossing the
  thresholds in section 9 writes an `ERROR` log line with the outbox and parcel IDs (no payload).
  In this project "alerting" means these metrics and log lines, visible through Actuator and the
  logs; an external monitoring system is out of scope.
- **Recovery:** most causes (broker down, missing binding, full queue) are fixed outside the row,
  after which the rows publish automatically in order. A row that can never be published (for
  example a payload a schema change made invalid) needs a reviewed fix: correcting it through a
  Flyway migration or an audited admin operation, never by deleting it. Consumers that are waiting
  for such an event reconcile it from history meanwhile (section 4.3), so the parcel's side effects
  are not lost.
- **Intentional limitation:** per-parcel order holds only while one relay runs. Several relay
  instances, or partitioning by parcel, are not needed at this scale.

#### 4.3 Consumer policy: duplicates, late events and gaps

Each consumer that has side effects keeps, in tables it owns, the **last applied sequence per
parcel**, the **processed event IDs** and a **pending table** for events that arrived early. This is
processing state, not a copy of parcel history (which ADR 0003 keeps in `delivery`): pending entries
are deleted once applied. Only
parcels relevant to the consumer get state: `notification` tracks customer-created parcels,
`merchant` tracks merchant-created parcels; other events are acknowledged without effect. The
consumer locks the parcel's state row while handling an event, so concurrent deliveries of the same
parcel are serialized. With the last applied sequence *last* and an incoming event with sequence
*s*:

| Case | Action |
|---|---|
| Event ID already processed, or *s* ≤ *last* | Duplicate: acknowledge, no effect. If the event ID is unknown, also count an anomaly metric |
| *s* = *last* + 1 | Apply the effect, set *last* = *s*, record the event ID, then apply any pending events that now follow in order |
| *s* > *last* + 1 (gap) | **Do not apply.** Store the event in the pending table (unique event ID) in the same transaction and acknowledge. It is applied once the missing events have been applied |

- **Reconciliation of gaps:** a scheduled task in the consumer finds pending events older than the
  grace period (section 9). For each such parcel it asks `delivery`'s `api` for the parcel's events
  from *last* + 1 to the pending sequence, rebuilt from status history with their original event IDs
  (section 2), and processes them in order exactly as if they had arrived from RabbitMQ. When the
  original messages arrive later, they are duplicates.
- **Bounded recovery:** reconciliation retries with backoff up to a maximum number of attempts
  (section 9). If it still cannot close the gap (for example `delivery` returns no event for a
  sequence, or applying it keeps failing), the parcel is marked **stuck**: an `ERROR` log line and a
  metric are emitted, and the parcel's later events stay pending. Nothing is dropped; recovery
  after the cause is fixed is a re-run of reconciliation. The pending table is small by nature: a
  parcel has at most six statuses.
- **Late events do not lose side effects:** every event of a relevant parcel is applied exactly once
  in effect and in order, whether it arrived on time, late, from a dead-letter replay or through
  reconciliation. A late event can therefore cause a late notification or webhook; whether
  notifications for statuses already superseded should be suppressed is a product choice left open.
- **Effect per consumer:** for `notification` the effect is the mock message if the status
  triggers one (section 8); for `merchant` it is creating the webhook delivery records (section 7).
  Webhook records are created in sequence order, but retries can still reorder the requests a
  merchant receives, so merchants must apply the highest `sequence` (merchant documentation).
- **Dependency note:** reconciliation needs a read method on `delivery`'s `api`. Both consumers may
  already depend on `delivery` (ADR 0003), but the method itself is an `api` change that is reviewed
  when implemented.

### 5. RabbitMQ topology and dead-lettering

- **Exchange:** one durable topic exchange `swiftdrop.events`, declared by `delivery` (the publisher
  owns the exchange). Routing key = event type (`parcel.status-changed`). Messages are published
  persistent.
- **Queues:** owned, declared and bound by each **consumer** module, named
  `<consumer-module>.<event-type>`: `notification.parcel-status-changed` and
  `merchant.parcel-status-changed`. Each consumer has its own queue, so failures and backlogs stay
  separate.
- **Queue type:** durable quorum queues. On the single local node they give no replication benefit,
  but they are RabbitMQ's recommended durable type and support a delivery limit and at-least-once
  dead-lettering.

#### 5.1 Required dead-letter configuration

| Element | Requirement |
|---|---|
| Source queue (each consumer queue) | Durable quorum queue with: dead-letter exchange `swiftdrop.dead-letter`; dead-letter routing key = the source queue's name; explicit delivery limit (section 9, never `-1`); dead-letter strategy `at-least-once`; overflow `reject-publish`; maximum length (section 9) |
| Dead-letter exchange | Durable direct exchange `swiftdrop.dead-letter`, declared identically by each consumer module (declarations are idempotent) |
| Routing | The dead-letter routing key replaces the original routing key, so each source queue's dead letters reach only its own dead-letter queue |
| Dead-letter queue | Durable quorum queue `<queue>.dlq`, bound to `swiftdrop.dead-letter` with the source queue's name; no dead-letter exchange of its own (no cycle) and no length limit (it must never refuse a dead letter) |
| Persistence | Dead letters keep their original delivery mode, so they survive a broker restart only because the target queue is durable **and** the messages were published persistent |
| Broker prerequisite | The `stream_queue` feature flag must be enabled; at-least-once dead-lettering depends on it |

#### 5.2 Why these settings (documented)

From the RabbitMQ 4.3 quorum-queue and dead-letter documentation:

- A message that exceeds the delivery limit (default 20 since 4.0) is **dropped** unless a
  dead-letter exchange is configured; if the configured exchange is missing, dead letters are
  silently dropped.
- A configured dead-letter exchange is **not** a transfer guarantee. With the default
  `at-most-once` strategy, dead letters are republished without internal confirms and can be lost if
  the target is unavailable.
- `at-least-once` makes the source queue keep a dead letter until every target queue has confirmed
  it. It requires the `stream_queue` feature flag and `overflow` = `reject-publish`; with the
  default `drop-head` it silently falls back to `at-most-once`. RabbitMQ recommends a maximum length
  with it.
- **Costs:** dead-lettered bodies are held in memory until confirmed (more memory and CPU), they
  count toward the source queue's length limit, and a missing, unroutable or unavailable target
  leaves them in the source queue. If that persists, the source queue fills, rejects new
  publications, and the backlog moves back to the outbox.
- **Duplicates:** the internal retry can deliver the same dead letter to the dead-letter queue more
  than once. Replays are harmless because consumers deduplicate (section 4.3).
- Switching a queue from `at-least-once` back to `at-most-once` deletes dead letters not yet
  confirmed, so that change must not be made on queues with unconfirmed dead letters.

#### 5.3 Application retries versus the broker's delivery limit

These are different counters and must not be confused:

| Counter | Kept by | Incremented when | At the limit |
|---|---|---|---|
| Application attempts | The listener, in memory, within one delivery | The listener throws a retryable exception; Spring AMQP retries in-process with backoff, invisible to the broker | The message is rejected **without requeue** and dead-lettered at once |
| Delivery count (`x-delivery-count`) | The quorum queue | In 4.3 (documented): `basic.reject` with requeue, or a channel or connection closed with the message unacknowledged (for example a crash). **Not** `basic.nack` with requeue | The message is dead-lettered |

- Listeners are configured never to requeue a failed message. Normal failures therefore reach the
  dead-letter queue through the application-attempt path, independent of which AMQP call Spring
  AMQP uses (the local `spring-rabbit` 4.1.2 contains both `basicReject` and `basicNack` calls).
- The delivery limit is only a **backstop** for crash loops: a message whose processing repeatedly
  kills the process or the connection is dead-lettered after the limit. A requeue through
  `basic.nack` would not count toward it, which is why requeueing is not used.

#### 5.4 Where the settings live

- Declared as queue arguments by the owning consumer module, so the configuration is versioned in
  the repository without new infrastructure. Spring AMQP's `QueueBuilder` has methods for the
  quorum type, dead-letter exchange and routing key, delivery limit, maximum length and overflow;
  the dead-letter strategy has no builder method and is passed as the `x-dead-letter-strategy`
  argument (documented; not yet tested on the project's broker).
- RabbitMQ recommends policies instead, because arguments cannot change without deleting and
  re-declaring the queue. A later change of these values therefore needs a migration step for
  existing queues.

### 6. Consumers: failures and dead letters

- A consumer acknowledges a message only after its own database transaction (effect, processed event
  ID, sequence or pending entry) has committed.
- **Transient failures** (for example the database briefly unavailable): a bounded number of
  application attempts (section 5.3), then rejected without requeue and dead-lettered.
- **Malformed or unsupported messages** (cannot be parsed, unknown `schemaVersion`, failed
  validation): classified as non-retryable and rejected without requeue immediately, so they are
  dead-lettered with their original body. Spring AMQP's default error handling treats message
  conversion failures as fatal (documented; to validate with the chosen listener configuration).
- **Poison-message recovery:**
  1. The dead-letter queue depth metric and an `ERROR` log line (event ID, queue, failure category)
     reveal the message.
  2. The message is inspected; its death headers (`x-death`, `x-first-death-*`) give the original
     queue and the reason.
  3. The cause is fixed (code, data or a newly supported schema version).
  4. The message is republished to its **source queue only** (default exchange, routing key = queue
     name) with its original body and event ID. The replay is recorded with who performed it, when
     and which event IDs.
  5. The consumer handles it like any other delivery: a duplicate if reconciliation already applied
     it, otherwise applied in order.
- Meanwhile a dead-lettered event does not block its parcel forever: the next event of that parcel
  becomes pending, and reconciliation applies the missing event from history (section 4.3). If the
  same failure occurs there, the parcel becomes stuck and is alerted on.
- The replay mechanism (an admin function in the backend, or the management UI, whose message moving
  needs the shovel plugins that the local setup does not enable) is open.

### 7. Merchant webhooks

#### 7.1 Delivery records and retries

- The `merchant` consumer's effect for an event of a **merchant-owned** parcel is one **webhook
  delivery record** per active endpoint of **that** merchant, unique per event ID and endpoint, so a
  duplicate or reconciled event cannot create a second record. Events for customer-created parcels
  create no records. Sending happens outside the consumer, so a slow merchant never holds a RabbitMQ
  message.
- **Sending is database-driven:** a scheduled sender claims due records with
  `FOR UPDATE SKIP LOCKED`, sets a short lease (next attempt time moved forward) and commits; it
  then sends without holding a database transaction and stores the outcome. If the process crashes
  during a send, the record becomes due again after the lease, so a merchant can receive the same
  event twice.
- Each attempt is recorded: attempt number, time, outcome category, HTTP status if any, duration and
  next attempt time. No response bodies, signatures, secrets or personal data are stored.
- **Success:** any `2xx` response within the timeout. **Retry:** connection errors, timeouts,
  `5xx`, `408` and `429`. **No retry:** `3xx` (redirects are not followed), other `4xx` and a
  refused destination (section 7.3); the record is marked failed.
- **Backoff:** exponential with jitter, capped, with a maximum number of attempts, after which the
  record is marked failed. A `Retry-After` value is honored only up to the delay cap.

#### 7.2 Payload, signing and replay protection

- **Payload:** the event envelope and data from section 2 (no personal data), as the HTTP body.
- **Signing:** HMAC-SHA256 with the endpoint's secret. Headers: `X-SwiftDrop-Event-Id`,
  `X-SwiftDrop-Event-Type`, `X-SwiftDrop-Timestamp` (Unix seconds of this attempt) and
  `X-SwiftDrop-Signature` with the value `v1=<hex HMAC of "<timestamp>.<raw body>">`. Every attempt,
  including a manual redelivery, is signed with a fresh timestamp; the body and event ID stay the
  same.
- **Receiver obligations** (to be documented for merchants and followed by the demo receiver):
  compute the HMAC over the raw body before parsing, compare in constant time, reject timestamps
  outside the tolerance (section 9), deduplicate by event ID for at least the tolerance window, and
  apply the highest `sequence` per parcel. The timestamp limits replay of a captured request; the
  event ID stops duplicates within the window.
- The `v1=` prefix lets a later signature scheme be added in parallel.

#### 7.3 Endpoint safety (SSRF protection)

Merchant-supplied URLs make the backend send requests, so they could target internal systems. The
rules below apply when an endpoint is registered or changed **and again for every attempt**,
including manual redeliveries.

- **URL rules:** absolute URL; scheme `https`; a host name or IP literal; no user information;
  port 443 unless the allowed ports are widened by configuration; no fragment. The host is checked
  by its resolved addresses, so alternative encodings of an address (decimal, octal, IPv4-mapped
  IPv6) cannot bypass the rules.
- **Prohibited destinations:** an attempt is refused if the host resolves to **any** address in
  these ranges (a host with one public and one private address is refused):
  - IPv4: `0.0.0.0/8`, `10.0.0.0/8`, `100.64.0.0/10`, `127.0.0.0/8`, `169.254.0.0/16` (including
    cloud metadata addresses), `172.16.0.0/12`, `192.0.0.0/24`, `192.0.2.0/24`, `192.168.0.0/16`,
    `198.18.0.0/15`, `198.51.100.0/24`, `203.0.113.0/24`, `224.0.0.0/4`, `240.0.0.0/4`.
  - IPv6: `::/128`, `::1/128`, `fc00::/7`, `fe80::/10`, `fec0::/10`, `ff00::/8`, `2001:db8::/32`,
    and addresses embedding IPv4 (`::ffff:0:0/96`, `64:ff9b::/96`, `2002::/16`), which are checked
    against the IPv4 list.
  - The names of the backend's own host and local services, if configured.
- **Redirects** are never followed; the client is configured for this explicitly instead of relying
  on a default. No outbound proxy is used for webhooks.
- **No oracle for internal networks:** merchants see only outcome categories (success, HTTP status,
  timeout, network error, refused destination), not detailed connection errors. Response bodies are
  not read beyond what the client needs and are never stored or shown.
- **Local development:** a separate configuration flag, off by default, allows `http` and loopback
  addresses (`localhost`, `127.0.0.1`, `::1`) for the demo receiver. It never allows private,
  link-local or other prohibited ranges. Production configuration must not enable it, and the
  backend logs a warning at startup when it is on.

**DNS rebinding: the connection must use the checked address.** Checking a host's addresses and then
letting the HTTP client resolve the name again leaves a window in which a malicious DNS server can
answer with a public address for the check and an internal one for the connection. HTTPS does
**not** close this window: the TCP connection to the internal address and the TLS handshake still
happen, which allows probing internal hosts and ports, and any internal service presenting a
certificate trusted by the JVM for that name would complete the handshake. The options:

| Option | How it works | Security | Cost |
|---|---|---|---|
| A. JDK `HttpClient` (through `RestClient`) with a check just before each attempt | Resolve and check; the client resolves again, usually from the JVM's address cache | Window remains whenever the cache entry is missing or expires between the two lookups; no guarantee | No dependency, little code |
| B. JDK client connecting to the checked IP literal | Put the IP in the URL and set the original host name | Breaks TLS: SNI and hostname verification would use the IP; the JDK client restricts the `Host` header and has no per-client hostname verifier, so this needs global system properties that weaken verification | Rejected |
| C. `HttpsURLConnection` with a custom socket factory | Connect to the IP, set SNI and verify the original host name manually | Can be correct but is custom TLS code, easy to get subtly wrong | High complexity; rejected |
| D. A JVM-wide resolver (`InetAddressResolverProvider`, Java 18+) | Filter prohibited answers for every lookup in the JVM | Affects PostgreSQL, RabbitMQ and every other connection; needs allow-lists for infrastructure | Global side effects; rejected |
| E. **Apache HttpClient 5 with a validating `DnsResolver`** | The client calls our resolver, which resolves once, checks all addresses and returns only checked ones; the connection manager connects to exactly those addresses, while TLS SNI and hostname verification use the URL's host name | Closes the window for direct connections, keeps standard certificate and hostname verification | One new dependency (`org.apache.httpcomponents.client5:httpclient5`, version managed by Spring Boot 4.1.2 as 5.6.5, with `httpcore5` 5.4.4); Spring's `HttpComponentsClientHttpRequestFactory` plugs it into `RestClient`; a resolver of a few dozen lines |
| F. Egress proxy that enforces the rules | All webhook traffic goes through a filtering proxy | Strong, independent of application code | Extra infrastructure, against the project's simplicity rule; rejected |

- **Decided:** option **E**. The team approved Apache HttpClient 5 as a dependency for outbound
  merchant webhook requests on 2026-10-25. It is used **only** by the webhook sender; other HTTP
  calls are unaffected. It is the smallest change that makes the connection use the validated
  address while keeping TLS verification standard. Option A was rejected because it cannot
  guarantee that the connection goes to a checked address.
- **Version:** declared without a version so that the Spring Boot BOM manages it (5.6.5 with
  `httpcore5` 5.4.4 in Boot 4.1.2). No manual pin or upgrade without an approved reason. The
  dependency is added only by the change that implements the webhook sender, which also updates
  the version documentation owned by ADR 0002 as the project rules require.
- **Basis:** the local `httpclient5` 5.6.5 jar exposes `DnsResolver` and
  `PoolingHttpClientConnectionManagerBuilder.setDnsResolver` (inspected locally). The behavior below
  has **not** been tested or proven yet.
- **Mandatory implementation and test requirements.** The webhook sender may be enabled outside
  local development only after tests show that:
  1. the validating resolver is the only source of connection addresses, and connections go only
     to permitted, validated addresses;
  2. TLS certificate validation and hostname verification against the URL's host name stay enabled
     (no trust-all or no-op verifier);
  3. redirects are disabled;
  4. DNS resolution and connection handling introduce no avoidable SSRF bypass (for example no proxy
     route, no second resolution, no pooled connection reused for a host that now fails the
     checks);
  5. private, loopback, link-local and other prohibited IPv4 and IPv6 destinations are rejected,
     except for the explicitly enabled local-development configuration.
- If verification shows that option E cannot meet these requirements, the webhook sender stays
  restricted to local development until a new ADR decides otherwise; it does not silently fall back
  to option A.

#### 7.4 Webhook secret encryption

- The backend generates each endpoint's secret (at least 32 random bytes from `SecureRandom`) and
  shows it to the merchant once. Because signing needs the original secret, it is **encrypted, not
  hashed** (the project rule allows either). Inbound merchant API keys remain hashed.
- **Algorithm:** AES-256-GCM using the JDK's `javax.crypto` (no dependency), with a 96-bit nonce
  from `SecureRandom` per encryption and a 128-bit authentication tag. The endpoint ID and key ID
  are passed as additional authenticated data, so a ciphertext copied to another row fails to
  decrypt. Spring Security Crypto's `AesBytesEncryptor` (GCM, already on the classpath) was
  considered; its interface has no additional authenticated data, so plain JCA is preferred.
- **Nonce uniqueness:** a fresh random nonce for every encryption, including re-encryption during
  rotation; nonces are never derived from IDs or counters. Encryptions happen only when a secret is
  created, rotated or re-encrypted, so the number per key stays many orders of magnitude below the
  limit NIST SP 800-38D sets for random nonces.
- **Stored form:** key ID, nonce and ciphertext with tag, in the `merchant`-owned endpoint table.
- **Authentication-tag validation:** decryption fails on any tag mismatch (tampering, wrong key or
  wrong row). A failure never falls back to sending unsigned or with another secret.
- **Keys and rotation:** keys come from the environment as a set of `key ID → 256-bit key` entries
  plus the ID of the active key; values are never committed, and `.env.example` gets placeholders
  only. New secrets use the active key. To rotate, add a new key and make it active, re-encrypt
  existing secrets in a controlled job, then remove the old key once no row refers to it. Rotating
  a merchant's **secret** (not the key) is a separate merchant-facing operation (open).
- **Key unavailable:** if the active key is missing or malformed, the backend refuses to start
  where webhooks are enabled. If a record's key ID is unknown or decryption fails, the attempt is
  not sent, the record is set to a `secret-unavailable` state that does not consume attempts, a
  metric and an error log line (endpoint ID and key ID only) are emitted, and sending resumes once
  the key is restored. Losing a key for good means merchants must get new secrets.
- Secrets, keys, signatures and decrypted values are never logged, returned after creation, or
  included in error messages.

#### 7.5 Authorization, visibility and redelivery

- Merchant access is always scoped by the merchant ID of the authenticated principal, never by an
  ID taken from the request. Merchants can see and manage **only their own** endpoints and delivery
  records; a request for another merchant's endpoint or record returns `404`, so IDs cannot be
  probed. Only approved merchants can register endpoints.
- Users with the Dispatcher/Admin role (one role, SYS-2) can view all delivery records and disable
  an endpoint; nobody can read secrets.
- **Manual redelivery** applies to a record in the failed state, and is allowed to the owning
  merchant and to Dispatcher/Admin users. It:
  - re-checks authorization and that the endpoint is still active;
  - re-validates the URL and destination for every attempt (section 7.3);
  - sends the original body and event ID, signed with the endpoint's current secret and a fresh
    timestamp, so the receiver's deduplication still recognizes it;
  - starts a new bounded attempt series with the normal limits, and is refused while a series is
    still running; the number of manual redeliveries per record is capped (section 9);
  - is recorded with who triggered it (user ID and role), when, and the resulting attempts.

### 8. Notifications

- **Channel:** a **mock SMS** channel that stores each message in a `notification`-owned table
  instead of sending it, addressed to the recipient's user account and viewable in the application
  for the demo. This meets the brief's "email or a mock SMS service" without a new dependency or
  external provider. Message content and phone numbers are never logged.
- **Initial policy:**

  | Parcel source | Recipient | Triggering statuses |
  |---|---|---|
  | Customer-created | The customer who owns the parcel | `PICKED_UP`, `OUT_FOR_DELIVERY`, `DELIVERED`, `FAILED` |
  | Merchant-created | No notification; the merchant is informed by webhooks for every status change | none |

  `CREATED` is not notified because the customer has just created the parcel, and `IN_TRANSIT` is
  skipped to limit noise. Statuses that do not trigger a message still advance the consumer's
  sequence (section 4.3). Notifying the end recipients of merchant parcels needs contact data and
  consent rules and is deferred.
- **Failures:** storing a mock message happens in the consumer's transaction, so it follows the
  consumer rules in sections 4.3 and 6. A real provider, if added later, needs its own delivery
  records and retries like webhooks.

### 9. Guarantees versus configuration

The guarantees in sections 1 to 8 are architectural; changing them needs a new ADR. The values below
are initial defaults, set through configuration (environment variables, with local and demo
overrides), and can be tuned without an ADR as long as they stay within the bounds.

| Setting | Initial default | Bounds |
|---|---|---|
| Relay poll interval | 1 s | 0.5 to 10 s |
| Relay batch size | 100 rows | 1 to 500 |
| Publisher confirm timeout | 5 s | 1 to 30 s |
| Outbox row publish backoff | 1 s, ×2, max 1 min | max at most 5 min |
| Relay alert thresholds | oldest unpublished row older than 2 min; a row with 10 or more attempts; no completed poll for 1 min while rows are unpublished | n/a |
| Published outbox row retention | 7 days, then deleted by a daily cleanup; unpublished rows are never deleted automatically | at least 1 day |
| Consumer application attempts | 3 (initial + 2 retries), backoff 1 s, ×2, max 10 s | 1 to 5 attempts, max delay 30 s |
| Quorum-queue delivery limit | 5 | 2 to 20, never unlimited |
| Consumer queue maximum length | 100,000 messages | must be set: RabbitMQ recommends it with `at-least-once`, and `reject-publish` has no effect without it |
| Gap grace period before reconciliation | 60 s | 10 s to 10 min |
| Reconciliation attempts before "stuck" | 5, backoff 1 min, ×2 | 3 to 10 |
| Processed-event ID retention | 30 days (duplicates older than that are still caught by the last applied sequence) | at least the dead-letter retention below |
| Dead-letter retention before review | 14 days (operational practice, not a broker setting) | n/a |
| Webhook attempts per series | 8 | 3 to 15 |
| Webhook backoff | 30 s base, ×2, ±20 % jitter, 1 h cap (about 1 h until the last attempt) | base 1 s to 5 min; demo profile may use 2 s |
| Webhook connect / response timeout | 5 s / 10 s | connect at most 10 s, response at most 30 s |
| Webhook sender poll and lease | every 5 s, batch 20, lease 2 min | lease longer than connect + response timeout |
| Manual redeliveries per record | 3 | at least 1 |
| Signature timestamp tolerance (receivers) | 5 min | 1 to 10 min |

### 10. Observability

- Log lines for publishing, consuming, reconciliation and webhook attempts include the event ID,
  parcel ID, endpoint ID and attempt number, never personal data, secrets, signatures or message
  bodies.
- Metrics through Micrometer (already present with Actuator): unpublished outbox rows, the age of
  the oldest one, rows above the attempt threshold and the time of the last completed relay poll;
  consumer failures, duplicates, anomalies, pending events, reconciliations and stuck parcels;
  messages in each dead-letter queue; webhook attempts by outcome, records in `secret-unavailable`
  and refused destinations.
- The existing RabbitMQ health check stays; queues and dead-letter queues are visible in the
  management UI locally.

### 11. Testing requirements

Unit and component tests run in the default Docker-free `clean verify` with the existing test
dependencies (JUnit, Mockito, AssertJ, spring-test). Tests needing real PostgreSQL and RabbitMQ wait
for the separate integration-test tool decision; this ADR adds no test dependency.

- **Outbox crash windows and duplicate publication** (relay with a mocked `RabbitTemplate` and
  repository): crash before publish leaves the row unpublished; a negative confirm, a return and a
  confirm timeout leave it unpublished with a later retry time; a confirm followed by a failed mark
  leads to a second publish with the **same** event ID.
- **Relay ordering and blocking:** rows are published in outbox-ID order; a failing row skips only
  its own parcel's later rows while other parcels are published; a connection failure ends the
  poll; a second relay instance does not run while the lock is held; the alert thresholds produce
  the metric and log line.
- **Consumer ordering:** sequence numbers increase by one per status change and are unique per
  parcel; a duplicate and an event with *s* ≤ *last* have no effect; a gap event is stored pending
  and not applied; the missing event applies both in order; reconciliation after the grace period
  applies the missing events from history with their original IDs, and the late original is then a
  duplicate; a reconciliation that keeps failing marks the parcel stuck without dropping events.
- **Consumer failures:** a permanent failure is rejected without requeue; transient failures are
  retried the configured number of times; listeners never requeue.
- **Webhook retries:** `2xx`, `3xx`, `4xx`, `408`, `429`, `5xx` and timeout cases against
  `MockRestServiceServer`; backoff and jitter bounds with a fixed `Clock` and seeded randomness; the
  attempt limit; a crash after claiming a record makes it due again after the lease; a duplicate or
  reconciled event creates no second record.
- **SSRF rejection** (validator with a stubbed resolver): every prohibited IPv4 and IPv6 range,
  IPv4-mapped and other embedded forms, decimal and octal literals, user information, `http` and
  non-allowed ports outside local mode, a host with mixed public and private addresses, and a
  rebinding case where the host resolves to a public address at registration and a private one at
  send time. Redirect responses are not followed. For the Apache HttpClient 5 client: the
  connection uses only the addresses the resolver returned; a certificate for another host name
  and an untrusted certificate are rejected; no proxy is used; a pooled connection is not reused for
  a host that now fails the checks.
- **Signatures and replay protection:** the signature matches fixed test vectors; a reference
  verifier (used by the demo receiver) rejects a changed body, a wrong secret, an old timestamp and
  a repeated event ID; a redelivery carries a fresh timestamp and the same event ID.
- **Secret encryption failures:** two encryptions of the same secret differ (fresh nonce); a
  tampered ciphertext, nonce or tag, a wrong key, a ciphertext moved to another endpoint row and an
  unknown key ID all fail and lead to `secret-unavailable` without sending; a missing active key
  prevents startup; no secret appears in logs or responses.
- **Merchant isolation:** merchant A cannot list, view, change or redeliver merchant B's endpoints
  or records (`404`); an event for A's parcel creates records only for A's endpoints; customer
  parcels create none; redelivery checks authorization, the attempt series and the cap, and records
  an audit entry.
- **Notifications:** the policy table in section 8, including no notification for merchant parcels
  and no second message for a duplicate.
- **Integration (TST-1, needs the tool decision):** a status change goes through the outbox and
  RabbitMQ to a stored notification and a webhook delivery record; a malformed message reaches the
  dead-letter queue with its death headers; a message whose consumer connection is closed repeatedly
  is dead-lettered after the delivery limit; a replayed dead letter is processed once; the queue
  arguments in section 5 are accepted by RabbitMQ 4.3 with at-least-once dead-lettering active.

### 12. Dependencies

The design needs **one new dependency**, approved on 2026-10-25: Apache HttpClient 5
(`org.apache.httpcomponents.client5:httpclient5`, Boot-managed version) for the webhook sender
only (section 7.3). It is added when the webhook sender is implemented, not before. Everything else
uses what is already present: Spring AMQP 4.1's retry support is built in (its POM has no
`spring-retry` dependency), AES-GCM is in the JDK, `RestClient` comes from Spring, and scheduling
uses Spring's `@Scheduled`.

## Alternatives considered

- **Publish after commit without an outbox:** simplest, but a crash or broker outage after the
  commit silently loses the event, and with it a notification or webhook the assignment requires.
- **Publish before or inside the database transaction:** can announce a change that is then rolled
  back.
- **RabbitMQ channel transactions or publisher confirms alone:** they make the broker side reliable
  but do not coordinate with the database, so the dual-write problem remains.
- **Spring Modulith event publication registry:** an outbox-like mechanism, but not managed by
  Spring Boot; it would need a pinned version and a change to ADR 0002's policy.
- **Change data capture (for example Debezium):** extra infrastructure that no requirement
  justifies, against the project's simplicity rule and the infrastructure limits in ADR 0001's
  alternatives.
- **A relay that stops at the first failed row:** simplest ordering, but one problematic event
  blocks every unrelated parcel; skipping only the failed parcel's rows costs one in-memory set.
- **Several relay instances or partitioning by parcel:** more throughput, but more coordination;
  not needed at this scale.
- **Applying gap events immediately ("latest status wins"):** simple, but a missing event's side
  effect (for example a webhook for `PICKED_UP`) would be lost or arrive after a later status.
- **Ignoring late events as stale:** loses required side effects.
- **Waiting for missing events without reconciliation:** an event stuck in a dead-letter queue
  would block its parcel indefinitely.
- **Relying on the quorum-queue delivery limit for poison messages:** in 4.3 `basic.nack` with
  requeue does not count toward it, so a requeue loop might never end; explicit no-requeue
  rejection is used instead.
- **Default `at-most-once` dead-lettering:** less memory, but dead letters can be lost in transit;
  dead letters here are the only copy of a failed delivery to that consumer.
- **RabbitMQ policies for queue settings:** recommended by RabbitMQ and changeable at runtime, but
  they live outside the application and need extra infrastructure configuration; may be adopted
  later.
- **Webhook retries through RabbitMQ delay queues (TTL and dead-lettering per delay):** works, but
  needs one queue per delay, gives no queryable history for the merchant dashboard, and duplicates
  the delivery records that are needed anyway. A delayed-message plugin would be extra broker
  infrastructure.
- **Calling the merchant webhook inside the status transaction:** a slow or failing merchant would
  block or roll back the status change, contradicting ADR 0003.
- **Validating webhook URLs only at registration:** defeated by DNS rebinding.
- **Other SSRF options (B, C, D, F in section 7.3):** rejected for broken TLS verification, custom
  TLS code, JVM-wide side effects or extra infrastructure.
- **One shared queue for all consumers or a fanout exchange:** a shared queue couples the consumers'
  failures and backlogs; a topic exchange allows more event types later without new exchanges.
- **Classic queues:** acceptable on one node, but no delivery limit and no at-least-once
  dead-lettering.
- **Events carrying personal data ("fat" events):** fewer lookups, but spreads personal data into
  the broker, dead-letter queues and merchant systems.
- **Webhook authentication by a static shared token or mutual TLS:** a static token can be replayed
  and does not protect the body; mutual TLS is too heavy for the merchants and the demo.
- **Hashing webhook secrets:** impossible, because signing needs the original secret.
- **Kafka:** allowed by the brief, but RabbitMQ is the accepted baseline (ADR 0001, ADR 0002).

## Consequences

- A committed status change is published at least once under the conditions in section 3, at the
  cost of an outbox table, a relay and its monitoring. Delivery is not atomic, so duplicates are
  normal and are absorbed by event IDs and the last applied sequence.
- Each consumer with side effects needs three small tables (last applied sequence, processed event
  IDs, pending events) and a reconciliation task, and `delivery`'s `api` needs a method that
  rebuilds a parcel's events from history.
- Late events cause late side effects rather than lost ones; a gap delays a parcel's later effects
  by up to the grace period.
- A failing outbox row delays only its own parcel; broker-wide failures delay everything until the
  broker recovers, which the relay metrics reveal.
- At-least-once dead-lettering costs broker memory and requires queue length limits; changing queue
  arguments later means re-declaring queues.
- Webhook delivery records give merchants and Dispatcher/Admin users visible, isolated history and
  an audited manual redelivery path; dead-letter replays are audited too.
- Webhook secrets depend on encryption keys managed through the environment; losing a key means
  issuing new merchant secrets.
- Closing the DNS rebinding window relies on Apache HttpClient 5 and a validating resolver; the
  webhook sender may run outside local development only after the mandatory tests in section 7.3
  pass.
- The required integration test needs real PostgreSQL and RabbitMQ, so the test-tool decision
  becomes necessary before messaging is implemented.
- Implementation will need configuration not present today: publisher confirms and returns for the
  RabbitMQ connection, and environment placeholders for the encryption keys.

### Open implementation questions

- How dead-lettered messages are replayed: an admin function in the backend, or enabling the shovel
  plugins for the management UI (an infrastructure change).
- Whether queue settings move from arguments to RabbitMQ policies.
- How a stuck parcel or an unpublishable outbox row is repaired: an audited admin operation or a
  reviewed migration.
- Whether notifications for statuses already superseded by a later status are suppressed.
- The list of failure-reason codes, and whether a free-text reason may ever leave `delivery`.
- Notifications for the end recipients of merchant-created parcels (contact data and consent).
- One or several webhook endpoints per merchant, event filtering, and merchant-facing secret
  rotation (including a grace period with two valid secrets).
- The merchant endpoint used in the demo (a local stub or an external test receiver).
- The integration-test tool and how it runs without making the default build require Docker.
- The names and format of the environment variables for the encryption keys.
- The final values in section 9, to be confirmed during implementation.

### To validate before or during implementation

These points come from the RabbitMQ 4.3 documentation or from inspecting local jars, and have not
been tested against a running broker or a running client in this project. Each must be verified by
the implementing change:

- That the `stream_queue` feature flag is enabled on the project's RabbitMQ 4.3 node.
- That RabbitMQ 4.3 accepts the `x-dead-letter-strategy` argument together with the other queue
  arguments in section 5.1, and that dead letters are confirmed into `<queue>.dlq` (at-least-once
  active, not silently `at-most-once`).
- That the delivery count behaves as documented: `basic.reject` with requeue and closed connections
  increment it, `basic.nack` does not.
- Which rejection call Spring AMQP uses in the chosen listener configuration, and that conversion
  failures are treated as fatal; the design does not depend on either, but the tests confirm it.
- That `RabbitTemplate` publishes persistent messages by default, or that the relay sets it.
- The five mandatory Apache HttpClient 5 requirements in section 7.3: connections only to
  addresses returned by the validating resolver, TLS certificate and hostname verification enabled,
  redirects disabled, no avoidable SSRF bypass in resolution or connection handling, and rejection
  of prohibited IPv4 and IPv6 destinations outside the local-development configuration.
