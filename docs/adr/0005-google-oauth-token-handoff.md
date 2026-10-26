# 0005. Google OAuth token handoff

- Status: Accepted
- Date: 2026-10-26

## Context

SwiftDrop supports customer registration and login with local credentials (email and password), and must support "Login with Google" (requirement CUS-1, demo requirement DEM-1) using OpenID Connect (OIDC).

The backend already implements the core OIDC flow using Spring Security's `oauth2Login` (active when `SWIFTDROP_GOOGLE_CLIENT_ID` is set). The browser initiates authentication at `GET /oauth2/authorization/google` and Google redirects back to `GET /login/oauth2/code/google`. Spring Security validates the OAuth `state` parameter, exchanges the authorization code with Google, and verifies the signed OIDC ID token. The identity module's `GoogleSignInService` resolves the validated identity to a `User` entity.

However, the callback mechanism currently returns the application JWT as raw JSON (`AccessTokenResponse`) directly from `GET /login/oauth2/code/google`. Because the callback is a full browser navigation, this leaves the user looking at raw JSON on the backend origin (`http://localhost:8080`) rather than returning them to the Next.js frontend application (`http://localhost:3000`).

A mechanism is needed to hand off the authenticated session from the backend callback to the frontend single-page application without introducing security vulnerabilities.

### Security and Architectural Constraints

1. **No tokens in URLs:** Placing the application JWT into a query parameter or URL fragment (e.g. `SWIFTDROP_FRONTEND_BASE_URL/callback#token=...`) exposes bearer tokens to browser history, intermediate proxy logs, server access logs, and HTTP `Referer` headers.
2. **Stateless backend:** The backend application REST API is stateless; authentication uses bearer JWTs in the `Authorization` header. Sessions must not be retained after the OAuth exchange completes.
3. **Open redirect prevention:** Neither OAuth state, backend redirection, nor frontend callback query parameters may be used to redirect users to external origins.
4. **Account takeover prevention:** Google accounts must never be linked automatically to existing local accounts solely by matching email address.
5. **Architectural consistency:** The identity module owns authentication and credential tables (ADR 0001, ADR 0003). All database schema changes must be versioned Flyway migrations.

---

## Decision

We propose the **Backend-Mediated Authorization Code Exchange Flow (D-02)** for Google OAuth token handoff:

```
[Browser / Frontend]                [Backend API]                  [Google OIDC]
        |                                 |                              |
        |-- Store sanitized return path   |                              |
        |   in sessionStorage             |                              |
        |--- 1. Click "Google Sign In" -->|                              |
        |    GET /oauth2/authorization/google                            |
        |<-- 2. 302 Redirect to Google ---|                              |
        |                                 |                              |
        |--- 3. Authenticate & Consent --------------------------------->|
        |<-- 4. 302 Redirect to Backend with auth code ------------------|
        |                                 |                              |
        |--- 5. GET /login/oauth2/code/google?code=...&state=... ------->|
        |                                 |--- 6. Token exchange ------->|
        |                                 |<-- 7. Verified ID Token -----|
        |                                 |                              |
        |                                 |-- Resolve identity / user    |
        |                                 |-- Opportunistic code purge   |
        |                                 |-- Generate 32-byte code      |
        |                                 |-- Persist SHA-256(code)      |
        |<-- 8. 302 Redirect with code ---|                              |
        |    ${FRONTEND_BASE_URL}/auth/google/callback?code=...          |
        |                                 |                              |
        |--- 9. POST /api/v1/auth/google/exchanges { code } ------------>|
        |                                 |-- Atomic conditional UPDATE  |
        |                                 |-- Issue application JWT      |
        |<-- 10. 200 OK { accessToken } --|                              |
        |                                 |                              |
        |-- Retrieve & sanitize return    |                              |
        |   path from sessionStorage      |                              |
        |-- Navigate to safe target       |                              |
```

### 1. OAuth Handshake and Callback Redirection

1. The backend initiates and completes the OIDC handshake with Google using Spring Security's `oauth2Login`.
2. Upon successful authentication, the backend generates a cryptographically secure, high-entropy exchange code.
3. The backend sets HTTP response status 302 and redirects the user's browser strictly to the frontend callback page:
   ```
   ${SWIFTDROP_FRONTEND_BASE_URL}/auth/google/callback?code=<raw_code>
   ```
