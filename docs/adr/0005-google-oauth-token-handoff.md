# 0005. Google OAuth token handoff

- Status: Proposed (NOT ACCEPTED)
- Date: 2026-10-26

> [!IMPORTANT]
> **PROPOSED — NOT ACCEPTED**. This document is a draft architecture decision record prepared for team review under task SD-001 / SD-104. It has not been approved and does not authorize implementation.

## Context

SwiftDrop supports customer registration and login with local credentials (email and password), and must support "Login with Google" (requirement CUS-1, demo requirement DEM-1) using OpenID Connect (OIDC).

The backend already implements the core OIDC flow using Spring Security's `oauth2Login` (active when `SWIFTDROP_GOOGLE_CLIENT_ID` is set). The browser initiates authentication at `GET /oauth2/authorization/google` and Google redirects back to `GET /login/oauth2/code/google`. Spring Security validates the OAuth `state` parameter, exchanges the authorization code with Google, and verifies the signed OIDC ID token. The identity module's `GoogleSignInService` resolves the validated identity to a `User` entity.

However, the callback mechanism currently returns the application JWT as raw JSON (`AccessTokenResponse`) directly from `GET /login/oauth2/code/google`. Because the callback is a full browser navigation, this leaves the user looking at raw JSON on the backend origin (`http://localhost:8080`) rather than returning them to the Next.js frontend application (`http://localhost:3000`).

A mechanism is needed to hand off the authenticated session from the backend callback to the frontend single-page application without introducing security vulnerabilities.

### Security and Architectural Constraints

1. **No tokens in URLs:** Placing the application JWT into a query parameter or URL fragment (e.g. `SWIFTDROP_FRONTEND_BASE_URL/callback#token=...`) exposes bearer tokens to browser history, intermediate proxy logs, server access logs, and HTTP `Referer` headers.
2. **Stateless backend:** The backend application REST API is stateless; authentication uses bearer JWTs in the `Authorization` header. Sessions must not be retained after the OAuth exchange completes.
3. **Open redirect prevention:** Callback redirections must not enable external phishing redirects or open redirects.
4. **Account takeover prevention:** Google accounts must never be linked automatically to existing local accounts solely by matching email address.
5. **Architectural consistency:** The identity module owns authentication and credential tables (ADR 0001, ADR 0003). All database schema changes must be versioned Flyway migrations.

---

## Decision

We propose the **Backend-Mediated Authorization Code Exchange Flow (D-02)** for Google OAuth token handoff:

```
[Browser / Frontend]                [Backend API]                  [Google OIDC]
        |                                 |                              |
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
        |                                 |-- Generate 32-byte code      |
        |                                 |-- Persist SHA-256(code)      |
        |<-- 8. 302 Redirect with code ---|                              |
        |    ${FRONTEND_BASE_URL}/auth/google/callback?code=...          |
        |                                 |                              |
        |--- 9. POST /api/v1/auth/google/exchanges { code } ------------>|
        |                                 |-- Verify & mark used         |
        |                                 |-- Issue application JWT      |
        |<-- 10. 200 OK { accessToken } --|                              |
        |                                 |                              |
```

### 1. OAuth Handshake and Callback Redirection

1. The backend initiates and completes the OIDC handshake with Google using Spring Security's `oauth2Login`.
2. Upon successful authentication, the backend generates a cryptographically secure, high-entropy exchange code.
3. The backend sets HTTP response status 302 and redirects the user's browser to the frontend callback page:
   ```
   ${SWIFTDROP_FRONTEND_BASE_URL}/auth/google/callback?code=<raw_code>
   ```
4. If an optional, sanitized post-login internal destination was requested (e.g. `/track?code=SWD-1234`), it is passed to the frontend callback via a sanitized `redirect` parameter:
   ```
   ${SWIFTDROP_FRONTEND_BASE_URL}/auth/google/callback?code=<raw_code>&redirect=<sanitized_path>
   ```
5. The application JWT is **never** sent via redirection URLs.

### 2. Exchange Code Security

1. **Entropy:** The exchange code is generated using `java.security.SecureRandom`, containing at least 256 bits of entropy (32 random bytes), formatted as a URL-safe Base64 string without padding.
2. **Storage:** The raw exchange code is never persisted in plain text. The backend computes the SHA-256 digest (64-character lowercase hexadecimal string) and persists only the hash in the database.
3. **Lifetime:** The exchange code expires after 60 seconds (`expires_at = now() + INTERVAL '60 seconds'`).
4. **Single-Use and Atomic Redemption:** The code can be redeemed exactly once. When the frontend exchanges the code, the backend performs an atomic conditional update:
   ```sql
   UPDATE google_login_codes
   SET used_at = CURRENT_TIMESTAMP
   WHERE code_hash = :codeHash
     AND used_at IS NULL
     AND expires_at > CURRENT_TIMESTAMP
   RETURNING user_id;
   ```
   If no row is updated (code not found, expired, or already used), the request is rejected with HTTP `401 Unauthorized` and an RFC 9457 Problem Details body (`"Invalid or expired exchange code"`). Concurrent requests with the same code will result in exactly one successful redemption.

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
  - `400 Bad Request`: Missing or malformed code payload.
  - `401 Unauthorized`: Invalid, expired, or previously redeemed code.

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

