# PR #161 – Deep Integration & Security Audit (Read-Only)

**Pull Request:** https://github.com/ShenSandaru/SwiftDrop/pull/161  
**Repository:** `ShenSandaru/SwiftDrop`  
**Audit Date:** 2026-11-19  
**Audit Mode:** Read-Only Deep Integration & Security Audit  
**Verdict:** **SAFE TO MERGE**

---

## 1. PR Overview, Branches & Repository State

| Field | Value |
|---|---|
| **PR Number** | `#161` |
| **Title** | `feat(security): allow API key auth for pricing and zones` |
| **Author** | `AsiriHarischandra` (Asiri Harischandra) |
| **State** | `OPEN` (not draft, not merged, not closed) |
| **Source Branch** | `e-com` @ `fe9ae56d3ef06d18dcd2d5181275c36d911000d7` (`Merge branch 'dev' into e-com`) |
| **Target Branch** | `dev` @ `a946d1dbf3b85538ae6dbbccb3b91f9b31e9d289` (`Merge pull request #163 from ShenSandaru/dev`) |
| **Mergeability** | `MERGEABLE` (`0` conflicts; `mergeStateStatus: BLOCKED` only due to `REVIEW_REQUIRED`) |
| **Review Comments** | `0` comments, `0` reviews |
| **Local Workspace State** | On `e-com` @ `fe9ae56`, clean working tree (`0` modified, staged, or conflicted source files) |
| **Description vs Actual Diff** | **Exact match** — every changed file and behavior matches the PR description |

### Commits Included
1. `dd4c315` — `feat(security): allow API key auth for pricing and zones`
2. `91537a0` — `Merge branch 'dev' into e-com`
3. `56976cf` — `Update PricingAndZonesSecurityApiTest.java`
4. `fe9ae56` — `Merge branch 'dev' into e-com` (synced with latest `dev` `a946d1d`)

### GitHub Actions CI Status (HEAD `fe9ae56`)
- **`CI / backend`:** **SUCCESS** (`2026-11-19T16:30:18Z`)
- **`CI / frontend`:** **SUCCESS** (`2026-11-19T16:29:33Z`)

---

## 2. Changed Modules & Files (`6` files, `+533` / `-1`)

- **`config` module (backend):**
  - `backend/src/main/java/com/swiftdrop/config/AmbiguousCredentialsFilter.java` (`+41`, NEW)
  - `backend/src/main/java/com/swiftdrop/config/SecurityConfiguration.java` (`+36`)
  - `backend/src/test/java/com/swiftdrop/config/PricingAndZonesSecurityApiTest.java` (`+412`, NEW)
- **Documentation:**
  - `docs/adr/0008-merchant-api-key-authentication.md` (`+32`)
  - `docs/architecture.md` (`+8`)
  - `docs/merchant-integration-guide.md` (`+4` / `-1`)
- **Unchanged:** Zero changes to `frontend/`, `pom.xml`, `package.json`, `application.properties`, Flyway migrations, or any business module (`identity`, `pricing`, `delivery`, `dispatch`, `tracking`, `merchant`, `notification`, `assistant`).

---

## 3. Spring Security Filter-Chain Analysis

| Order | Filter Chain | Matcher | Auth Mechanisms |
|---|---|---|---|
| `HIGHEST_PRECEDENCE` | Google OAuth2 chain | OAuth2 login routes | OAuth2 |
| `HIGHEST_PRECEDENCE + 1` | `merchantApiSecurity` | `/api/v1/merchant-orders/**` | `MerchantApiKeyAuthenticationFilter` only |
| `HIGHEST_PRECEDENCE + 2` | `pricingAndZonesSecurity` (**NEW**) | `POST /api/v1/price-quotes` OR `GET /api/v1/zones` | `AmbiguousCredentialsFilter` → `MerchantApiKeyAuthenticationFilter` → `BearerTokenAuthenticationFilter` |
| Default (`LOWEST_PRECEDENCE`) | `apiSecurity` | All other requests | JWT Resource Server only |