4. **No Arbitrary Destinations in URLs:** The backend redirection URL carries **only** the single-use `code` (or `error`). Arbitrary post-login return URLs are **not** forwarded through backend redirection URLs or OAuth `state`, completely preventing open redirects at the backend boundary.
5. The application JWT is **never** sent via redirection URLs.

### 2. Exchange Code Security and Atomic Redemption

1. **Entropy:** The exchange code is generated using `java.security.SecureRandom`, containing at least 256 bits of entropy (32 random bytes), formatted as a URL-safe Base64 string without padding.
2. **Storage:** The raw exchange code is never persisted in plain text. The backend computes the SHA-256 digest (64-character lowercase hexadecimal string) and persists only the hash in the database.
3. **Lifetime:** The exchange code expires after 60 seconds (`expires_at = now() + INTERVAL '60 seconds'`).
4. **Atomic Single-Use Redemption:** The code can be redeemed exactly once. Redemption is performed via an atomic conditional SQL update inside a database transaction:
   ```sql
   UPDATE google_login_codes
   SET used_at = CURRENT_TIMESTAMP
   WHERE code_hash = :codeHash
     AND used_at IS NULL
     AND expires_at > CURRENT_TIMESTAMP
   RETURNING user_id;
   ```
5. **Transactional Consistency & Failure Semantics:**
   - The conditional update and user lookup execute within an `@Transactional` boundary in the `identity` module.
   - If user retrieval, token signing, or database connectivity fails unexpectedly before the transaction commits, the transaction rolls back, leaving the code unconsumed so the user may re-attempt the exchange before the 60-second expiry.
   - Once committed, `used_at` is permanently set.
6. **Concurrent Requests & Race Prevention:**
   - PostgreSQL row-level locks on the `code_hash` primary key ensure serial evaluation.
   - If concurrent requests submit the same code, exactly one transaction updates the row and receives the `user_id`. The competing transaction updates 0 rows and immediately fails.
7. **No Replay or Weakened Guarantees:**
   - Single-use guarantees are **never weakened** to support automatic client retries. If an exchange fails after consumption (e.g. network disconnection before the frontend receives the response), the code remains dead and the user must re-initiate login from `/login`.
8. **Uniform Generic 401 Response:**
   - If no row is updated (missing, malformed, expired, non-existent, or already-redeemed code), the endpoint returns HTTP `401 Unauthorized` with an RFC 9457 Problem Details body (`title: "Unauthorized"`, `detail: "Invalid or expired exchange code"`).
   - The exact same response is returned regardless of whether the code was expired, invalid, or replayed, preventing timing and enumeration oracles.

### 3. Frontend Redemption Endpoint

The `identity` module exposes a public REST endpoint:
- **Method & Path:** `POST /api/v1/auth/google/exchanges`
- **Request Body:**
  ```json
  {
    "code": "string"
  }
  ```
- **Response (200 OK):** Standard `AccessTokenResponse`:
  ```json
  {
    "accessToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
  ```
- **Error Responses:**
  - `400 Bad Request`: Missing or blank code field.
  - `401 Unauthorized`: Invalid, expired, or already-used exchange code.

### 4. Failure Redirect Contract

When Google sign-in fails or is rejected by backend business rules, the backend **must not** render raw JSON error pages to the browser. Instead, it invalidates the pending session and redirects to the frontend callback with a standardized error code from an explicit allowlist:
```
${SWIFTDROP_FRONTEND_BASE_URL}/auth/google/callback?error=<error_code>
```

#### Allowed Frontend Error Codes
| Error Code | Trigger Condition | User-Facing Explanation on Frontend |
|---|---|---|
| `conflict_email_exists` | Google email matches an existing local user not linked to this Google subject | "An account with this email already exists. Please sign in with your email and password." |
| `unverified_email` | Google account's `email_verified` claim is `false` | "Your Google account email address is not verified. Please verify your email with Google and try again." |
| `account_disabled` | User account has been deactivated (`enabled = false`) | "Your account has been deactivated. Please contact support." |
| `cancelled` | User denied consent or cancelled login at Google | "Google sign-in was cancelled." |
| `oauth_failed` | State parameter mismatch, invalid code, or provider communication error | "Google sign-in failed. Please try again." |

