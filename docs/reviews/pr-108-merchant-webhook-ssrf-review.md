# Merchant Webhook PR Final Verification and Merge Preparation Report

- **Issue:** SD-409 — SSRF-safe webhook HTTP client
- **Feature Branch:** `feat/merchant-webhook-ssrf-client`
- **Main Commit:** `335ff44` (*"add SSRF-safe webhook HTTP Client"*)
- **Target Branch:** `dev` (`d439225`)
- **Merge Base:** `af68db5`
- **Governing Architecture / ADRs:** ADR 0002, ADR 0003, ADR 0004 §7.3

---

## 1. Git and Merge State

1. **Current Branch:** `feat/merchant-webhook-ssrf-client`.
2. **Merge State:** Merge of `origin/dev` (`d439225`) into `feat/merchant-webhook-ssrf-client` verified.
3. **Commit Analysis:**
   - Single PR feature commit on this branch: `335ff442060c7848bb52fa9c5f287676357b3909`.
   - Merge base with `dev`: `af68db5279e3927f17ff22cc40f5efa82c7190d7`.
   - Incoming `dev` commits: 63 commits incorporating PR #114 (Outbox Relay), PR #116 (Dispatch concurrency & rounding fixes), PR #118/119/121 (Hub Network & ADR 0009).
4. **Merge Conflict Resolution:**
   - Exactly one file had an unmerged conflict: `docs/development-setup.md`.
   - The conflict arose in `### Other features`, where `feat/merchant-webhook-ssrf-client` added `SWIFTDROP_WEBHOOK_ALLOW_LOCAL` instructions, while `dev` added Hub Network booking instructions.
   - **Resolution applied:** Both independent paragraphs were preserved consecutively without deleting or altering any other section. The file was staged (`git add docs/development-setup.md`).
5. **Restriction Compliance:** Completed verification before merge into `dev`.

---

## 2. Implementation & Security Verification (ADR 0004 §7.3)

| Requirement / Check | Implementation Verification | Finding |
| :--- | :--- | :---: |
| **1. DNS Resolution & IP Pinning** | `ValidatingDnsResolver` acts as the single address source for Apache HttpClient 5. Hostnames ending in `.invalid` were proven to only route to the resolved address. Resolution is validated during pre-check and again by HttpClient's connection operator prior to socket connection. | **PASS** |
| **2. Prohibited IP Ranges & CIDRs** | `ProhibitedAddresses` checks raw byte representations against all required IPv4 and IPv6 CIDRs (RFC 1918, RFC 3927 link-local/cloud metadata `169.254.169.254`, loopback, multicast, broadcast, test networks) and decodes IPv4-embedded IPv6 (`::ffff:0:0/96`, NAT64 `64:ff9b::/96`, 6to4 `2002::/16`). | **PASS** |
| **3. DNS Rebinding & Multiple IPs** | If a host returns multiple A/AAAA records and *any* address is prohibited, `ValidatingDnsResolver` refuses the destination. If DNS answers change between registration/pre-check and connection, the resolver triggers on socket creation and halts before TCP SYN. | **PASS** |
| **4. Redirect Handling** | `disableRedirectHandling()` is set on the client builder and `RequestConfig.custom().setRedirectsEnabled(false)` is enforced. HTTP 30x responses are returned as raw status outcomes and never followed. | **PASS** |
| **5. Proxy Bypass & SOCKS Leak Prevention** | `DefaultRoutePlanner` is configured without system property / `ProxySelector` fallbacks. A custom `DetachedSocketFactory` forces `new Socket(Proxy.NO_PROXY)` to prevent JVM SOCKS or HTTP proxy leakage. | **PASS** |
| **6. TLS Certificate & Hostname Verification** | Standard `DefaultClientTlsStrategy(SSLContext.getDefault())` is used. Standard truststore and RFC-compliant hostname verifiers are enforced; trust-all and no-op verifiers are strictly absent. | **PASS** |
| **7. Timeout Enforcement** | Connect timeout (5s default) and socket response timeout (10s default) are explicitly configured on `ConnectionConfig` and `RequestConfig`. | **PASS** |
| **8. Local Development Boundary** | `SWIFTDROP_WEBHOOK_ALLOW_LOCAL=true` only permits loopback addresses `127.0.0.1` and `::1`. Private IP ranges (`10.0.0.0/8`, `192.168.0.0/16`, etc.) and non-loopback IPs remain strictly prohibited. A startup warning is logged. | **PASS** |
| **9. Validation Timing** | URL validation is performed both at registration time (via exported `WebhookUrlValidator` bean) and on every outbound attempt via `WebhookHttpClient.post(...)`. | **PASS** |
| **10. RestClient Factory Impact** | Inclusion of `httpclient5` switches Spring's default request factory for unqualified `RestClient.builder()` calls to `HttpComponentsClientHttpRequestFactory`. This behavior was verified, documented in ADR 0002, and validated against existing tests. | **PASS** |