- **Exact Filter Ordering:** `AmbiguousCredentialsFilter` is added before `BearerTokenAuthenticationFilter`, and `MerchantApiKeyAuthenticationFilter` is added after `AmbiguousCredentialsFilter`. Execution order is strictly: `AmbiguousCredentialsFilter` → `MerchantApiKeyAuthenticationFilter` → `BearerTokenAuthenticationFilter` → `AuthorizationFilter`.
- **Dual Credentials:** Requests presenting both a non-blank `X-Api-Key` and `Authorization: Bearer ...` are rejected immediately with `400 Bad Request` (`application/problem+json`) before either authenticator runs.
- **SecurityContext Isolation:** `SessionCreationPolicy.STATELESS`, `requestCache` disabled, and `MerchantApiKeyAuthenticationFilter` uses `contextHolder.createEmptyContext()` — no cross-request leakage.
- **Invalid/Missing Credentials:** Invalid `X-Api-Key` returns uniform `401` (`WWW-Authenticate: ApiKey`); invalid/disabled/nonexistent JWT is rejected with `401` via `jwtAuthenticationConverter()` (`UserDirectory` check); missing credentials return `401`.

---

## 4. Authorization Boundaries & CORS

- **Authorities Assigned:** `MerchantApiKeyAuthenticationFilter` assigns **only** `MERCHANT_API_KEY` (`AuthenticatedMerchant.AUTHORITY`) — never `ROLE_ADMIN`, `ROLE_MERCHANT`, `ROLE_CUSTOMER`, `ROLE_DISPATCHER`, or `ROLE_COURIER`.
- **Admin & User Endpoint Protection:**
  - `GET /api/v1/zones?all=true` → Protected by `@PreAuthorize("hasRole('ADMIN')")` in `ZoneController.allZones()` → **rejected with `403 Forbidden`** for API-key callers.
  - `GET /api/v1/zones/{id}`, `POST /api/v1/zones`, `PUT /api/v1/zones/{id}`, `DELETE /api/v1/zones/{id}` → Do not match `pricingAndZonesSecurity`; fall through to `apiSecurity` + `@PreAuthorize("hasRole('ADMIN')")` → **rejected with `401`** for API-key callers.
  - `/api/v1/users/me` and all other JWT endpoints → Fall through to `apiSecurity` → **rejected with `401`** for API-key callers.
- **Non-Approved / Revoked Keys:** Rejected with uniform `401` via `MerchantAuthentication.authenticate(apiKey)`.
- **CORS & Browser Security:** `corsConfigurationSource` allows only `Authorization` and `Content-Type` (`allowCredentials = false`). `X-Api-Key` remains excluded, keeping API keys server-to-server only.

---

## 5. System, Database & API Compatibility

- **Database & Migrations:** None — `0` Flyway migrations, `0` entity/schema changes.
- **Existing Modules & Portals:** Zero impact on `identity`, `delivery`, `dispatch`, `tracking`, `notification` (`IN_APP`/`EMAIL`/Resend/`SMS`/FCM), `merchant` orders (`/api/v1/merchant-orders/**`), or `frontend/`.
- **API Contracts:** `PriceQuoteController` and `ZoneController` are unmodified; request/response schemas and status codes remain 100% backward-compatible for JWT callers.

---

## 6. Test Coverage Assessment (`PricingAndZonesSecurityApiTest.java`)

- Exercises the **real** Spring Security filter chains (`@SpringBootTest` + `MockMvc`, no mocked auth bypass).
- Covers valid/invalid/disabled-user JWTs, valid/unknown/malformed/revoked/pending/rejected/suspended merchant API keys, missing credentials (`401`), dual credentials (`400`, including valid Bearer + invalid API key), CORS preflight refusing `X-Api-Key`, and `GET /api/v1/zones?all=true` rejection for API keys.

---

## 7. Confirmed Issues & Potential Risks

- **Confirmed Defects:** **None.**
- **Low / Non-Blocking Notes:**
  1. A request with a valid Bearer token + **blank** `X-Api-Key:   ` header bypasses `AmbiguousCredentialsFilter` (`!apiKey.isBlank()` is false) and is rejected by `MerchantApiKeyAuthenticationFilter` (`apiKey != null`) with `401` instead of `400` (fail-closed, no security bypass).
  2. `PricingAndZonesSecurityApiTest` lists `@MockitoBean` repositories for JPA-excluded startup, so future PRs adding new repositories will need to mirror them there.

---

## 8. Final Verdict

### **SAFE TO MERGE**

PR #161 is tightly scoped, adheres to ADR 0008 and module boundaries, introduces no security, authorization, CORS, migration, or integration regressions, is synced with latest `dev` (`a946d1d`), and passes both `CI / backend` and `CI / frontend`. Ready for standard PR review approval and merge.