#### Security Constraints on Failure Handling:
- **Prohibition of Raw Error Leakage:** The backend never forwards arbitrary exception messages, stack traces, or raw provider error descriptions in query parameters.
- **Strict UI Mapping:** The frontend callback page maps `error` strictly against this hardcoded dictionary to display an alert banner.
- **No Dynamic External Navigation on Error:** Error parameters cannot specify or influence navigation targets. Any interactive fallback link (such as "Return to Sign In") navigates strictly to a hardcoded internal route (`/login`).

### 5. Safe Post-Login Navigation

1. **Frontend Storage:** Before navigating the browser to `GET /oauth2/authorization/google`, the login page inspects any requested post-login internal destination (e.g. from `?redirect=...`).
2. **Double Sanitization:**
   - **First Pass (Pre-OAuth):** The frontend sanitizes the path using `sanitizeRedirectTarget(...)` and, if valid and not `/login`, stores it in browser `sessionStorage` under key `swiftdrop_auth_redirect`.
   - **Second Pass (Post-Callback):** After successful redemption at `/auth/google/callback`, the frontend retrieves the item from `sessionStorage`, immediately removes it (`sessionStorage.removeItem("swiftdrop_auth_redirect")`), and passes it through `sanitizeRedirectTarget(...)` once more before invoking `router.push()`.
