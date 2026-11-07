# 0008. Merchant API key authentication

- Status: Proposed
- Date: 2026-11-08

## Context

Merchants create delivery orders from their own systems through a secured API (MER-2). The brief
allows "API keys or OAuth 2.0 client credentials" (MER-2, SYS-3); the team must choose one and not
build both. Architecture §9 lists this as open, and the implementation contract records the
proposal as decision D-03 ("API keys, hashed, prefix lookup") with an ADR still required.

Constraints:

- [ADR 0003](0003-backend-modules-and-boundaries.md): `merchant` owns merchant API credentials;
  `config` wires user and merchant authentication into the security filter chain and imports
  only `merchant.api`. `identity` keeps users, roles and JWTs.
- Merchant capabilities require merchant status `APPROVED`, checked on every request; the role
  alone is not enough (contract §6, D-22). Member A's `MerchantLookup.isApproved` provides the check
  (`docs/merchant-contracts.md` §2.1).
- Credentials are stored hashed or encrypted, never in plain text; secrets and personal data stay
  out of logs (project rules, SYS-7).
- The existing API is stateless: JWT access tokens in the `Authorization` header, no sessions, no
  cookies, CORS only for the frontend's origins with the headers `Authorization` and `Content-Type`.
- Simplicity rule: no new dependency or infrastructure without a clear need.

## Decision

### 1. Mechanism: static API keys

Merchant systems authenticate with **API keys**. OAuth 2.0 client credentials are not implemented.

### 2. Key format

```text
sdk_<prefix>_<secret>
sdk_k7m2q9xw4ht1_Qm9n3r...   (60 characters in total)
```

- `sdk_`: fixed marker ("SwiftDrop key"), so leaked keys are easy to recognize and to scan for.
- `<prefix>`: 12 characters from `[a-z0-9]`, generated with `SecureRandom`. It identifies the key,
  is not secret, is unique (database constraint; generation retries on a collision) and is shown in
  the portal so merchants can tell keys apart.
- `<secret>`: 32 bytes from `SecureRandom`, encoded as base64url without padding (43 characters).
  The secret may contain `_` and `-`; parsing splits only at the first two `_`.
- A key that does not match `^sdk_[a-z0-9]{12}_[A-Za-z0-9_-]{43}$` is rejected before any database
  access.

### 3. Storage, hashing and verification

- The table `merchant_api_keys` (owned by `merchant`, contract §5.5) stores `key_prefix` and
  `key_hash`, plus `merchant_id`, `label`, `created_at`, `last_used_at` and `revoked_at`.
  The full key and the secret are **never stored**; the key is shown **once**, in the response that
  creates it.
- `key_hash` is the lowercase hex **SHA-256 of the full key string** (UTF-8). Hashing the whole key
  binds the prefix to the secret. A fast hash is sufficient because the secret has 256 bits of
  entropy: brute force is infeasible, unlike with passwords, and a slow hash such as BCrypt would
  cost CPU on every API request without adding security.
- Verification: check the format, look up the row by `key_prefix` (unique index), then compare the
  SHA-256 of the presented key with `key_hash` using `MessageDigest.isEqual` (constant time).
  A revoked key (`revoked_at` set) fails.
- The merchant must be `APPROVED` at request time (`MerchantLookup.isApproved`). There is no cache,
  so revocation, suspension and rejection take effect on the next request.
- `last_used_at` is updated at most once per minute per key, to avoid a write on every request.

### 4. Failure response

Every failure (missing header, malformed key, unknown prefix, wrong secret, revoked key,
merchant not `APPROVED`) gets the **same** response: `401` with the project's Problem Details body
and `WWW-Authenticate: ApiKey`. Nothing tells a caller which check failed. The prefix is not secret,
so a timing difference between a known and an unknown prefix reveals nothing useful.

### 5. Transport and security chain

- Header: `X-Api-Key: <key>`. Keys are never accepted in query strings, request bodies or cookies,
  and must be sent over HTTPS outside local development.
- A separate `SecurityFilterChain` in `config` with higher precedence covers
  **`/api/v1/merchant-orders/**`** only. It authenticates the `X-Api-Key` header through
  `merchant.api.MerchantAuthentication` and is stateless with CSRF disabled. It has no JWT
  resource server, so a bearer token is not accepted there. The existing chain does not read
  `X-Api-Key`, so a key is not accepted anywhere else.