Under no circumstances are internal stack traces, database exceptions, or raw provider error descriptions forwarded in redirect query parameters.

### 5. Safe Post-Login Navigation

1. Post-login internal paths (such as `/customer` or `/track?code=...`) may be preserved across the Google OAuth flow by encoding the target into the OAuth `state` parameter or reading it from client storage.
2. The frontend callback page (`/auth/google/callback`) must sanitize all post-login destinations using the project's standard redirect sanitization helper (`sanitizeRedirectTarget`):
   - Must start with `/`.
   - Must reject `//` and `/\`.
   - Must reject CRLF or control characters (`[\x00-\x1F\x7F]`).
   - Must evaluate origin against `http://localhost` dummy base to guarantee the destination remains within the frontend application.
   - Any invalid, external, or malformed value defaults safely to `/customer` (or `/` if unauthenticated).

### 6. Account Identity and Linking Rules

1. **Subject Matching:** External identities are resolved strictly by the tuple `(provider = 'GOOGLE', subject = sub)`.
2. **No Automatic Email Linking:** If a Google identity does not have an existing link in `external_identities`, but `users.email` matches an existing account, the login is rejected with `conflict_email_exists` (HTTP 409). Automatic linking is prohibited to prevent account takeover.
3. **New Customer Creation:** If no link exists and the email is unused, a new `User` is created with role `CUSTOMER`, `password_hash = null`, `enabled = true`, and a link in `external_identities`.
4. **Authenticated Account Linking:** Linking an existing local account to a Google account requires an authenticated in-app confirmation flow, which is out of scope for this decision and deferred to a future enhancement.

### 7. Database Storage and Cleanup

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

#### Cleanup Strategy
Because exchange codes expire in 60 seconds and the volume is modest:
- **Opportunistic cleanup:** During code redemption or issuance, delete codes expired more than 5 minutes ago (`DELETE FROM google_login_codes WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'`).
- Alternatively, a lightweight scheduled task in `identity` runs every 15 minutes to delete expired records.

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

1. **Return JWT in URL Fragment (`#access_token=...`):**
   - *Why rejected:* The fragment is accessible to client scripts and browser extensions, remains in browser history, and cannot be revoked or controlled once delivered. Passing credentials via URLs violates project security guidelines.
2. **Return JWT directly in HTTP response body on callback:**
   - *Why rejected:* This is the current implementation. Because the callback is initiated by a browser redirect from Google, the user sees raw JSON text in their browser window on the backend port. It breaks single-page application integration.
3. **Session Cookie + Backend-for-Frontend (BFF):**
   - *Why rejected:* SwiftDrop is an API-first modular monolith with a separate Next.js presentation frontend. Introducing server-side sessions, cookies, and CSRF tokens across origins would violate ADR 0001 and ADR 0002.
4. **Automatic Account Linking by Email:**
   - *Why rejected:* High security risk. If a malicious user registers an account with a victim's email before the victim signs in with Google, or if an email provider reassigns an email, accounts could be hijacked.

---

## Consequences

### Positive
- Tokens are never exposed in browser URLs, server logs, or referrer headers.
- Seamless user experience: user clicks "Sign in with Google", authenticates, and returns cleanly to the frontend dashboard.
- Clear separation of concerns: Spring Security handles OIDC; frontend handles UI and token storage.
- Atomic single-use semantics prevent code replay and race conditions.
- Strict allowlist of error codes prevents information disclosure while providing clear feedback.

### Negative / Trade-offs
- Requires an additional network hop (`POST /api/v1/auth/google/exchanges`) by the frontend before reaching the authenticated state.
- Requires a new database table (`google_login_codes`) and a periodic cleanup policy.
- Local development requires configuring `SWIFTDROP_FRONTEND_BASE_URL` and a Google Cloud OAuth client.

---

## Follow-up Work (SD-104 Implementation Sequence)

Once this ADR is reviewed and accepted by the team:
1. **Database:** Add Flyway migration `V202610...__identity_create_google_login_codes.sql`.
2. **Backend Domain & Repository:** Implement `GoogleLoginCode` entity/record and atomic redemption repository query.
3. **Backend Service:** Refactor `GoogleLoginResponses` to generate codes, record SHA-256 hashes, and redirect to frontend callback on success and failure.
4. **Backend Endpoint:** Implement `POST /api/v1/auth/google/exchanges` in `identity.web.AccessTokenController`.
5. **Frontend Callback Route:** Implement `frontend/app/auth/google/callback/page.tsx` with code exchange, error display, and sanitized post-login navigation.
6. **Tests:** Update `GoogleLoginApiTest.java` to verify:
   - Successful redirection with 32-byte code.
   - Code exchange returning valid JWT.
   - Replay attempt returning 401.
   - Expired code (> 60s) returning 401.
   - Concurrent redemption race condition (only one succeeds).
   - Rejections (403, 409) redirecting with standard error query parameters.