3. **Sanitization Rules (`sanitizeRedirectTarget`):**
   - Must start with a single forward slash `/`.
   - Must reject protocol-relative destinations (`//`) and Windows directory separators (`/\`).
   - Must reject CRLF or control characters (`[\x00-\x1F\x7F]`).
   - Must parse against a dummy base origin (`http://localhost`) and verify that `parsed.origin` remains `http://localhost` and `parsed.pathname` starts with `/`.
4. **Safe Default:** If the stored path is missing, empty, or fails sanitization, navigation defaults safely to `/customer` (or `/` if unauthenticated).

### 6. Account Identity and Linking Rules

1. **Subject Matching:** External identities are resolved strictly by the tuple `(provider = 'GOOGLE', subject = sub)`.
2. **No Automatic Email Linking:** If a Google identity does not have an existing link in `external_identities`, but `users.email` matches an existing account, the login is rejected with `conflict_email_exists` (HTTP 409). Automatic linking is prohibited to prevent account takeover.
3. **New Customer Creation:** If no link exists and the email is unused, a new `User` is created with role `CUSTOMER`, `password_hash = null`, `enabled = true`, and a link in `external_identities`.
4. **Authenticated Account Linking:** Linking an existing local account to a Google account requires an authenticated in-app confirmation flow, which is out of scope for this decision and deferred to a future enhancement.

### 7. Database Storage and Opportunistic Cleanup

The `identity` module owns the exchange code table via a Flyway migration (`backend/src/main/resources/db/migration/identity/`):

```sql
CREATE TABLE google_login_codes (
    code_hash   VARCHAR(64)  PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ  NULL
);

CREATE INDEX idx_google_login_codes_expires_at ON google_login_codes (expires_at);
```

#### Bounded Opportunistic Cleanup
To keep the database clean without background daemon overhead:
- **No Scheduled Background Task:** We reject adding `@EnableScheduling` or periodic cron tasks solely for cleaning up login codes. Background threads add unnecessary memory overhead, complicate automated tests, and create concurrency issues across multiple backend replicas.
- **Opportunistic Batch Purge:** During exchange code issuance or redemption, the backend executes a lightweight, bounded deletion:
  ```sql
  DELETE FROM google_login_codes
  WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'
     OR (used_at IS NOT NULL AND used_at < CURRENT_TIMESTAMP - INTERVAL '1 hour');
  ```
- Because codes expire in 60 seconds and code generation is low-frequency, opportunistic deletion keeps table size small and bounded with negligible performance impact.

### 8. Configuration

No secrets are exposed. The required environment variables are:

| Variable | Environment | Description |
|---|---|---|
| `SWIFTDROP_GOOGLE_CLIENT_ID` | Backend (`.env`) | Google OAuth Web Application Client ID |
| `SWIFTDROP_GOOGLE_CLIENT_SECRET` | Backend (`.env`) | Google OAuth Client Secret |
| `SWIFTDROP_FRONTEND_BASE_URL` | Backend (`.env`) | Base URL of frontend (default: `http://localhost:3000`) |
| `SWIFTDROP_CORS_ALLOWED_ORIGINS` | Backend (`.env`) | Must include frontend origin for `/api/**` calls |
| `NEXT_PUBLIC_ENABLE_GOOGLE_LOGIN` | Frontend (`.env.local`) | Feature flag (`true` / `false`) for Google button |
| `NEXT_PUBLIC_API_BASE_URL` | Frontend (`.env.local`) | Backend API base URL (default: `http://localhost:8080`) |

---

## Alternatives Considered

1. **Passing Destination in OAuth `state` or Redirect URL:**
   - *Why rejected:* Echoing URLs through third-party OAuth state or backend redirect query parameters widens the open-redirect attack surface and requires stateful encryption or backend URL allowlisting. `sessionStorage` confines the navigation state entirely within the client origin.
2. **Scheduled Cleanup Background Task:**
   - *Why rejected:* Introduces unnecessary scheduler infrastructure, timer threads, and test mocking complexity for a transient 60-second code table. Opportunistic cleanup achieves the same storage bounds with zero background overhead.
3. **Return JWT in URL Fragment (`#access_token=...`):**
   - *Why rejected:* The fragment is accessible to client scripts and browser extensions, remains in browser history, and cannot be revoked or controlled once delivered. Passing credentials via URLs violates project security guidelines.
4. **Return JWT directly in HTTP response body on callback:**
   - *Why rejected:* Current implementation leaves the browser displaying raw JSON on the backend port (`http://localhost:8080/login/oauth2/code/google`), breaking frontend SPA flow.
5. **Automatic Account Linking by Email:**
   - *Why rejected:* High security risk. If a malicious user registers an account with a victim's email before the victim signs in with Google, or if an email provider reassigns an email, accounts could be hijacked.

---

## Consequences

### Positive
- Tokens are never exposed in browser URLs, server logs, or referrer headers.
- Safe post-login navigation without exposing open redirect vulnerabilities on either backend or frontend.
- Zero background scheduler overhead via bounded opportunistic cleanup.
- Atomic single-use semantics prevent code replay and race conditions.
- Strict allowlist of error codes prevents information disclosure while providing clear user feedback.

### Negative / Trade-offs
- Requires an additional network hop (`POST /api/v1/auth/google/exchanges`) by the frontend before reaching the authenticated state.
- If a client network disconnects after the code has been marked `used_at` but before the response is received, the code cannot be reused; the user must log in again. (This is a deliberate security trade-off to preserve strict single-use semantics).
- Requires a new database table (`google_login_codes`).

---

## Follow-up Work (SD-104 Implementation Sequence)

Once this ADR is reviewed and accepted by the team:
1. **Database:** Add Flyway migration `V202610...__identity_create_google_login_codes.sql`.
2. **Backend Domain & Repository:** Implement `GoogleLoginCode` entity/record and atomic redemption repository query with opportunistic cleanup.
3. **Backend Service:** Refactor `GoogleLoginResponses` to generate codes, record SHA-256 hashes, and redirect to frontend callback on success (`?code=...`) and failure (`?error=...`).
4. **Backend Endpoint:** Implement `POST /api/v1/auth/google/exchanges` in `identity.web.AccessTokenController`.
5. **Frontend Callback Route:** Implement `frontend/app/auth/google/callback/page.tsx` with code exchange, allowlisted error display, and double-sanitized `sessionStorage` post-login navigation.
6. **Tests:** Update `GoogleLoginApiTest.java` to verify:
   - Successful redirection with 32-byte code.
   - Code exchange returning valid JWT.
   - Replay attempt returning 401.
   - Expired code (> 60s) returning 401.
   - Concurrent redemption race condition (only one succeeds).
   - Rejections (403, 409) redirecting with standard error query parameters.