- The authenticated principal carries only the **merchant ID** and the key ID, with the single
  authority `MERCHANT_API_KEY`; it never carries a user role, so a key cannot reach any user
  endpoint. Endpoints take the merchant ID from the principal, never from the request body.
- The merchant API is **server-to-server**: `X-Api-Key` is not added to the CORS allowed headers,
  so browsers cannot send keys cross-origin. This also discourages embedding keys in web pages.

### 6. Key management by merchants

- Merchant users (JWT with role `MERCHANT`) whose merchant is `APPROVED` manage their own keys:
  `POST /api/v1/merchants/me/api-keys` (with a label; returns the full key once),
  `GET /api/v1/merchants/me/api-keys` (prefix, label, created, last used, revoked; never the hash),
  `DELETE /api/v1/merchants/me/api-keys/{id}` (revokes by setting `revoked_at`; rows are kept).
  Another merchant's key answers `404`.
- At most **5 active keys** per merchant, enough for rotation without accumulating unused keys.
  **Rotation:** create a new key, deploy it, then revoke the old one. Keys do not expire in v1.
- **Suspension or rejection does not revoke keys.** The per-request status check already blocks
  them, and a reinstated merchant's integrations work again without new keys. Merchants can
  revoke keys themselves at any time.

### 7. Logging and audit

- Keys, secrets, hashes and the `X-Api-Key` header are never logged or returned after creation.
  Log lines may contain the key prefix and merchant ID.
- Key creation and revocation are logged with merchant ID, key prefix and acting user ID.

### 8. Tests required with the implementation (SD-403, SD-404)

- Generation: format, prefix alphabet, secret length, different keys on each call, prefix
  collision retry.
- Verification: valid key; malformed, unknown, wrong-secret and revoked keys; a merchant that is
  `PENDING`, `REJECTED` or `SUSPENDED`; all with the same `401` body.
- Chains: a JWT is rejected on `/api/v1/merchant-orders/**`; an API key is rejected on every other
  endpoint; `config` imports only `merchant.api` (`ModuleBoundaryTest`).
- Management: key shown once; list never returns the hash; other merchant's key `404`; the limit of
  5 active keys; non-approved merchants cannot create keys.
- No key or secret appears in logs or error responses; `last_used_at` is written at most once per
  minute.

## Alternatives considered

- **OAuth 2.0 client credentials:** an authorization server (Spring Authorization Server, a new
  dependency) issuing short-lived tokens to registered clients. Standard and with short-lived
  tokens, but it adds a token endpoint, client registration and token signing next to the
  existing JWTs, and a second JWT issuer in the resource server. That is too much for one API
  with a handful of merchants; the brief accepts API keys.
- **Bearer key in `Authorization`:** would collide with the JWT resource server, which parses every
  bearer token; a separate header keeps the two mechanisms in separate chains.
- **BCrypt or Argon2 hashes:** designed for low-entropy passwords; they slow every request and need
  no prefix scheme, but add nothing against brute force of a 256-bit secret.
- **Encrypted keys (recoverable):** the system never needs the key again, so a one-way hash is
  safer.
- **HMAC with a server-side pepper:** protects weak secrets if the database leaks; unnecessary with
  256-bit secrets and adds key management.
- **Revoking keys automatically on suspension:** simpler to reason about, but forces merchants to
  rotate keys after reinstatement; the per-request status check already blocks a suspended
  merchant.
- **Caching key lookups:** saves two queries per request, but delays revocation and suspension;
  revisit only if the merchant API becomes a measured bottleneck.

## Consequences

- No new dependency; the implementation uses Spring Security, `SecureRandom` and `MessageDigest`
  from the JDK.
- Keys are long-lived: a leaked key works until it is revoked or the merchant is suspended.
  Merchants must store keys as secrets; the integration guide (SD-413) explains rotation.
- Each merchant API request costs an indexed lookup by prefix and a merchant status lookup.
- No rate limiting on the merchant API in v1 (known risk, as for the public tracking endpoints).
- `config` gains a second filter chain, reviewed by the `config` owner (SD-404).
- `docs/merchant-contracts.md` §1 names the order endpoint `/api/v1/merchant/parcels`; this ADR
  and the implementation contract use `/api/v1/merchant-orders`. The contract document should be
  aligned when this ADR is accepted.
- Resolves D-03 and the merchant API item in architecture §9 when accepted.