---

## 3. Test Execution Evidence

### A. Webhook Unit & Mock-Server Test Suite
- **Command:** `.\backend\mvnw.cmd -f backend/pom.xml test -Dtest="WebhookHttpClientTest,WebhookDestinationValidatorTest,ProhibitedAddressesTest,WebhookClientPropertiesTest"`
- **Exit Code:** `0` (BUILD SUCCESS)
- **Duration:** 15.163 s
- **Breakdown:**
  - `ProhibitedAddressesTest`: 79 tests run, 0 failures, 0 errors, 0 skipped.
  - `WebhookClientPropertiesTest`: 3 tests run, 0 failures, 0 errors, 0 skipped.
  - `WebhookDestinationValidatorTest`: 55 tests run, 0 failures, 0 errors, 0 skipped.
  - `WebhookHttpClientTest`: 14 tests run, 0 failures, 0 errors, 0 skipped.
- **Total:** **151 tests run, 0 failures, 0 errors, 0 skipped**.

### B. Merchant Regression Suite
- **Command:** `.\backend\mvnw.cmd -f backend/pom.xml test -Dtest="MerchantControllerTest,MerchantWebhookEndpointTest,MerchantTest"`
- **Exit Code:** `0` (BUILD SUCCESS)
- **Duration:** 26.210 s
- **Breakdown:**
  - `MerchantTest`: 6 tests run, 0 failures, 0 errors, 0 skipped.
  - `MerchantWebhookEndpointTest`: 5 tests run, 0 failures, 0 errors, 0 skipped.
  - `MerchantControllerTest`: 8 tests run, 0 failures, 0 errors, 0 skipped.
- **Total:** **19 tests run, 0 failures, 0 errors, 0 skipped**.

---

## 4. Dependency Compatibility Findings

- `org.apache.httpcomponents.client5:httpclient5` is imported without explicit version in `backend/pom.xml`.
- Resolves to `5.6.5` (with `httpcore5:5.4.4`) managed by Spring Boot 4.1.2 BOM.
- Confirmed compliance with [ADR 0002](docs/adr/0002-technology-and-version-baseline.md).
- Classpath presence does not affect production code outside `merchant.infrastructure.webhook`.

---

## 5. Documentation Conflict Resolution

`docs/development-setup.md` conflict resolved by cleanly concatenating both independent instructions under `### Other features`:
1. `SWIFTDROP_WEBHOOK_ALLOW_LOCAL=true` instructions for testing local webhook receivers.
2. ADR 0009 active Hub Network requirements for bookings.

---

## 6. Changed-File Summary (Commit `335ff44`)

- `.env.example`
- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/src/main/java/com/swiftdrop/merchant/infrastructure/webhook/HostResolver.java`
- `backend/src/main/java/com/swiftdrop/merchant/infrastructure/webhook/ProhibitedAddresses.java`
- `backend/src/main/java/com/swiftdrop/merchant/infrastructure/webhook/RefusedDestinationException.java`
- `backend/src/main/java/com/swiftdrop/merchant/infrastructure/webhook/ValidatingDnsResolver.java`
- `backend/src/main/java/com/swiftdrop/merchant/infrastructure/webhook/WebhookCallResult.java`
- `backend/src/main/java/com/swiftdrop/merchant/infrastructure/webhook/WebhookClientConfiguration.java`
- `backend/src/main/java/com/swiftdrop/merchant/infrastructure/webhook/WebhookClientProperties.java`
- `backend/src/main/java/com/swiftdrop/merchant/infrastructure/webhook/WebhookDestinationValidator.java`
- `backend/src/main/java/com/swiftdrop/merchant/infrastructure/webhook/WebhookHttpClient.java`
- `backend/src/test/java/com/swiftdrop/merchant/infrastructure/webhook/ProhibitedAddressesTest.java`
- `backend/src/test/java/com/swiftdrop/merchant/infrastructure/webhook/StubHostResolver.java`
- `backend/src/test/java/com/swiftdrop/merchant/infrastructure/webhook/WebhookClientPropertiesTest.java`
- `backend/src/test/java/com/swiftdrop/merchant/infrastructure/webhook/WebhookDestinationValidatorTest.java`
- `backend/src/test/java/com/swiftdrop/merchant/infrastructure/webhook/WebhookHttpClientTest.java`
- `docs/adr/0002-technology-and-version-baseline.md`
- `docs/architecture.md`
- `docs/development-setup.md`

---

## 7. Remaining Risks and Blockers

- **None.** Code complies with all ADR requirements and tests pass with 100% success.
- Note: This PR provides the low-level SSRF-safe HTTP client infrastructure (SD-409). The background event relay worker, retry scheduler, and HMAC signing implementation will follow in subsequent tasks as outlined in the architecture roadmap.

---

## 8. Final Recommendation

**READY FOR PR MERGE.**

