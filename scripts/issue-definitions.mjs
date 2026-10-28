/**
 * SwiftDrop Planned GitHub Issues Definition
 *
 * Sourced directly from docs/work-plan.md, docs/implementation-contract.md, and accepted ADRs.
 * Only covers genuine remaining/active work.
 */

export const ISSUES = [
  // ==========================================
  // SD-004 · Code Coverage
  // ==========================================
  {
    id: "SD-004",
    title: "SD-004: Configure measurable JaCoCo code coverage in Maven and CI",
    module: "Quality & CI",
    labels: ["quality", "backend", "ci"],
    blockedBy: "Team approval of D-11 (JaCoCo plugin baseline)",
    body: `### Objective
Configure measurable test code coverage reporting for backend unit and integration test executions without breaking Docker-free build workflows.

### Scope
- Add and pin \`jacoco-maven-plugin\` in \`backend/pom.xml\` once team decision D-11 is approved.
- Configure coverage report generation (HTML + XML) for \`clean verify\` and \`clean verify -Pintegration\`.
- Update GitHub Actions CI (\`.github/workflows/ci.yml\`) to upload JaCoCo coverage reports as workflow artifacts.
- Propose amendment to \`docs/adr/0002-technology-and-version-baseline.md\` for the agreed plugin version.
- Out of scope: Strict coverage failure thresholds (deferred until team agreement).

### Implementation Guidance
- Follow ADR 0002 for Maven plugin management conventions.
- Ensure \`./mvnw clean test\` and \`./mvnw clean verify\` execute cleanly without requiring Docker.
- In CI, archive \`target/site/jacoco\` as an artifact upon build completion.
- Note: Do not modify accepted ADR 0002 without explicit team sign-off on decision D-11.

### Consistency Rules
- Keep Maven build reproducible and warnings-free.
- Do not introduce external SaaS coverage reporters or tokens.

### Testing
- Verify \`mvnw clean verify\` generates \`target/site/jacoco/index.html\` locally.
- Verify GitHub Actions workflow parses and runs successfully.

### Acceptance Criteria
- [ ] \`jacoco-maven-plugin\` configured in \`backend/pom.xml\`.
- [ ] HTML and XML reports generated upon running verify goals.
- [ ] CI workflow uploads JaCoCo report artifact.
- [ ] \`docs/adr/0002-technology-and-version-baseline.md\` updated with the pinned version.

### Dependencies
- Blocked by team acceptance of decision D-11 (JaCoCo plugin baseline).
`,
  },

  // ==========================================
  // SD-107 · Google OAuth Verification
  // ==========================================
  {
    id: "SD-107",
    title: "SD-107: Perform and document real Google OAuth round-trip verification",
    module: "Identity",
    labels: ["identity", "security", "docs"],
    body: `### Objective
Verify the end-to-end Google OAuth 2.0 / OpenID Connect token handoff against Google's live identity provider and document the verification evidence.

### Scope
- Configure a local development environment with valid team Google OAuth credentials (in local, git-ignored \`.env\` only).
- Test initial customer signup/login via Google redirect flow.
- Verify token handoff via one-time code exchange (\`POST /api/v1/auth/google/exchanges\`).
- Test subsequent login resolving to the same \`external_identities\` record.
- Verify collision handling when an existing email was registered via local password (returns 409 Conflict with appropriate error code).
- Update \`docs/architecture.md\` §9 with verification date and outcome.

### Implementation Guidance
- Refer to [ADR 0005](docs/adr/0005-google-oauth-token-handoff.md) and \`GoogleLoginCodeRepository\`.
- Ensure no credentials or tokens are committed to git or exposed in logs.
- Note: Upstream backend handoff contract (SD-104) is already merged; this task is dependency-ready for testing with live Google OAuth credentials.

### Acceptance Criteria
- [ ] First sign-in creates a customer user with linked Google sub in \`external_identities\`.
- [ ] Subsequent sign-in finds and authenticates the existing user.
- [ ] Email conflict path returns HTTP 409 with problem-details body.
- [ ] Verification outcome and date recorded in \`docs/architecture.md\` §9.

### Dependencies
- Upstream SD-104 (token handoff implementation) is already merged; no pending blocking issues.
`,
  },

  // ==========================================
  // SD-009 · Demo and Seed Data
  // ==========================================
  {
    id: "SD-009",
    title: "SD-009: Implement demo and seed data for local evaluation and profiles",
    module: "Deployment",
    labels: ["deployment", "infrastructure", "backend", "demo"],
    dependsOn: ["SD-201", "SD-305", "SD-401"],
    blockedBy: "Team approval of D-27 (demo credentials configuration)",
    body: `### Objective
Provide idempotent seed data under a dedicated \`demo\` Spring profile to populate realistic demonstration entities (zones, pricing rules, staff, courier, and merchant accounts) for evaluation and local testing.

### Scope
- Implement a seed runner or database initializer activated exclusively under \`spring.profiles.active=demo\`.
- Seed items: delivery zones, initial pricing rules, one bootstrap admin, two active courier profiles, and one approved merchant account.
- Must execute idempotently (safe on repeated application startups without duplicating rows).
- Demo passwords and secrets must be injected via environment variables (never hardcoded in source code or Git).
- Strictly excluded: placing demo or seed data in Flyway production migrations.

### Implementation Guidance
- Follow ADR 0003 and implementation contract §1 (D-27).
- Ensure \`DatabaseMigrationIT\` and standard production migrations remain free of sample/seed data.
- Document demo credentials and profile activation in \`docs/development-setup.md\`.

### Acceptance Criteria
- [ ] Activating the \`demo\` profile populates zones, pricing rules, courier, and merchant data.
- [ ] Initializer is fully idempotent across restarts.
- [ ] No seed data inserted into production Flyway migration scripts.
- [ ] Credentials configured via environment variables with placeholders in \`.env.example\`.

### Dependencies
- Depends on SD-201 (pricing schema), SD-305 (courier profiles), SD-401 (merchant aggregate).
- Blocked by team approval of decision D-27 (demo credentials configuration).
`,
  },

  // ==========================================
  // SD-201 · Pricing Schema
  // ==========================================
  {
    id: "SD-201",
    title: "SD-201: Implement pricing schema, zones, pricing rules, and JPA repositories",
    module: "Pricing",
    labels: ["pricing", "database", "backend"],
    body: `### Objective
Establish the database schema and domain entity mappings for delivery zones and pricing rules to support server-side delivery fee calculation.

### Scope
- Create Flyway migration script under \`backend/src/main/resources/db/migration/pricing/V<timestamp>__pricing_create_zones_and_pricing_rules.sql\`.
- Create tables: \`zones\` and \`pricing_rules\`.
- Create JPA entities (\`Zone\`, \`PricingRule\`) and Spring Data JPA repositories in \`com.swiftdrop.pricing.domain\`.
- Out of scope: Fee calculation logic (owned by SD-202) and HTTP controller endpoints (owned by SD-203, SD-211).

### Implementation Guidance
- Contract: docs/implementation-contract.md §5.2.
- Schema constraints:
  - \`zones\`: \`id UUID PRIMARY KEY\`, \`code VARCHAR(50) NOT NULL UNIQUE\`, \`name VARCHAR(200) NOT NULL\`, \`active BOOLEAN NOT NULL DEFAULT true\`, \`version BIGINT NOT NULL DEFAULT 0\`, \`created_at TIMESTAMPTZ NOT NULL\`.
  - \`pricing_rules\`: \`id UUID PRIMARY KEY\`, \`origin_zone_id UUID NOT NULL REFERENCES zones(id)\`, \`destination_zone_id UUID NOT NULL REFERENCES zones(id)\`, \`base_fee NUMERIC(12,2) NOT NULL CHECK (base_fee >= 0)\`, \`per_kg_fee NUMERIC(12,2) NOT NULL CHECK (per_kg_fee >= 0)\`, \`small_size_surcharge NUMERIC(12,2) NOT NULL DEFAULT 0.00\`, \`medium_size_surcharge NUMERIC(12,2) NOT NULL DEFAULT 0.00\`, \`large_size_surcharge NUMERIC(12,2) NOT NULL DEFAULT 0.00\`, \`currency CHAR(3) NOT NULL DEFAULT 'LKR'\`, \`active BOOLEAN NOT NULL DEFAULT true\`, \`version BIGINT NOT NULL DEFAULT 0\`, \`created_at TIMESTAMPTZ NOT NULL\`.
  - Unique constraint on \`(origin_zone_id, destination_zone_id)\`.

### Consistency Rules
- Adhere strictly to ADR 0003: \`pricing\` has zero dependencies on other business modules.
- UUID primary keys, optimistic locking with \`@Version\`.
- Must satisfy \`MigrationConventionTest\` and \`DatabaseMigrationIT\` (PostgreSQL 18).

### Testing
- Unit tests for entity invariants and validation.
- Integration test running via Testcontainers PostgreSQL 18.

### Acceptance Criteria
- [ ] Flyway migration executes cleanly and adheres to naming conventions.
- [ ] JPA entities map cleanly with \`hibernate.ddl-auto=validate\`.
- [ ] Positive amount and unique zone pair constraints enforced.
- [ ] Passes \`MigrationConventionTest\` and \`DatabaseMigrationIT\`.
`,
  },

  // ==========================================
  // SD-202 · Fee Calculator Strategy
  // ==========================================
  {
    id: "SD-202",
    title: "SD-202: Implement FeeCalculator strategy service in the pricing module",
    module: "Pricing",
    labels: ["pricing", "backend"],
    dependsOn: ["SD-201"],
    body: `### Objective
Implement the \`FeeCalculator\` public API interface to compute delivery fee breakdowns using database pricing rules, parcel size surcharges, and chargeable weight tiers.

### Scope
- Implement \`com.swiftdrop.pricing.api.FeeCalculator\` in \`com.swiftdrop.pricing.application\`.
- Retrieve active pricing rule for origin and destination zones.
- Apply fee formula: \`totalFee = baseFee + (chargeableKg * perKgFee) + sizeSurcharge\`.
- Handle ceiling weight rounding (1g -> 1kg; 1000g -> 1kg; 1001g -> 2kg).
- Return immutable \`FeeQuote\` record with 2-decimal \`BigDecimal\` arithmetic (\`RoundingMode.HALF_UP\`) and \`LKR\` currency.
- Throw domain exception mapped to 409 Conflict if no active rule or zone is found.

### Implementation Guidance
- Existing public API contracts: \`FeeCalculator.java\`, \`FeeQuote.java\`, \`ParcelSize.java\` in \`com.swiftdrop.pricing.api\`.
- Ensure only \`com.swiftdrop.pricing.api\` types are exposed across module boundaries.

### Testing
- Unit tests covering boundary weights (1g, 1000g, 1001g, 30000g), every \`ParcelSize\`, zero weight surcharge, and missing/inactive rules.

### Acceptance Criteria
- [ ] \`FeeCalculator\` implemented and registered as a Spring bean.
- [ ] Calculation strictly adheres to formula in contract §5.2.
- [ ] Missing rule throws appropriate exception resulting in 409 Conflict.
- [ ] Boundary tests pass cleanly.
`,
  },

  // ==========================================
  // SD-203 · Public Price Quote Endpoint
  // ==========================================
  {
    id: "SD-203",
    title: "SD-203: Implement public delivery price-quote REST endpoint",
    module: "Pricing",
    labels: ["pricing", "api", "backend"],
    dependsOn: ["SD-202"],
    body: `### Objective
Provide a public REST endpoint enabling customers, merchants, and the frontend to calculate an instant fee quote for a proposed delivery route and parcel tier.

### Scope
- Create \`POST /api/v1/price-quotes\` in \`com.swiftdrop.pricing.web\`.
- Request DTO: \`originZoneId\` (UUID), \`destinationZoneId\` (UUID), \`size\` (ParcelSize), \`weightGrams\` (int, 1-30,000).
- Public endpoint (accessible without authentication).
- Response DTO: totalFee, currency, baseFee, weightFee, sizeFee, pricingRuleId.

### Implementation Guidance
- Jakarta validation on request record; violations return standardized 400 Bad Request via \`GlobalExceptionHandler\`.
- Add endpoint entry to \`SecurityConfiguration\` permitting unauthenticated access.
- Add test row in \`IdentityAuthorizationMatrixTest\`.

### Testing
- MockMvc tests for validation errors (weight <= 0, weight > 30000, null IDs).
- MockMvc tests verifying public accessibility without bearer token.

### Acceptance Criteria
- [ ] \`POST /api/v1/price-quotes\` returns HTTP 200 with complete \`FeeQuote\` details.
- [ ] Input validation errors return HTTP 400 with sorted field names.
- [ ] Inactive or non-existent zone pair returns HTTP 409.
- [ ] Authorization matrix tests pass.
`,
  },

  // ==========================================
  // SD-211 · Pricing Admin API
  // ==========================================
  {
    id: "SD-211",
    title: "SD-211: Implement pricing administration REST endpoints for zones and rules",
    module: "Pricing",
    labels: ["pricing", "api", "admin", "backend"],
    dependsOn: ["SD-201"],
    body: `### Objective
Provide administrative REST endpoints for Dispatcher/Admins to manage delivery zones and pricing rules.

### Scope
- \`GET /api/v1/zones\` (public read of active zones; admin read of all zones).
- \`POST /api/v1/zones\` and \`PATCH /api/v1/zones/{id}\` (Admin only).
- \`GET /api/v1/pricing-rules\` and \`POST /api/v1/pricing-rules\` (Admin only).
- \`PATCH /api/v1/pricing-rules/{id}\` (Admin only; supports activating/deactivating rules).

### Implementation Guidance
- Only \`DISPATCHER_ADMIN\` role allowed for write operations.
- Duplicate zone code or duplicate active zone pair returns HTTP 409 Conflict.
- Optimistic locking collision via \`@Version\` returns HTTP 409 Conflict.
- Page responses wrapped in \`PageResponse<T>\`.

### Testing
- MockMvc tests for role authorization (Customer/Courier/Merchant get 403 Forbidden; unauthenticated gets 401 Unauthorized).
- Optimistic locking conflict tests.

### Acceptance Criteria
- [ ] CRUD and toggle endpoints implemented under \`/api/v1/zones\` and \`/api/v1/pricing-rules\`.
- [ ] Only \`DISPATCHER_ADMIN\` permitted for mutating endpoints.
- [ ] Duplicate constraints return HTTP 409.
- [ ] Authorization matrix updated.
`,
  },

  // ==========================================
  // SD-212 · Pricing Admin UI
  // ==========================================
  {
    id: "SD-212",
    title: "SD-212: Implement admin pricing and zone management UI pages",
    module: "Pricing",
    labels: ["pricing", "frontend", "admin"],
    dependsOn: ["SD-211"],
    body: `### Objective
Replace the placeholder page at \`frontend/app/(admin)/admin/pricing/page.tsx\` with interactive interfaces for managing delivery zones and pricing rules.

### Scope
- Zone list and creation modal / form.
- Pricing rules table with zone selector and fee rate editors.
- Error banner parsing RFC 7807 problem details from backend.
- Deactivation toggle for deprecated rules and zones.

### Implementation Guidance
- Reuse \`frontend/lib/api.ts\` client and \`@/components/portal\` UI primitives.
- Ensure responsive layout down to 360px viewport.

### Acceptance Criteria
- [ ] Admin can view, create, and toggle status of zones and pricing rules.
- [ ] Validation errors displayed clearly next to fields.
- [ ] \`npm run lint\` and \`npm run build\` pass cleanly.
`,
  },

  // ==========================================
  // SD-205 · Tracking Number Generator
  // ==========================================
  {
    id: "SD-205",
    title: "SD-205: Implement secure tracking-number generator with collision retry",
    module: "Delivery",
    labels: ["delivery", "backend"],
    body: `### Objective
Implement a secure, standardized tracking-number generator for the delivery module to assign unguessable human-readable tracking identifiers to parcels.

### Scope
- Implement tracking number generator service in \`com.swiftdrop.delivery.domain\`.
- Format: \`SD-YYYYMMDD-XXXXXXXX\` (alphanumeric uppercase, unambiguous character set excluding confusing characters like 0/O, 1/I).
- Cryptographically secure randomness via \`SecureRandom\`.
- Bounded collision retry logic (at least 3 attempts).
- Normalization helper for case-insensitive lookup (trim, uppercase).

### Testing
- Unit tests verifying format regex, character set uniqueness, entropy, and collision retry behavior with simulated collisions.

### Acceptance Criteria
- [ ] Generates valid format tracking numbers matching pattern.
- [ ] Character set eliminates visually ambiguous characters.
- [ ] Collision retry loop handles transient collisions gracefully.
`,
  },

  // ==========================================
  // SD-204 · Parcel Aggregate and Schema
  // ==========================================
  {
    id: "SD-204",
    title: "SD-204: Implement Parcel aggregate, status history schema, and JPA repositories",
    module: "Delivery",
    labels: ["delivery", "database", "backend"],
    dependsOn: ["SD-205"],
    blockedBy: "Formal team acceptance of ADR 0006",
    body: `### Objective
Create the database persistence schema and core \`Parcel\` aggregate root for the parcel delivery lifecycle.

### Scope
- Flyway migration for \`parcels\` and \`parcel_status_history\` tables under \`backend/src/main/resources/db/migration/delivery/\`.
- \`Parcel\` JPA entity with embedded addresses (pickup and delivery), status, size, weight, fee, snapshot currency, customer/merchant ownership, courier assignment, version, and timestamps.
- Ensure customer XOR merchant ownership invariant: customer parcels have \`customer_id\` non-null and \`merchant_id\` null; merchant parcels have \`merchant_id\` non-null.
- \`ParcelStatusHistory\` JPA entity recording status, sequence number, changed_at, changed_by, and optional failure reason.
- Spring Data JPA repositories in \`com.swiftdrop.delivery.domain\`.

### Implementation Guidance
- Follow docs/implementation-contract.md §5.3.
- Reference existing public enums in \`com.swiftdrop.delivery.api\` (\`ParcelStatus\`, \`ParcelSource\`, \`FailureReason\`).
- Foreign keys: \`customer_id REFERENCES users(id)\`, \`assigned_courier_id REFERENCES users(id)\`, zone references to \`zones(id)\`. No direct foreign key or JPA association to \`merchants\` (referenced by UUID only per ADR 0003).
- Exclude PII (addresses, phone numbers, recipient names) from entity \`toString()\` methods.

### Testing
- Unit tests for aggregate invariants and ownership constraints.
- Database migration test via \`DatabaseMigrationIT\`.

### Acceptance Criteria
- [ ] Flyway migration executes successfully against PostgreSQL 18.
- [ ] Entity mappings compatible with Hibernate validation.
- [ ] Ownership constraint (customer XOR merchant) enforced.
- [ ] Sequence increment and optimistic locking \`@Version\` configured.
- [ ] Passes \`MigrationConventionTest\` and \`DatabaseMigrationIT\`.

### Dependencies
- Blocked until ADR 0006 is formally accepted by the team.
`,
  },

  // ==========================================
  // SD-301 · Delivery State Machine
  // ==========================================
  {
    id: "SD-301",
    title: "SD-301: Implement delivery status state machine and history transitions",
    module: "Delivery",
    labels: ["delivery", "backend"],
    dependsOn: ["SD-204"],
    blockedBy: "Formal team acceptance of ADR 0006",
    body: `### Objective
Implement strict parcel status lifecycle transitions, validation rules, history audit logging, and domain event triggering per ADR 0006.

### Scope
- Implement delivery domain state machine enforcing permitted transitions:
  - CREATED -> PICKED_UP (requires assigned courier)
  - PICKED_UP -> IN_TRANSIT
  - PICKED_UP -> FAILED (requires FailureReason)
  - IN_TRANSIT -> OUT_FOR_DELIVERY
  - IN_TRANSIT -> FAILED (requires FailureReason)
  - OUT_FOR_DELIVERY -> DELIVERED (requires valid delivery proof)
  - OUT_FOR_DELIVERY -> FAILED (requires FailureReason)
  - Terminal states: DELIVERED and FAILED cannot transition further.
- Increment monotonic sequence number per parcel transition.
- Record transition entry in \`parcel_status_history\` under aggregate transaction lock.
- Publish in-process \`ParcelStatusChangedEvent\` for transactional listener processing.

### Implementation Guidance
- Contract: ADR 0006 §1 Status Transition Matrix.
- Return 409 Conflict if transition is illegal or preconditions are not satisfied.

### Testing
- Exhaustive unit test matrix covering all permitted and forbidden status transitions.
- Concurrency test ensuring sequence uniqueness.

### Acceptance Criteria
- [ ] All transitions in ADR 0006 strictly enforced; invalid transitions throw domain exception.
- [ ] Mandatory \`FailureReason\` validated on transitions to \`FAILED\`.
- [ ] Monotonic sequence numbers increment per parcel without gaps.
- [ ] Status changes append audit row to history within the same transaction.

### Dependencies
- Blocked until ADR 0006 is formally accepted by the team.
`,
  },

  // ==========================================
  // SD-302 · Delivery Module API Facade
  // ==========================================
  {
    id: "SD-302",
    title: "SD-302: Implement delivery.api query and assignment contracts",
    module: "Delivery",
    labels: ["delivery", "api", "backend"],
    dependsOn: ["SD-301"],
    body: `### Objective
Implement the public service contracts in \`com.swiftdrop.delivery.api\` enabling other modules (dispatch, merchant, tracking) to query parcel summaries and assign couriers without accessing delivery internals.

### Scope
- Implement query methods: \`findSummaryById\`, \`findSummaryByTrackingNumber\`, \`findUnassignedParcels\`, \`findActiveParcelsByCourier\`, \`findMerchantParcels\`.
- Implement \`assignCourier(UUID parcelId, UUID courierId)\` contract:
  - Courier assignment permitted only while parcel is in \`CREATED\` status.
  - Returns 409 Conflict if parcel has progressed beyond \`CREATED\`.
- Return immutable \`ParcelSummary\` records (no internal entities or PII leakage).

### Implementation Guidance
- Contract: docs/implementation-contract.md §8.1.
- Maintain strict module boundaries per ADR 0003.

### Acceptance Criteria
- [ ] Public API methods implemented in \`delivery\` module.
- [ ] Assignment strictly restricted to \`CREATED\` status.
- [ ] Summary records contain no unneeded PII.
- [ ] Module boundary tests pass.
`,
  },

  // ==========================================
  // SD-303 · Transactional Outbox Events
  // ==========================================
  {
    id: "SD-303",
    title: "SD-303: Implement transactional outbox recording for parcel status events",
    module: "Delivery",
    labels: ["delivery", "database", "messaging", "backend"],
    dependsOn: ["SD-301"],
    body: `### Objective
Persist domain status-change events into a \`parcel_outbox_events\` table atomically within the parcel transition transaction to guarantee reliable at-least-once publishing (ADR 0004).

### Scope
- Flyway migration for \`parcel_outbox_events\` table (id UUID, parcel_id UUID, event_type VARCHAR, aggregate_sequence BIGINT, payload JSONB/TEXT, status VARCHAR, retry_count INT, created_at TIMESTAMPTZ, processed_at TIMESTAMPTZ).
- Event serialization producing standardized \`parcel.status-changed\` event payload (ADR 0004 §2).
- Save outbox record in the same JPA transaction as \`Parcel\` and \`ParcelStatusHistory\`.

### Implementation Guidance
- Ensure payload contains no personal data (recipient address, phone numbers excluded).
- Include: \`eventId\`, \`eventType\`, \`aggregateId\`, \`aggregateSequence\`, \`status\`, \`previousStatus\`, \`timestamp\`, \`source\`, \`trackingNumber\`.

### Testing
- Transaction rollback test: verify that a rollback of parcel update leaves no orphan outbox records.
- JSON schema validation test on outbox payload.

### Acceptance Criteria
- [ ] Outbox table created via Flyway.
- [ ] Outbox record committed atomically with status history.
- [ ] Event payload adheres to ADR 0004 §2 format with zero PII.
`,
  },

  // ==========================================
  // SD-304 · Public Tracking Lookup API
  // ==========================================
  {
    id: "SD-304",
    title: "SD-304: Implement public parcel tracking lookup REST endpoint",
    module: "Delivery",
    labels: ["delivery", "api", "tracking", "backend"],
    dependsOn: ["SD-205", "SD-301"],
    body: `### Objective
Provide a public REST endpoint enabling recipients and senders to track parcel progress by tracking number without authentication and without leaking personal contact information.

### Scope
- \`GET /api/v1/tracked-parcels/{trackingNumber}\` (Public endpoint).
- Response DTO: trackingNumber, status, current status timestamp, origin zone name, destination zone name, timeline of status transitions (timestamp, status, failure reason if applicable).
- Explicitly exclude recipient names, phone numbers, delivery street addresses, and internal pricing figures.
- Return 404 Not Found for non-existent tracking numbers without timing side-channels.

### Implementation Guidance
- Input normalization (uppercase, trimmed tracking number).
- Add endpoint to \`SecurityConfiguration\` permitting unauthenticated access.
- Add test row in \`IdentityAuthorizationMatrixTest\`.

### Acceptance Criteria
- [ ] Public endpoint returns parcel status and timeline.
- [ ] Sensitive customer PII is omitted from public response.
- [ ] 404 returned for invalid tracking number.
- [ ] Authorization matrix tests pass.
`,
  },

  // ==========================================
  // SD-309 · Courier Deliveries For Day
  // ==========================================
  {
    id: "SD-309",
    title: "SD-309: Implement courier daily deliveries list endpoint",
    module: "Delivery",
    labels: ["delivery", "courier", "api", "backend"],
    dependsOn: ["SD-302"],
    blockedBy: "Formal team acceptance of ADR 0006",
    body: `### Objective
Provide couriers with a dedicated REST endpoint to fetch active parcels assigned to them for the current business day.

### Scope
- \`GET /api/v1/courier/parcels\` (Authenticated: \`COURIER\` role only).
- Filter parcels assigned to the authenticated courier principal.
- Exclude terminal parcels completed prior to the current business day.
- Return full pickup and delivery address details and contact numbers necessary for delivery execution (entitled courier view).

### Implementation Guidance
- Enforce business day boundary using configured \`SWIFTDROP_BUSINESS_TIME_ZONE\` (Asia/Colombo).
- Caller identity extracted from JWT authentication principal.
- Active courier parcel query semantics and terminal status exclusions depend on proposed ADR 0006 §1-§3. Do not finalize or merge until ADR 0006 is formally accepted.

### Acceptance Criteria
- [ ] Only callers with \`COURIER\` role can access endpoint (others receive 403 Forbidden).
- [ ] Returns only parcels assigned to the calling courier.
- [ ] Contact information included for legitimate delivery execution.

### Dependencies
- Depends on SD-302 (\`delivery.api\` query contracts).
- Blocked until ADR 0006 (delivery lifecycle and transition rules) is formally accepted by the team.
`,
  },

  // ==========================================
  // SD-310 · Courier Status Update Endpoint
  // ==========================================
  {
    id: "SD-310",
    title: "SD-310: Implement courier status transition endpoint with delivery notes",
    module: "Delivery",
    labels: ["delivery", "courier", "api", "backend"],
    dependsOn: ["SD-301"],
    blockedBy: "Formal team acceptance of ADR 0006",
    body: `### Objective
Enable assigned couriers to advance parcel statuses in the field and record optional delivery notes.

### Scope
- \`POST /api/v1/parcels/{id}/status-changes\` (Authenticated: \`COURIER\` role).
- Request body: \`targetStatus\` (ParcelStatus), \`failureReasonCode\` (optional FailureReason), \`note\` (optional String, max 500 chars).
- Strict caller ownership check: If caller is not the currently assigned courier, return HTTP 404 Not Found (prevents parcel assignment probing).
- Illegal transitions return HTTP 409 Conflict.

### Implementation Guidance
- Contract: ADR 0006 §7 Error mapping (404 for unassigned couriers, 409 for invalid transitions).
- Record courier ID and note in status history record.
- The status transition rules, allowed caller checks, and error mappings depend directly on proposed ADR 0006. Do not finalize or merge until ADR 0006 is formally accepted.

### Acceptance Criteria
- [ ] Assigned courier can transition valid lifecycle stages.
- [ ] Non-assigned courier receives HTTP 404 Not Found.
- [ ] Note length capped at 500 characters.
- [ ] Invalid transition returns HTTP 409 Conflict.

### Dependencies
- Depends on SD-301 (status state machine).
- Blocked until ADR 0006 (delivery lifecycle and transition rules) is formally accepted by the team.
`,
  },

  // ==========================================
  // SD-311 · Proof of Delivery
  // ==========================================
  {
    id: "SD-311",
    title: "SD-311: Implement proof of delivery capture and photo attachment",
    module: "Delivery",
    labels: ["delivery", "courier", "backend"],
    dependsOn: ["SD-310"],
    blockedBy: "Formal team acceptance of ADR 0006",
    body: `### Objective
Enable couriers to capture mandatory delivery proof (recipient name, signature confirmation, optional photo upload) prior to marking a parcel as DELIVERED.

### Scope
- Flyway migration for \`delivery_proofs\` table (id, parcel_id, recipient_name, photo_url/photo_path, created_at, created_by).
- \`POST /api/v1/parcels/{id}/proof\` multipart endpoint accepting recipient name and image file (JPEG/PNG only, max 5MB).
- Content-type sniffing and file size validation.
- Delivery transition to \`DELIVERED\` strictly validates that an approved proof record exists for the parcel.

### Implementation Guidance
- Contract: ADR 0006 §4 Delivery Proof Rules.
- Store uploaded images in local storage directory (configurable via properties).
- Photos accessible only to sender, assigned courier, and Dispatcher/Admin.
- Proof preconditions and schema rules depend directly on proposed ADR 0006 §4. Do not finalize or merge until ADR 0006 is formally accepted.

### Acceptance Criteria
- [ ] Delivery proof record created with valid recipient name.
- [ ] Transitioning to \`DELIVERED\` without proof returns HTTP 409 Conflict.
- [ ] File content-type validation prevents non-image uploads.

### Dependencies
- Depends on SD-310 (courier status transitions).
- Blocked until ADR 0006 (delivery proof and lifecycle rules) is formally accepted by the team.
`,
  },

  // ==========================================
  // SD-312 · Failed Delivery Reasons
  // ==========================================
  {
    id: "SD-312",
    title: "SD-312: Implement standardized failure reason recording for undeliverable parcels",
    module: "Delivery",
    labels: ["delivery", "courier", "backend"],
    dependsOn: ["SD-310"],
    blockedBy: "Formal team acceptance of ADR 0006",
    body: `### Objective
Enforce standardized delivery failure codes when marking a parcel as FAILED to provide structured operational tracking.

### Scope
- Validate that transitions to \`FAILED\` supply a mandatory \`FailureReason\` code:
  - \`RECIPIENT_UNAVAILABLE\`
  - \`ADDRESS_NOT_FOUND\`
  - \`REFUSED\`
  - \`DAMAGED\`
  - \`OTHER\`
- Record code in \`parcel_status_history\` and domain event.
- Missing or invalid failure reason on failure transition returns HTTP 400 Bad Request.

### Implementation Guidance
- Contract: ADR 0006 §5 Failure reason codes and terminal failure states.
- Note: Although \`FailureReason.java\` enum exists in \`delivery.api\`, the mandatory status transition rules and database check constraints depend on proposed ADR 0006. Do not finalize or merge until ADR 0006 is formally accepted.

### Acceptance Criteria
- [ ] \`FailureReason\` enum persisted in database with check constraint.
- [ ] Transitioning to \`FAILED\` without a reason code returns HTTP 400 Bad Request.
- [ ] Reason code exposed in parcel detail and status history.

### Dependencies
- Depends on SD-310 (courier status transitions).
- Blocked until ADR 0006 (delivery lifecycle and failure handling) is formally accepted by the team.
`,
  },

  // ==========================================
  // SD-313 · Courier Mobile UI
  // ==========================================
  {
    id: "SD-313",
    title: "SD-313: Implement courier mobile portal UI for deliveries, proof, and status updates",
    module: "Delivery",
    labels: ["delivery", "courier", "frontend"],
    dependsOn: ["SD-309", "SD-310", "SD-311", "SD-312"],
    body: `### Objective
Replace placeholder pages in \`frontend/app/(courier)/courier/\` with mobile-optimized views for active parcel routes, status advancement, camera proof capture, and failure recording.

### Scope
- Daily assigned parcel list with progress indicators.
- Parcel action sheet: advance status buttons (Pick Up, Start Transit, Out for Delivery, Complete Delivery).
- Delivery proof capture modal: recipient name text field + mobile camera file input.
- Failure report modal with standardized reason dropdown.

### Implementation Guidance
- Mobile-first layout usable on 360px viewport width.
- Integrate with \`frontend/lib/api.ts\`.

### Acceptance Criteria
- [ ] Courier can view assigned parcels and advance statuses.
- [ ] Proof capture uploads photo and completes delivery.
- [ ] Failure modal records failure reasons.
- [ ] \`npm run lint\` and \`npm run build\` pass.
`,
  },

  // ==========================================
  // SD-314 · Delivery Reports
  // ==========================================
  {
    id: "SD-314",
    title: "SD-314: Implement delivery performance and failure rate reporting endpoints",
    module: "Delivery",
    labels: ["delivery", "admin", "reports", "backend"],
    dependsOn: ["SD-301"],
    blockedBy: "Formal team acceptance of ADR 0006",
    body: `### Objective
Provide administrative reporting endpoints in \`com.swiftdrop.delivery.application.report\` calculating deliveries per day and delivery success/failure rates.

### Scope
- \`GET /api/v1/reports/deliveries-per-day?from=YYYY-MM-DD&to=YYYY-MM-DD\` (Admin only, max 92 day window).
- \`GET /api/v1/reports/delivery-performance?from=YYYY-MM-DD&to=YYYY-MM-DD\` (Admin only).
- Calculate total deliveries, completed deliveries, failed deliveries, and failure rate percentages.
- Handle zero-delivery days in range.
- Business day boundaries calculated using configured \`SWIFTDROP_BUSINESS_TIME_ZONE\`.

### Implementation Guidance
- Definitions of delivery completion and failure outcomes depend directly on terminal statuses in proposed ADR 0006 §1-§2 (and D-08).
- Do not finalize or merge until ADR 0006 is formally accepted.

### Acceptance Criteria
- [ ] Endpoints restricted to \`DISPATCHER_ADMIN\` role.
- [ ] Date window validated (max 92 days; from <= to).
- [ ] Aggregations verified via SQL tests with fixed clock.

### Dependencies
- Depends on SD-301 (status state machine).
- Blocked until ADR 0006 (delivery lifecycle and terminal states) is formally accepted by the team.
`,
  },

  // ==========================================
  // SD-315 · Reports UI
  // ==========================================
  {
    id: "SD-315",
    title: "SD-315: Implement administrative reports dashboard UI",
    module: "Delivery",
    labels: ["delivery", "admin", "reports", "frontend"],
    dependsOn: ["SD-314"],
    body: `### Objective
Replace placeholder page at \`frontend/app/(admin)/admin/reports/page.tsx\` with interactive date-range filtering, delivery count cards, and performance trend tables.

### Scope
- Date range picker component with default 30-day preset.
- Metric summary cards (Total Bookings, Delivered Rate %, Failed Rate %).
- Delivery performance table with daily breakdown.

### Acceptance Criteria
- [ ] Displays report metrics fetched from backend reporting endpoints.
- [ ] Date filter triggers refreshed query.
- [ ] \`npm run lint\` and \`npm run build\` pass.
`,
  },

  // ==========================================
  // SD-316 · Parcel Ownership Authorization Suite
  // ==========================================
  {
    id: "SD-316",
    title: "SD-316: Implement parcel ownership and role authorization test suite (TST-2)",
    module: "Quality & Testing",
    labels: ["quality", "test", "security", "delivery", "backend"],
    dependsOn: ["SD-207", "SD-310", "SD-311"],
    blockedBy: "Formal team acceptance of ADR 0006",
    body: `### Objective
Implement the project-level TST-2 authorization and ownership verification suite ensuring strict access control and privacy isolation across all parcel endpoints.

### Scope
- Add comprehensive matrix test cases using the existing \`AuthorizationMatrixExecutor\` framework.
- Cover all parcel endpoints: \`GET /api/v1/parcels\`, \`GET /api/v1/parcels/{id}\`, \`GET /api/v1/parcels/{id}/receipt\`, \`POST /api/v1/parcels/{id}/status-changes\`, \`POST /api/v1/parcels/{id}/proof\`.
- Assert expected status codes for distinct caller principals:
  - Calling customer (owner): allowed.
  - Non-owner customer: returns HTTP 404 Not Found (prevents parcel ID enumeration).
  - Assigned courier: allowed for assigned operational endpoints.
  - Non-assigned courier: returns HTTP 404 Not Found (prevents probing assignment state).
  - Dispatcher/Admin: permitted for management endpoints.
  - Unauthenticated caller: returns HTTP 401 Unauthorized.
- Out of scope: Implementing business logic (tests assert behavior of existing endpoints).

### Implementation Guidance
- Follow ADR 0003, ADR 0006 §7 (404 for unassigned/unowned parcel access), and the project brief (TST-2).
- Use \`AuthorizationMatrixRow\` and \`TestPrincipal\` helpers in \`backend/src/test/java/com/swiftdrop/test/security/\`.

### Acceptance Criteria
- [ ] Matrix tests assert 404 Not Found on all non-owner customer requests.
- [ ] Matrix tests assert 404 Not Found when courier B attempts to update or upload proof for courier A's parcel.
- [ ] Admin permissions verified across management queries.
- [ ] Anonymous requests consistently return 401 Unauthorized.

### Dependencies
- Depends on SD-207 (customer detail/history), SD-310 (courier status changes), SD-311 (proof capture).
- Blocked until ADR 0006 (error mapping rules and status ownership) is formally accepted by the team.
`,
  },

  // ==========================================
  // SD-206 · Customer Delivery Request
  // ==========================================
  {
    id: "SD-206",
    title: "SD-206: Implement customer parcel booking REST endpoint",
    module: "Customer",
    labels: ["customer", "delivery", "pricing", "backend"],
    dependsOn: ["SD-202", "SD-204", "SD-301"],
    body: `### Objective
Allow authenticated customers to submit delivery requests with server-recalculated pricing and initial status history creation.

### Scope
- \`POST /api/v1/parcels\` (Authenticated: \`CUSTOMER\` role only).
- Request body: sender address & contact, recipient address & contact, originZoneId, destinationZoneId, size, weightGrams, specialInstructions.
- Server-side fee calculation via \`FeeCalculator\` (client-submitted fees ignored).
- Generates secure tracking number via \`SD-205\`.
- Atomically creates \`Parcel\` in \`CREATED\` status, initial \`ParcelStatusHistory\` entry (sequence 1), and outbox event.
- Returns HTTP 201 Created with \`Location: /api/v1/parcels/{id}\`.

### Implementation Guidance
- Contract: docs/implementation-contract.md §4.3 and §8.1 \`ParcelCreation\`.
- Caller user ID becomes parcel \`customerId\` (\`source = CUSTOMER\`).

### Testing
- MockMvc test asserting client-supplied fee values are ignored in favor of \`FeeCalculator\`.
- Role verification (Courier and Merchant cannot call customer booking endpoint).

### Acceptance Criteria
- [ ] Customer can book parcel; returns 201 Created with tracking number.
- [ ] Fee is verified and calculated server-side.
- [ ] Initial history and outbox row written in single transaction.
`,
  },

  // ==========================================
  // SD-207 · Customer History & Detail
  // ==========================================
  {
    id: "SD-207",
    title: "SD-207: Implement customer delivery history and parcel detail endpoints",
    module: "Customer",
    labels: ["customer", "delivery", "api", "backend"],
    dependsOn: ["SD-206"],
    body: `### Objective
Enable customers to list their past delivery bookings and inspect individual parcel timelines with strict data isolation.

### Scope
- \`GET /api/v1/parcels\` (Paged via \`PageResponse<T>\`, newest first).
  - Customers see only their own parcels.
  - Dispatcher/Admin sees all parcels with optional status filter.
- \`GET /api/v1/parcels/{id}\`:
  - Returns complete details and status history timeline.
  - Returns HTTP 404 Not Found if requested by another customer.

### Acceptance Criteria
- [ ] Customers receive only their own parcels.
- [ ] Non-owner access attempts return HTTP 404 Not Found.
- [ ] Response includes full status transition history.
`,
  },

  // ==========================================
  // SD-208 · Delivery Receipt
  // ==========================================
  {
    id: "SD-208",
    title: "SD-208: Implement delivery receipt endpoint and printable receipt format",
    module: "Customer",
    labels: ["customer", "delivery", "backend"],
    dependsOn: ["SD-207"],
    blockedBy: "Formal team acceptance of ADR 0006",
    body: `### Objective
Provide a delivery receipt endpoint containing immutable pricing snapshot, route information, timestamps, and payment breakdown.

### Scope
- \`GET /api/v1/parcels/{id}/receipt\` (Owner customer or Admin only).
- Available immediately upon booking and throughout delivery lifecycle (per ADR 0006 §6).
- Returns trackingNumber, booking date, sender/recipient summary, size, weight, fee breakdown, and current status.
- Returns 404 Not Found for non-owner customer.

### Implementation Guidance
- Receipt contents and lifecycle availability depend on proposed ADR 0006 §6.
- Do not finalize or merge implementation until ADR 0006 is formally accepted by the team.

### Acceptance Criteria
- [ ] Returns detailed receipt payload immediately after parcel creation.
- [ ] Unrelated customers receive HTTP 404 Not Found.

### Dependencies
- Depends on SD-207 (customer delivery history and detail).
- Blocked until ADR 0006 (receipt availability across lifecycle) is formally accepted by the team.
`,
  },

  // ==========================================
  // SD-209 · Frontend Booking Flow
  // ==========================================
  {
    id: "SD-209",
    title: "SD-209: Implement customer booking flow with live fee quote estimator UI",
    module: "Customer",
    labels: ["customer", "pricing", "frontend"],
    dependsOn: ["SD-203", "SD-206"],
    body: `### Objective
Replace placeholder page at \`frontend/app/(customer)/customer/new/page.tsx\` with an interactive booking flow featuring live price estimation.

### Scope
- Address and contact form for pickup and recipient locations with validation.
- Zone selector populated from active zones.
- Real-time "Get Price Quote" button calling \`POST /api/v1/price-quotes\`.
- Booking confirmation screen showing generated tracking number and link to tracking portal.

### Acceptance Criteria
- [ ] User can calculate estimated quote prior to confirmation.
- [ ] Booking submission creates parcel and displays tracking number.
- [ ] \`npm run lint\` and \`npm run build\` pass.
`,
  },

  // ==========================================
  // SD-210 · Frontend History & Receipt
  // ==========================================
  {
    id: "SD-210",
    title: "SD-210: Implement customer delivery history list and printable receipt pages UI",
    module: "Customer",
    labels: ["customer", "frontend"],
    dependsOn: ["SD-207", "SD-208"],
    body: `### Objective
Replace placeholder at \`frontend/app/(customer)/customer/history/page.tsx\` and implement parcel detail view with print-ready receipt styling.

### Scope
- Paged delivery history table with status badges and date filters.
- Parcel detail drawer/page showing chronological status timeline.
- Print-ready receipt modal / page with print media CSS stylesheet.

### Acceptance Criteria
- [ ] Customer can view their parcel history.
- [ ] Receipt view formats cleanly for browser print/PDF export.
- [ ] \`npm run lint\` and \`npm run build\` pass.
`,
  },

  // ==========================================
  // SD-305 · Courier Profiles (Dispatch)
  // ==========================================
  {
    id: "SD-305",
    title: "SD-305: Implement courier profile management and eligibility schema in dispatch",
    module: "Dispatch",
    labels: ["dispatch", "database", "backend"],
    body: `### Objective
Establish the \`dispatch\` module's domain persistence and administrative APIs for managing courier profiles and vehicle types.

### Scope
- Flyway migration for \`couriers\` table under \`backend/src/main/resources/db/migration/dispatch/\`.
- Link profile to \`users(id)\` where user role is \`COURIER\` (validated via \`identity.api.UserDirectory\`).
- Fields: id UUID, user_id UUID NOT NULL UNIQUE, vehicle_type VARCHAR(50), active BOOLEAN DEFAULT true, created_at TIMESTAMPTZ.
- Admin REST endpoints: \`GET /api/v1/courier-profiles\`, \`POST /api/v1/courier-profiles\`, \`PATCH /api/v1/courier-profiles/{id}\`.

### Implementation Guidance
- Adhere to ADR 0003: dispatch queries \`UserDirectory\` from \`identity.api\` to verify courier existence and role.

### Acceptance Criteria
- [ ] Flyway migration executes cleanly.
- [ ] Creating profile for non-existent or non-courier user returns HTTP 400/409.
- [ ] Only \`DISPATCHER_ADMIN\` can manage courier profiles.
- [ ] Passes \`MigrationConventionTest\` and \`DatabaseMigrationIT\`.
`,
  },

  // ==========================================
  // SD-306 · Parcel Assignment (Dispatch)
  // ==========================================
  {
    id: "SD-306",
    title: "SD-306: Implement unassigned parcel listing and courier assignment workflow",
    module: "Dispatch",
    labels: ["dispatch", "delivery", "api", "backend"],
    dependsOn: ["SD-302", "SD-305"],
    body: `### Objective
Provide dispatchers with tools to review unassigned delivery requests and assign qualified couriers.

### Scope
- \`GET /api/v1/unassigned-parcels\` (Admin only, paged).
- \`POST /api/v1/parcel-assignments\` (Admin only):
  - Request body: \`parcelId\` (UUID), \`courierId\` (UUID).
  - Validates courier profile is active in \`dispatch\`.
  - Invokes \`delivery.api.ParcelAssignment.assignCourier(parcelId, courierId)\`.
  - Returns HTTP 409 Conflict if parcel has moved beyond \`CREATED\` status.

### Consistency Rules
- Dispatch module must not query delivery tables or entities directly (ADR 0003).

### Acceptance Criteria
- [ ] Dispatcher can list unassigned parcels.
- [ ] Assignment verifies courier eligibility before calling delivery facade.
- [ ] Assignment on non-CREATED parcel rejected with HTTP 409.
`,
  },

  // ==========================================
  // SD-307 · Courier Workload View
  // ==========================================
  {
    id: "SD-307",
    title: "SD-307: Implement active courier workload query endpoint",
    module: "Dispatch",
    labels: ["dispatch", "delivery", "api", "backend"],
    dependsOn: ["SD-302", "SD-305"],
    body: `### Objective
Provide dispatchers with real-time active parcel counts per courier to balance assignment workloads across the fleet.

### Scope
- \`GET /api/v1/courier-workload\` (Authenticated: \`DISPATCHER_ADMIN\` only).
- Returns list of active couriers with their current count of non-terminal assigned parcels (including zero).
- Queries \`delivery.api.ParcelQueries.findActiveCountsByCourier()\`.

### Acceptance Criteria
- [ ] Returns active couriers with accurate counts of in-progress deliveries.
- [ ] Access restricted to dispatchers and admins.
`,
  },

  // ==========================================
  // SD-308 · Dispatcher UI
  // ==========================================
  {
    id: "SD-308",
    title: "SD-308: Implement dispatcher console UI for unassigned parcels and courier assignment",
    module: "Dispatch",
    labels: ["dispatch", "frontend", "admin"],
    dependsOn: ["SD-306", "SD-307"],
    body: `### Objective
Replace placeholder page at \`frontend/app/(dispatch)/dispatch/page.tsx\` with a functional dispatch management console.

### Scope
- Unassigned parcels table with origin/destination zones and age.
- Courier assignment modal showing candidate couriers and their real-time workload counts.
- Quick assignment button with success confirmation.
- Courier fleet roster overview.

### Acceptance Criteria
- [ ] Dispatcher can view unassigned parcels and assign couriers.
- [ ] Workload count displayed alongside courier names during selection.
- [ ] \`npm run lint\` and \`npm run build\` pass.
`,
  },

  // ==========================================
  // SD-401 · Merchant Registration
  // ==========================================
  {
    id: "SD-401",
    title: "SD-401: Implement merchant registration and pending merchant aggregate",
    module: "Merchant",
    labels: ["merchant", "database", "backend"],
    body: `### Objective
Allow prospective merchant businesses to register on SwiftDrop and create pending merchant profiles awaiting administrator review.

### Scope
- Flyway migration for \`merchants\` table (id UUID, user_id UUID UNIQUE, business_name VARCHAR(200), contact_phone VARCHAR(20), status VARCHAR(30) DEFAULT 'PENDING', reviewer_id UUID, review_notes TEXT, reviewed_at TIMESTAMPTZ, created_at TIMESTAMPTZ).
- \`POST /api/v1/merchants\` (Public registration):
  - Atomically creates user with \`MERCHANT\` role via \`UserDirectory\` and pending \`Merchant\` profile.
- \`GET /api/v1/merchants/me\` (Authenticated merchant: returns own business profile and review status).

### Acceptance Criteria
- [ ] Migration creates \`merchants\` table with constraints.
- [ ] Public registration creates user and pending merchant record.
- [ ] Duplicate email or business conflict returns HTTP 409.
- [ ] Passes \`MigrationConventionTest\` and \`DatabaseMigrationIT\`.
`,
  },

  // ==========================================
  // SD-402 · Merchant Approval / Rejection
  // ==========================================
  {
    id: "SD-402",
    title: "SD-402: Implement admin merchant review, approval, rejection, and suspension",
    module: "Merchant",
    labels: ["merchant", "admin", "api", "backend"],
    dependsOn: ["SD-401"],
    body: `### Objective
Provide administrative endpoints for dispatchers and admins to review, approve, reject, or suspend merchant accounts.

### Scope
- \`GET /api/v1/admin/merchants\` (Admin only, filter by status: PENDING, APPROVED, REJECTED, SUSPENDED).
- \`POST /api/v1/admin/merchants/{id}/approval\` (Admin only).
- \`POST /api/v1/admin/merchants/{id}/rejection\` (Admin only, requires rejection reason).
- \`POST /api/v1/admin/merchants/{id}/suspension\` (Admin only).
- Non-approved merchants receive HTTP 403 Forbidden on merchant operational APIs.

### Acceptance Criteria
- [ ] Admin can approve or reject pending merchants.
- [ ] Status updates record reviewing admin ID and timestamp.
- [ ] Non-approved merchants barred from order and webhook APIs.
`,
  },

  // ==========================================
  // SD-403 · Merchant API Keys
  // ==========================================
  {
    id: "SD-403",
    title: "SD-403: Implement merchant API key generation, hashing, and revocation",
    module: "Merchant",
    labels: ["merchant", "security", "backend"],
    dependsOn: ["SD-402"],
    body: `### Objective
Enable approved merchants to generate and manage high-entropy API keys for programmatic delivery order intake.

### Scope
- Flyway migration for \`merchant_api_keys\` table (id, merchant_id, key_prefix, key_hash, label, created_at, expires_at, revoked_at, last_used_at).
- Cryptographic generation using \`SecureRandom\` (raw key shown exactly once to merchant upon generation).
- Secure SHA-256 hashing at rest.
- \`GET /api/v1/merchant/api-keys\`, \`POST /api/v1/merchant/api-keys\`, \`DELETE /api/v1/merchant/api-keys/{id}\` (Approved merchant only).

### Security Rules
- Raw secret key is never stored or logged.
- Only prefix and label returned on listing endpoints.

### Acceptance Criteria
- [ ] API keys generated with 256 bits of entropy.
- [ ] Raw key returned only once upon creation.
- [ ] Keys stored hashed at rest.
`,
  },

  // ==========================================
  // SD-404 · Merchant API Key Filter
  // ==========================================
  {
    id: "SD-404",
    title: "SD-404: Implement X-Api-Key authentication filter for merchant endpoints",
    module: "Merchant",
    labels: ["merchant", "security", "backend"],
    dependsOn: ["SD-403"],
    body: `### Objective
Integrate an \`X-Api-Key\` authentication filter into the Spring Security chain to authenticate programmatic merchant API requests.

### Scope
- Create \`MerchantApiKeyAuthenticationFilter\` protecting \`/api/v1/merchant-orders/**\`.
- Constant-time hash verification against \`merchant_api_keys\`.
- Populate security context with authenticated merchant identity.
- Reject invalid, revoked, or expired keys with uniform HTTP 401 Unauthorized.
- Ensure JWT tokens are rejected on API key routes and API keys are rejected on standard JWT routes.

### Acceptance Criteria
- [ ] Valid API key authenticates request and resolves merchant ID.
- [ ] Invalid or revoked keys return HTTP 401 Unauthorized.
- [ ] Module boundaries in \`SecurityConfiguration\` strictly preserved.
`,
  },

  // ==========================================
  // SD-405 · Merchant Order Intake
  // ==========================================
  {
    id: "SD-405",
    title: "SD-405: Implement idempotent merchant delivery order intake REST API",
    module: "Merchant",
    labels: ["merchant", "delivery", "api", "backend"],
    dependsOn: ["SD-404", "SD-206"],
    body: `### Objective
Provide an idempotent REST API enabling merchants to submit delivery orders programmatically using their API keys.

### Scope
- \`POST /api/v1/merchant-orders\` (Authenticated via \`X-Api-Key\`).
- Request body: \`merchantOrderReference\` (unique per merchant), sender/pickup details, recipient details, size, weightGrams.
- Idempotency per D-25: duplicate submissions with the same \`merchantOrderReference\` return existing parcel details without creating a second parcel or emitting duplicate outbox events.
- Creates parcel in \`delivery\` module via public API with \`source = MERCHANT\`.
- \`GET /api/v1/merchant-orders/{ref}\`: returns parcel details for own order only.

### Acceptance Criteria
- [ ] Programmatic order intake creates merchant parcel.
- [ ] Re-submitting identical order reference returns HTTP 200 with original parcel without duplication.
- [ ] Merchants isolated from viewing other merchants' orders (HTTP 404).
`,
  },

  // ==========================================
  // SD-406 · Merchant Dashboard API
  // ==========================================
  {
    id: "SD-406",
    title: "SD-406: Implement merchant portal dashboard orders query endpoint",
    module: "Merchant",
    labels: ["merchant", "api", "backend"],
    dependsOn: ["SD-402"],
    body: `### Objective
Provide dashboard query endpoints for merchants logging into the web portal to monitor their delivery orders and fulfillment status.

### Scope
- \`GET /api/v1/merchants/me/orders\` (Authenticated: \`MERCHANT\` role via JWT).
- Paged via \`PageResponse<T>\`, filterable by status and date range.
- Summary counts of orders by status.
- Strictly isolated to caller's merchant account.

### Acceptance Criteria
- [ ] Merchant can fetch their own orders and status aggregates.
- [ ] Isolation prevents data leakage across merchants.
`,
  },

  // ==========================================
  // SD-407 · Merchant Portal UI
  // ==========================================
  {
    id: "SD-407",
    title: "SD-407: Implement merchant registration, dashboard, and API-key UI pages",
    module: "Merchant",
    labels: ["merchant", "frontend"],
    dependsOn: ["SD-401", "SD-403", "SD-406"],
    body: `### Objective
Replace placeholder pages under \`frontend/app/(merchant)/merchant/\` with merchant registration, order overview, and API key management interfaces.

### Scope
- Merchant registration form and pending status state screen.
- Orders dashboard table with status filtering.
- API key generation modal with one-time copyable secret key display and key list.

### Acceptance Criteria
- [ ] Merchant can register, review orders, and generate API keys.
- [ ] One-time secret copy banner implemented.
- [ ] \`npm run lint\` and \`npm run build\` pass.
`,
  },

  // ==========================================
  // SD-408 · Webhook Endpoint Registration
  // ==========================================
  {
    id: "SD-408",
    title: "SD-408: Implement webhook endpoint registration with encrypted secret storage",
    module: "Merchant",
    labels: ["merchant", "webhooks", "security", "backend"],
    dependsOn: ["SD-402"],
    body: `### Objective
Enable merchants to register webhook URLs and generate shared secrets encrypted at rest for payload signature verification.

### Scope
- Flyway migration for \`webhook_endpoints\` table (id, merchant_id, url, secret_encrypted, active, created_at).
- Encrypt webhook secrets at rest using AES-256-GCM with authenticated data (ADR 0004 §7.4).
- \`POST /api/v1/merchant/webhooks\` and \`GET /api/v1/merchant/webhooks\`.
- Raw webhook signing secret shown once upon creation; never retrievable again or logged.

### Acceptance Criteria
- [ ] Webhook endpoint created with AES-256-GCM encrypted secret.
- [ ] Secret display follows show-once security convention.
- [ ] Inactive keys prevent webhook dispatch.
`,
  },

  // ==========================================
  // SD-409 · SSRF Safe HTTP Client
  // ==========================================
  {
    id: "SD-409",
    title: "SD-409: Implement SSRF-safe HTTP client for webhook delivery",
    module: "Merchant",
    labels: ["merchant", "security", "webhooks", "backend"],
    body: `### Objective
Implement an Apache HttpClient 5 client strictly protected against Server-Side Request Forgery (SSRF) for delivering outbound webhooks (ADR 0004 §7.3).

### Scope
- Custom \`DnsResolver\` verifying resolved IP addresses before connection.
- Block loopback (127.0.0.0/8, ::1), link-local (169.254.0.0/16, fe80::/10), private RFC 1918 (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16), and cloud metadata IP (169.254.169.254).
- Disallow redirects; enforce connect and socket timeouts (default 5s).
- Allow localhost override only when explicitly enabled for local development.

### Testing
- Full SSRF verification test suite per ADR 0004 §11 testing loopback, metadata IPs, DNS rebinding, and IP-literal hostnames.

### Acceptance Criteria
- [ ] Webhook requests to private or metadata addresses are blocked.
- [ ] Redirects are rejected.
- [ ] Comprehensive SSRF unit test suite passes.
`,
  },

  // ==========================================
  // SD-410 · Merchant Event Consumer
  // ==========================================
  {
    id: "SD-410",
    title: "SD-410: Implement RabbitMQ merchant event consumer and webhook delivery recording",
    module: "Merchant",
    labels: ["merchant", "messaging", "webhooks", "backend"],
    dependsOn: ["SD-408"],
    body: `### Objective
Consume parcel status change events from RabbitMQ and record pending webhook deliveries for active merchant endpoints.

### Scope
- Declare queue \`merchant.parcel-status-changed\` bound to \`swiftdrop.events\`.
- Filter events: process only merchant-originated parcels (\`source == MERCHANT\`).
- For each active webhook endpoint belonging to the merchant, create a pending \`webhook_deliveries\` record with event sequence deduplication.
- Acknowledge broker message after transaction commit.

### Acceptance Criteria
- [ ] RabbitMQ listener processes parcel status events.
- [ ] Customer parcels ignored; merchant parcels generate webhook delivery records.
- [ ] Deduplication prevents duplicate delivery records for replayed messages.
`,
  },

  // ==========================================
  // SD-411 · Webhook Delivery Sender
  // ==========================================
  {
    id: "SD-411",
    title: "SD-411: Implement outbound webhook sender with HMAC signatures and exponential backoff",
    module: "Merchant",
    labels: ["merchant", "webhooks", "backend"],
    dependsOn: ["SD-409", "SD-410"],
    body: `### Objective
Dispatch pending webhook delivery records using HMAC-SHA256 signatures, exponential backoff retries, and attempt logging.

### Scope
- Scheduled dispatcher claiming pending deliveries with \`SELECT ... FOR UPDATE SKIP LOCKED\`.
- Decrypt webhook secret and compute \`X-SwiftDrop-Signature\` header (\`t=timestamp,v1=hex(hmac)\`).
- Send HTTP POST via SSRF-safe client (SD-409).
- Record delivery attempt outcome, response code, and duration in \`webhook_attempts\` table.
- Retry transient failures (5xx, timeouts) with exponential backoff and jitter; terminate on permanent errors (4xx).

### Acceptance Criteria
- [ ] Webhook payload signed with valid HMAC-SHA256 header.
- [ ] Transient failures scheduled for retry with backoff.
- [ ] Max attempts cap moves delivery to FAILED state.
`,
  },

  // ==========================================
  // SD-412 · Webhook History & Manual Redelivery
  // ==========================================
  {
    id: "SD-412",
    title: "SD-412: Implement webhook delivery history and manual redelivery API and UI",
    module: "Merchant",
    labels: ["merchant", "webhooks", "admin", "frontend"],
    dependsOn: ["SD-411"],
    body: `### Objective
Provide merchants and admins with visibility into webhook delivery attempts and allow manual redelivery triggers for failed attempts.

### Scope
- \`GET /api/v1/merchant/webhooks/deliveries\` (Paged list of deliveries and attempts).
- \`POST /api/v1/merchant/webhooks/deliveries/{id}/redeliver\` (Re-queues failed delivery).
- Webhook management UI tab in merchant portal.

### Acceptance Criteria
- [ ] Merchants can view recent delivery logs and response codes.
- [ ] Failed webhooks can be manually re-triggered.
- [ ] \`npm run lint\` and \`npm run build\` pass.
`,
  },

  // ==========================================
  // SD-413 · Demo Webhook Receiver
  // ==========================================
  {
    id: "SD-413",
    title: "SD-413: Implement standalone demo webhook receiver and merchant integration guide",
    module: "Merchant",
    labels: ["merchant", "webhooks", "docs", "tools"],
    dependsOn: ["SD-411"],
    body: `### Objective
Provide a lightweight demo webhook receiver tool and documentation to verify merchant webhook delivery during testing and evaluation.

### Scope
- Implement a small Node.js receiver in \`tools/webhook-receiver/\`.
- Validates \`X-SwiftDrop-Signature\` using HMAC-SHA256, verifies timestamp drift, and logs received events.
- Write merchant integration guide documenting authentication, order intake, and webhook verification.

### Acceptance Criteria
- [ ] Demo receiver starts and logs validated status webhook events.
- [ ] Integration guide documented in repository docs.
`,
  },

  // ==========================================
  // SD-501 · RabbitMQ Topology
  // ==========================================
  {
    id: "SD-501",
    title: "SD-501: Declare RabbitMQ topic exchange, queues, and dead-letter topology",
    module: "Notification",
    labels: ["messaging", "infrastructure", "backend"],
    body: `### Objective
Declare the standardized RabbitMQ messaging topology including exchange, consumer queues, dead-letter exchanges (DLX), and publisher confirms per ADR 0004.

### Scope
- Spring AMQP configuration declaring topic exchange \`swiftdrop.events\`.
- Declare dead-letter exchange \`swiftdrop.dlx\` and dead-letter queue \`swiftdrop.dlq\`.
- Configure publisher confirms and publisher returns on \`RabbitTemplate\`.
- Integration test in \`backend/src/test\` verifying topology creation and message routing on RabbitMQ 4.3 container.

### Acceptance Criteria
- [ ] Exchange and queues declared on application startup.
- [ ] Dead-letter routing functions upon message rejection.
- [ ] Integration test passes with Testcontainers RabbitMQ.
`,
  },

  // ==========================================
  // SD-502 · Outbox Relay Publisher
  // ==========================================
  {
    id: "SD-502",
    title: "SD-502: Implement scheduled transactional outbox relay publisher",
    module: "Delivery",
    labels: ["delivery", "messaging", "backend"],
    dependsOn: ["SD-303", "SD-501"],
    body: `### Objective
Implement the outbox polling relay that reads unpublished records from \`parcel_outbox_events\` and publishes them to RabbitMQ with strict ordering per parcel.

### Scope
- Scheduled background task in \`com.swiftdrop.delivery.infrastructure.outbox\`.
- Read unpublished events ordered by \`parcel_id\` and \`aggregate_sequence\`.
- Publish to \`swiftdrop.events\` with routing key \`parcel.status-changed\`.
- Mark events as published upon confirmation; back off on broker connection failure.
- PostgreSQL advisory lock to ensure single active relay in multi-instance environments.

### Acceptance Criteria
- [ ] Outbox relay publishes pending events to RabbitMQ.
- [ ] Sequence order preserved per parcel.
- [ ] Handled broker outages without message loss or infinite tight-polling.
`,
  },

  // ==========================================
  // SD-503 · Shared Ordered Consumer
  // ==========================================
  {
    id: "SD-503",
    title: "SD-503: Implement shared ordered event consumer support and sequence deduplication",
    module: "Messaging",
    labels: ["messaging", "shared", "backend"],
    body: `### Objective
Implement shared message consumption logic to guarantee strictly ordered, idempotent processing of parcel events across consumer modules (ADR 0004 §4.3).

### Scope
- Reusable consumer coordinator in \`com.swiftdrop.shared.messaging\`.
- Tracks highest processed sequence number per parcel aggregate.
- Detects out-of-order deliveries and buffers premature events until gaps are reconciled.
- Deduplicates re-delivered messages.

### Acceptance Criteria
- [ ] Duplicate event delivery processed idempotently.
- [ ] Out-of-order events buffered until expected sequence arrives.
- [ ] Unit tests verify sequence gap detection and handling.
`,
  },

  // ==========================================
  // SD-504 · Notification Consumer & Mock SMS
  // ==========================================
  {
    id: "SD-504",
    title: "SD-504: Implement notification consumer and mock SMS delivery store",
    module: "Notification",
    labels: ["notification", "messaging", "database", "backend"],
    dependsOn: ["SD-501", "SD-503"],
    body: `### Objective
Consume parcel status change events from RabbitMQ and record customer notification messages in a persistent mock SMS dispatch log.

### Scope
- Flyway migration for \`notifications\` table in \`backend/src/main/resources/db/migration/notification/\`.
- RabbitMQ listener on queue \`notification.parcel-status-changed\`.
- Format notification text based on target status (contains tracking number, date/time, status; excludes sensitive personal address details).
- Persist record with recipient customer ID, tracking number, message content, and dispatch timestamp.

### Acceptance Criteria
- [ ] Status events generate formatted notification records.
- [ ] Recipient message content contains no PII.
- [ ] Passes \`MigrationConventionTest\` and \`DatabaseMigrationIT\`.
`,
  },

  // ==========================================
  // SD-505 · Parcel Events Reconciliation API
  // ==========================================
  {
    id: "SD-505",
    title: "SD-505: Implement delivery.api.ParcelEvents query for sequence gap reconciliation",
    module: "Delivery",
    labels: ["delivery", "api", "messaging", "backend"],
    dependsOn: ["SD-303"],
    body: `### Objective
Expose a public query contract on the delivery module allowing event consumers to fetch missing status history events when sequence gaps are detected.

### Scope
- Interface in \`com.swiftdrop.delivery.api\`: \`findEventsSince(UUID parcelId, long fromSequence)\`.
- Reconstructs event payloads from \`parcel_status_history\` identically to outbox events.
- Used by \`SD-503\` shared consumer to reconcile missing message sequences.

### Acceptance Criteria
- [ ] Returns chronological list of missing status events for parcel.
- [ ] Reconstructed event IDs and payload data match original event contract.
`,
  },

  // ==========================================
  // SD-506 · Customer Notifications Inbox
  // ==========================================
  {
    id: "SD-506",
    title: "SD-506: Implement customer notification inbox API and frontend notification center",
    module: "Notification",
    labels: ["notification", "frontend", "api", "backend"],
    dependsOn: ["SD-504"],
    body: `### Objective
Provide customers with an inbox API and UI bell dropdown to review delivery status alerts.

### Scope
- \`GET /api/v1/notifications\` (Authenticated customer, paged, newest first).
- \`PATCH /api/v1/notifications/{id}/read\` (Mark notification as read).
- Frontend notification dropdown in customer portal shell header with unread count badge.

### Acceptance Criteria
- [ ] Customers can retrieve their notification history and mark alerts as read.
- [ ] Header badge indicates unread notification count.
- [ ] \`npm run lint\` and \`npm run build\` pass.
`,
  },

  // ==========================================
  // SD-507 · Dead Letter Replay
  // ==========================================
  {
    id: "SD-507",
    title: "SD-507: Implement admin dead-letter queue inspection and audited replay endpoint",
    module: "Messaging",
    labels: ["messaging", "admin", "api", "backend"],
    dependsOn: ["SD-501"],
    body: `### Objective
Provide administrative visibility into messages routed to the RabbitMQ dead-letter queue (DLQ) and an audited manual replay mechanism.

### Scope
- \`GET /api/v1/admin/dead-letters\` (Admin only: view DLQ contents with error headers and death count).
- \`POST /api/v1/admin/dead-letters/{id}/replay\` (Admin only: republishes message to target queue with audit logging).
- Audit log entry records administrator ID, timestamp, and message ID.

### Acceptance Criteria
- [ ] Admins can inspect dead-lettered messages and failure reasons.
- [ ] Manual replay routes message back to original consumer queue.
- [ ] Replay action logged with audit trail.
`,
  },

  // ==========================================
  // SD-508 · SSE Live Tracking Stream
  // ==========================================
  {
    id: "SD-508",
    title: "SD-508: Implement Server-Sent Events (SSE) live parcel tracking endpoint",
    module: "Tracking",
    labels: ["tracking", "sse", "api", "backend"],
    dependsOn: ["SD-304"],
    body: `### Objective
Provide a real-time Server-Sent Events (SSE) endpoint allowing browsers to stream immediate status updates for an active parcel without polling (ADR 0003, ADR 0004).

### Scope
- \`GET /api/v1/tracked-parcels/{trackingNumber}/live\` (Public endpoint).
- Emit immediate snapshot event on connection containing current status and timeline.
- In-memory event registry listening for application status change events.
- Push live status update event when matching parcel changes status.
- Implement heartbeat comments every 15s, connection timeout handling, and client disconnect cleanup.
- Strictly exclude PII from stream payloads.

### Acceptance Criteria
- [ ] Browser receives current snapshot immediately on SSE connection.
- [ ] Status updates pushed in real time upon transition commit.
- [ ] Client disconnects cleaned up without resource leaks.
`,
  },

  // ==========================================
  // SD-510 · Public Live Tracking Page
  // ==========================================
  {
    id: "SD-510",
    title: "SD-510: Implement public live tracking web page with real-time SSE updates",
    module: "Tracking",
    labels: ["tracking", "frontend"],
    dependsOn: ["SD-508"],
    body: `### Objective
Provide a public web page at \`frontend/app/track/[trackingNumber]/page.tsx\` displaying real-time parcel progress with automatic live updates.

### Scope
- Tracking search bar on landing and tracking pages.
- Visual milestone timeline (Created, Picked Up, In Transit, Out for Delivery, Delivered / Failed).
- Real-time connection via browser \`EventSource\` to SSE endpoint with reconnect handling.
- Displays failure reason badge if parcel has reached FAILED status.
- Works without user authentication; responsive on 360px viewport.

### Acceptance Criteria
- [ ] Public page displays parcel status timeline.
- [ ] Receives and renders live SSE status updates without page reload.
- [ ] \`npm run lint\` and \`npm run build\` pass.
`,
  },

  // ==========================================
  // SD-603 · Responsive & Accessibility Pass
  // ==========================================
  {
    id: "SD-603",
    title: "SD-603: Complete responsive layout and accessibility audit across all frontend portals",
    module: "Frontend",
    labels: ["frontend", "accessibility", "quality"],
    dependsOn: ["SD-209", "SD-313", "SD-308", "SD-407", "SD-510"],
    body: `### Objective
Conduct an end-to-end responsiveness and accessibility review across customer, courier, merchant, dispatch, and admin portals.

### Scope
- Verify usability at 360px viewport width (especially courier mobile and tracking views).
- Check keyboard navigation and focus rings across all forms and dialogs.
- Verify color contrast and WCAG 2.1 AA compliance on badges, charts, and tables.
- Standardize empty, loading skeleton, and error boundary states.

### Acceptance Criteria
- [ ] All portal pages functional on 360px mobile viewports.
- [ ] Keyboard navigation operable throughout portal forms.
- [ ] \`npm run lint\` and \`npm run build\` pass without errors or warnings.
`,
  },

  // ==========================================
  // SD-701 · Event Flow Integration Test
  // ==========================================
  {
    id: "SD-701",
    title: "SD-701: Implement end-to-end event-flow integration test with RabbitMQ container (TST-1)",
    module: "Quality & Testing",
    labels: ["test", "messaging", "backend"],
    dependsOn: ["SD-502", "SD-504", "SD-410"],
    body: `### Objective
Implement the mandatory TST-1 end-to-end integration test verifying status change propagation through the transactional outbox, RabbitMQ, and downstream consumers.

### Scope
- Integration test in \`backend/src/test/java/com/swiftdrop/\` run via \`-Pintegration\`.
- Triggers parcel status transition -> verifies outbox record written.
- Outbox relay executes -> publishes to \`swiftdrop.events\`.
- Verifies notification record created in database.
- Verifies merchant webhook delivery record created.
- Verifies malformed message routing to DLQ with dead-letter headers.

### Acceptance Criteria
- [ ] TST-1 integration test passes on Testcontainers PostgreSQL and RabbitMQ.
- [ ] Verified via \`./mvnw -Pintegration verify\`.
`,
  },

  // ==========================================
  // SD-703 · CI Test Results & Coverage Reporting
  // ==========================================
  {
    id: "SD-703",
    title: "SD-703: Configure Surefire/Failsafe test results and coverage reporting in CI (TST-3)",
    module: "Quality & Testing",
    labels: ["quality", "ci", "test", "backend"],
    dependsOn: ["SD-004"],
    blockedBy: "Team approval of D-11 (JaCoCo plugin baseline)",
    body: `### Objective
Configure GitHub Actions CI and Maven reporting to publish Surefire/Failsafe test summaries and coverage artifacts to support requirement TST-3 and submission report compilation.

### Scope
- Update \`.github/workflows/ci.yml\` to upload Surefire and Failsafe test XML/HTML reports as build artifacts.
- Archive coverage report generated by SD-004 (pending resolution of team decision D-11).
- Document how test counts, pass rates, and coverage statistics are extracted for the submission test report (SD-908).
- Out of scope: Modifying accepted ADR 0002 or enforcing arbitrary build failure thresholds before team alignment on D-11.

### Implementation Guidance
- Follow existing workflow conventions in \`.github/workflows/ci.yml\`.
- Keep Docker-free backend build steps untouched in default PR verification.

### Acceptance Criteria
- [ ] CI workflow publishes Surefire/Failsafe test results as accessible artifacts.
- [ ] Coverage artifacts from SD-004 uploaded upon successful verify goals.
- [ ] Extraction procedure documented for test report compilation.

### Dependencies
- Depends on SD-004 (code coverage configuration).
- Blocked by team approval of decision D-11 (JaCoCo plugin baseline).
`,
  },

  // ==========================================
  // SD-704 · Security & Personal Data Review
  // ==========================================
  {
    id: "SD-704",
    title: "SD-704: Perform project-wide security, authorization, and personal-data privacy audit",
    module: "Quality & Testing",
    labels: ["security", "quality", "docs"],
    body: `### Objective
Execute a comprehensive security and personal-data privacy audit across all backend endpoints, database entities, logs, and frontend displays.

### Scope
- Verify 404-on-unowned resource access across all customer, courier, and merchant endpoints.
- Ensure personal data (addresses, phone numbers, recipient names) is excluded from logs, error responses, outbox events, and SSE streams.
- Scan codebase for accidentally hardcoded secrets or insecure defaults.
- Verify that Swagger UI and Actuator endpoints are configured securely for production readiness.

### Acceptance Criteria
- [ ] Security audit checklist completed.
- [ ] Zero sensitive data leaked in logs or outbox events.
- [ ] Findings and hardening documented in architecture notes.
`,
  },

  // ==========================================
  // SD-801 · Backend Dockerfile
  // ==========================================
  {
    id: "SD-801",
    title: "SD-801: Create multi-stage production Dockerfile for backend service",
    module: "Deployment",
    labels: ["docker", "infrastructure"],
    body: `### Objective
Create a secure, optimized multi-stage Dockerfile for containerizing the Spring Boot backend service.

### Scope
- Create \`backend/Dockerfile\`.
- Build stage: Eclipse Temurin JDK 21 on Alpine/Ubuntu, running Maven build.
- Runtime stage: Eclipse Temurin JRE 21, running as dedicated non-root user.
- Healthcheck configured against \`/actuator/health\`.
- All environment properties configurable via container environment variables.

### Acceptance Criteria
- [ ] Multi-stage Dockerfile builds backend cleanly.
- [ ] Image runs as non-root user.
- [ ] Container healthcheck reports healthy status upon startup.
`,
  },

  // ==========================================
  // SD-802 · Frontend Dockerfile
  // ==========================================
  {
    id: "SD-802",
    title: "SD-802: Create multi-stage production Dockerfile for Next.js frontend",
    module: "Deployment",
    labels: ["docker", "infrastructure"],
    body: `### Objective
Create an optimized multi-stage Dockerfile for the Next.js frontend using standalone output mode.

### Scope
- Create \`frontend/Dockerfile\`.
- Multi-stage build leveraging Next.js \`output: "standalone"\` in \`next.config.ts\`.
- Node 24 Alpine runtime running as non-root user.
- Configurable \`NEXT_PUBLIC_API_BASE_URL\` environment variable.

### Acceptance Criteria
- [ ] Frontend image builds successfully from lockfile.
- [ ] Runs as non-root user with minimal image footprint.
`,
  },

  // ==========================================
  // SD-803 · Full Stack Compose Profile
  // ==========================================
  {
    id: "SD-803",
    title: "SD-803: Configure full-stack application profile in compose.yaml",
    module: "Deployment",
    labels: ["docker", "infrastructure"],
    dependsOn: ["SD-801", "SD-802"],
    body: `### Objective
Add an application profile to \`compose.yaml\` allowing team members and evaluators to run the entire SwiftDrop application stack with a single command.

### Scope
- Update \`compose.yaml\` to define \`backend\` and \`frontend\` services under the \`app\` profile.
- Service dependencies: \`backend\` depends on healthy \`postgres\` and \`rabbitmq\`; \`frontend\` depends on \`backend\`.
- Default \`docker compose up\` continues to start only database and broker infrastructure.
- Running \`docker compose --profile app up\` boots all four services cleanly.

### Acceptance Criteria
- [ ] Full-stack application boots with \`docker compose --profile app up\`.
- [ ] Default infrastructure startup remains unchanged.
- [ ] Verified via \`docker compose config --quiet\`.
`,
  },

  // ==========================================
  // SD-804 · Setup Documentation
  // ==========================================
  {
    id: "SD-804",
    title: "SD-804: Update root README with verified submission and demo setup guide",
    module: "Deployment",
    labels: ["docs", "deployment"],
    dependsOn: ["SD-803"],
    body: `### Objective
Update root \`README.md\` and \`docs/development-setup.md\` with verified, end-to-end evaluation and setup instructions for submission.

### Scope
- Single-command startup instructions using Docker Compose.
- Bootstrap administrator credentials setup.
- Step-by-step test commands (\`./mvnw clean test\`, \`./mvnw -Pintegration verify\`, \`npm run lint\`).
- Portal roles guide and evaluation tour.

### Acceptance Criteria
- [ ] README setup instructions verified on clean clone.
- [ ] Clear pointers to architecture docs, ADRs, and role portals.
`,
  },

  // ==========================================
  // SD-901 to SD-906 · Design Artifacts
  // ==========================================
  {
    id: "SD-901",
    title: "SD-901: Create and commit system Use Case diagrams (DES-1)",
    module: "Design Docs",
    labels: ["design", "docs"],
    body: `### Objective
Document comprehensive system use case diagrams covering Customer, Courier, Merchant, and Dispatcher/Admin actors per project brief requirement DES-1.

### Scope
- PlantUML or Mermaid diagram sources committed in \`docs/design/use-cases/\`.
- Model all functional requirements across all four user roles.
- Include actor boundary descriptions and include/extend relationships.

### Acceptance Criteria
- [ ] Use case diagram source files committed to repository.
- [ ] Covers all requirements (CUS, COU, MER, ADM).
`,
  },
  {
    id: "SD-902",
    title: "SD-902: Create and commit complete Entity-Relationship (ER) diagram (DES-2)",
    module: "Design Docs",
    labels: ["design", "docs", "database"],
    body: `### Objective
Create and commit the authoritative Entity-Relationship diagram covering all SwiftDrop database schemas and tables per requirement DES-2.

### Scope
- Diagrams committed in \`docs/design/er/\`.
- Reflect tables across all modules: \`users\`, \`roles\`, \`external_identities\`, \`zones\`, \`pricing_rules\`, \`parcels\`, \`parcel_status_history\`, \`parcel_outbox_events\`, \`delivery_proofs\`, \`couriers\`, \`merchants\`, \`merchant_api_keys\`, \`webhook_endpoints\`, \`notifications\`.
- Color-code table module ownership boundaries per ADR 0003.

### Acceptance Criteria
- [ ] ER diagram matches merged Flyway migration schemas exactly.
- [ ] Module ownership boundaries visually distinguished.
`,
  },
  {
    id: "SD-903",
    title: "SD-903: Author architecture proposal and modular monolith design report (DES-3)",
    module: "Design Docs",
    labels: ["design", "docs"],
    body: `### Objective
Author the design report section justifying the modular monolith architectural style, layer separation, and technology selection per requirement DES-3.

### Scope
- Commit document under \`docs/design/architecture-proposal.md\`.
- Detail architectural drivers: bounded contexts, encapsulation, transactional consistency, simplified operations.
- Cross-reference ADR 0001, ADR 0002, and ADR 0003.

### Acceptance Criteria
- [ ] Architecture proposal authored and reviewed by team.
- [ ] Justifies modular monolith vs microservices with concrete trade-offs.
`,
  },
  {
    id: "SD-904",
    title: "SD-904: Author messaging, outbox, and event integration design document (DES-4)",
    module: "Design Docs",
    labels: ["design", "docs", "messaging"],
    body: `### Objective
Author the integration design document detailing synchronous REST patterns, transactional outbox mechanics, RabbitMQ pub/sub, SSE, and webhooks per requirement DES-4.

### Scope
- Document committed under \`docs/design/integration-design.md\`.
- Explain reliability guarantees: at-least-once delivery, monotonic sequences, idempotency, failure backoffs, and dead-lettering.
- Cross-reference ADR 0004.

### Acceptance Criteria
- [ ] Integration document authored with clear communication flow diagrams.
`,
  },
  {
    id: "SD-905",
    title: "SD-905: Create sequence and activity diagrams for core business workflows (DES-5)",
    module: "Design Docs",
    labels: ["design", "docs"],
    body: `### Objective
Create detailed sequence and activity diagrams illustrating end-to-end execution across SwiftDrop's primary business flows per requirement DES-5.

### Scope
- Diagrams committed under \`docs/design/sequence/\`.
- Workflow 1: Customer parcel booking with server fee recalculation.
- Workflow 2: Programmatic merchant order submission via API key.
- Workflow 3: Courier status advancement -> outbox -> RabbitMQ -> notification + webhook dispatch.
- Workflow 4: Google OAuth 2.0 authorization code handoff and exchange.

### Acceptance Criteria
- [ ] Sequence diagrams reflect actual code implementations and contracts.
`,
  },
  {
    id: "SD-906",
    title: "SD-906: Author software design patterns application summary (DES-6)",
    module: "Design Docs",
    labels: ["design", "docs"],
    body: `### Objective
Document the practical software engineering and design patterns implemented throughout the SwiftDrop codebase per requirement DES-6.

### Scope
- Document in \`docs/design/design-patterns.md\`.
- Cover patterns in use:
  - Repository Pattern (Spring Data JPA)
  - Strategy Pattern (\`FeeCalculator\` pricing engine)
  - Observer / Pub-Sub Pattern (RabbitMQ domain events, in-process Spring events)
  - Transactional Outbox Pattern (\`parcel_outbox_events\`)
  - Facade Pattern (module \`api\` packages)
  - Factory & Builder records
- Cite concrete classes and files.

### Acceptance Criteria
- [ ] Pattern summary details real implementations with line references.
`,
  },

  // ==========================================
  // SD-907 to SD-910 · Final Submission & Demo
  // ==========================================
  {
    id: "SD-907",
    title: "SD-907: Compile and assemble comprehensive system design documentation PDF (SUB-2)",
    module: "Submission",
    labels: ["submission", "docs"],
    dependsOn: ["SD-901", "SD-902", "SD-903", "SD-904", "SD-905", "SD-906"],
    body: `### Objective
Assemble all design artifacts (DES-1 through DES-6) into a polished, professional design documentation document for final project submission.

### Scope
- Consolidate use cases, ER diagrams, architectural rationale, integration topology, sequence diagrams, and design pattern notes.
- Format document consistently with table of contents and team contribution index.

### Acceptance Criteria
- [ ] Design documentation compiled and verified for submission.
`,
  },
  {
    id: "SD-908",
    title: "SD-908: Compile test report and automated test coverage summary (SUB-3)",
    module: "Submission",
    labels: ["submission", "test", "docs"],
    dependsOn: ["SD-004", "SD-701"],
    body: `### Objective
Compile the comprehensive test execution report and JaCoCo coverage metrics for project submission per requirement SUB-3.

### Scope
- Summarize backend unit, integration, and security authorization matrix results.
- Tabulate coverage percentages across business modules.
- Document TST-1 event flow and TST-2 security matrix evidence.

### Acceptance Criteria
- [ ] Test report compiled with verified metrics and test run evidence.
`,
  },
  {
    id: "SD-909",
    title: "SD-909: Compile individual team contribution tracking sheet (SUB-4)",
    module: "Submission",
    labels: ["submission", "docs"],
    body: `### Objective
Document individual contribution summaries for all team members mapped against GitHub pull requests, commits, and assigned workstreams per requirement SUB-4.

### Scope
- Detail modules, issues, and specific components authored by each team member.
- Reconcile with GitHub commit history and merged pull requests.

### Acceptance Criteria
- [ ] Contribution sheet completed and reviewed by all team members.
`,
  },
  {
    id: "SD-910",
    title: "SD-910: Create scripted live demonstration walkthrough and rehearsal guide (DEM-1..6)",
    module: "Submission",
    labels: ["submission", "demo", "docs"],
    dependsOn: ["SD-803"],
    body: `### Objective
Develop the official demonstration runbook and rehearse the live evaluation script covering scenarios DEM-1 through DEM-6 from containerized environment.

### Scope
- Step-by-step instructions for live demo:
  - DEM-1: Customer booking and live SSE tracking.
  - DEM-2: Programmatic merchant order and signed webhook delivery.
  - DEM-3: Dispatch assignment and mobile courier proof capture.
  - DEM-4: Asynchronous status alerts and customer notification inbox.
  - DEM-5: Architecture walkthrough (modular monolith boundaries and outbox).
  - DEM-6: Team member question-and-answer assignments.

### Acceptance Criteria
- [ ] Complete demo runbook documented in \`docs/demo-script.md\`.
- [ ] End-to-end rehearsal executed successfully from containers.
`,
  },
];
