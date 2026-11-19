# PR #160 – Firebase Cloud Messaging: Deep Integration Audit & Fix Plan

**Pull Request:** https://github.com/ShenSandaru/SwiftDrop/pull/160  
**Repository:** `ShenSandaru/SwiftDrop`  
**Audit Date:** 2026-11-19  
**Audit Mode:** Read-Only Deep Integration Audit  
**Verdict:** **SAFE AFTER FIXES**

---

## 1. Latest PR Metadata

| Field | Value |
|---|---|
| **PR Number** | `#160` |
| **Title** | `feat(notification): add Firebase Cloud Messaging backend foundation` |
| **Author** | `sewmini12` (SEWMINI KAVINDYA) |
| **State** | `OPEN` (not draft, not merged, not closed) |
| **Mergeability** | `CONFLICTING` (`mergeStateStatus: DIRTY`, `reviewDecision: REVIEW_REQUIRED`) |
| **Source Branch** | `feat/dev-notifications` |
| **Source HEAD SHA** | `87fbaf19001c36aeddcdd1e0b7bb9fd538cda736` (2026-11-18) |
| **Target Branch** | `dev` |
| **Latest `origin/dev` SHA** | `cd5ed600f5296d1a02b623b241ae3ed5e5987808` (`Merge pull request #162 from ShenSandaru/dev`) |
| **Changed Files** | `29` files (`+4,016` / `-736`, including `frontend/package-lock.json` `+2,513` / `-729`) |
| **Review Comments** | `0` comments, `0` reviews |
| **Local Workspace State** | On `feat/dev-notifications` (`87fbaf1`) with an in-progress merge of `dev` (`cd5ed60`) and 1 unresolved conflict: `UU backend/src/test/java/com/swiftdrop/MigrationConventionTest.java` (preserved untouched) |

---

## 2. Actual Implementation Summary

> **Scope Discrepancy:** The PR description states that `NotificationEventProcessor` integration and frontend Firebase/service-worker support are *"planned for subsequent implementation"*, but **both are already implemented in the PR diff**.

- **Backend (`notification`):**
  - `V202611181000__notification_create_push_tokens.sql`: creates `notification_push_tokens` (`id`, `user_id`, `token` UNIQUE, `platform`, `is_active`, `created_at`, `updated_at`, `last_used_at`) + index `ix_notification_push_tokens_user_active`.
  - `NotificationPushToken`, `NotificationPushTokenRepository`, `PushTokenService`, `PushTokenController` (`POST/DELETE /api/v1/notifications/push-tokens`).
  - `PushNotificationSender`, `MockPushNotificationSender` (default), `FcmPushNotificationSender` (`firebase-admin:9.4.3`), `FcmProperties`.
  - `NotificationEventProcessor.dispatchPushChannel()` integrated into parcel status event processing.
- **Frontend (`frontend/`):**
  - `firebase` (`^13.3.0`) added to `package.json` / `package-lock.json`.
  - `frontend/lib/firebase.ts`, `frontend/lib/push-notifications.ts`, `frontend/public/firebase-messaging-sw.js`.
  - Foreground FCM listener (`onMessage`), deduplication, and toast in `frontend/lib/notification-context.tsx`; logout cleanup in `auth-context.tsx`; push toggle UI in `frontend/app/notifications/page.tsx`.

---

## 3. Backend Integration Assessment

- **Dispatch Point & Isolation:** `dispatchPushChannel(event, user, content)` runs after `IN_APP`, `EMAIL`, and `SMS` in `NotificationEventProcessor`.
- **Idempotency:** Uses `recordRepository.insertIfAbsent(...)` for `NotificationChannel.PUSH` (`PENDING` -> `SENT`/`FAILED`), preventing duplicate records on redelivery.
- **Multi-Device & Error Handling:** Iterates over `findByUserIdAndIsActiveTrue(user.id())` with per-token `try/catch (RuntimeException)` so one failing device token does not block other devices or break `IN_APP`/`EMAIL`/`SMS`.
- **Invalid Tokens:** `FcmPushNotificationSender` maps `MessagingErrorCode.UNREGISTERED` to `PushSendResult.INVALID_TOKEN`, which calls `token.deactivate(now)` and saves.
- **Users Without Tokens:** Returns immediately when `activeTokens` is empty (`0` records inserted, no errors).
- **Backward Compatibility:** `NotificationEventProcessor` retains overloaded constructors for existing tests and guards `if (pushSender == null || pushTokenRepository == null) return;`.

---

## 4. Firebase Configuration Assessment

- **Default Provider:** `MockPushNotificationSender` uses `@ConditionalOnProperty(name = "swiftdrop.notification.push.provider", havingValue = "mock", matchIfMissing = true)`. App starts cleanly without Firebase credentials.
- **FCM Provider:** `FcmPushNotificationSender` activates only when `swiftdrop.notification.push.provider=fcm`. Fails fast with a clear `IllegalStateException` if `SWIFTDROP_FIREBASE_SERVICE_ACCOUNT_PATH` is blank or unreadable.
- **Singleton Initialization:** Synchronized `FirebaseApp.getApps()` check ensures `FirebaseApp.initializeApp()` runs once.
- **Credential Hygiene:** No service account JSON files are committed; `.gitignore` updated.
- **Gap (P3):** Invalid provider values (neither `mock` nor `fcm`) leave no `PushNotificationSender` bean, causing `NoSuchBeanDefinitionException` at startup (acceptable fail-fast, but should be documented).

---

## 5. Push Token & Database Assessment

- **Auth & Ownership:** `PushTokenController` enforces `@PreAuthorize("isAuthenticated()")`, extracts `userId` from JWT (`Callers.userId(jwt)`), and scopes `DELETE` to `(token, userId)` via `deactivateTokenForUser`.
- **Shared-Browser Reassignment & Idempotency:** `PushTokenService.registerToken()` looks up by `token` (`UNIQUE`); if found, calls `reassignToUser(userId, platform, now)` and reactivates (`isActive = true`).
- **Platform Validation:** `@Size(max = 20)` on request; defaults blank/null to `"WEB"`.
- **Flyway Compatibility:**
  - `V202611181000__notification_create_push_tokens.sql` follows naming conventions and has **no version collision** with `dev`'s latest `V202611171400__identity_add_hub_id_to_users.sql`.
  - **Confirmed Failure (P2):** `MigrationConventionTest.java` conflicts with `dev` (`V202611171300`, `V202611171400`).
  - **Confirmed Failure (P2):** `DatabaseMigrationIT.java` was **not updated** to include `V202611181000__notification_create_push_tokens.sql` and the updated migration count.

---

## 6. Frontend & Service-Worker Assessment

- **SSR & Config Safety:** `firebase.ts` guards `typeof window === "undefined"`, checks `isFirebaseConfigured()`, and reuses `getApps()`. Missing env vars safely disable push without breaking the app.
- **Registration & Logout:** `push-notifications.ts` registers `/firebase-messaging-sw.js?...`, waits for activation (`waitForActiveServiceWorker`), calls `getToken` + `POST /api/v1/notifications/push-tokens`, and cleans up on logout (`cleanupPushOnLogout()`).
- **Foreground & Background:** `notification-context.tsx` listens via `onMessage` with `seenMessageIdsRef` deduplication; `firebase-messaging-sw.js` avoids duplicate display when `payload.notification` is present and routes clicks to `/notifications?parcelId=...&trackingNumber=...` (preserving PR #157 role-based navigation).
- **Version Mismatch (P3):** `frontend/package.json` uses `firebase@^13.3.0`, whereas `frontend/public/firebase-messaging-sw.js` imports Firebase compat scripts `11.10.0` from `gstatic.com`. These should be aligned to the same major/minor version to avoid subtle messaging incompatibilities.

---

## 7. Security & Privacy Assessment

- **No Secret Exposure:** Service account credentials stay outside Git; `PushTokenResponse` omits raw FCM tokens; `FcmPushNotificationSender` logs only `messageId` and `errorCode`.
- **No IDOR:** `userId` is always taken from JWT; `DELETE` checks `(token, userId)`.
- **Payload Privacy:** FCM `data` contains only `type`, `parcelId`, and `trackingNumber` — sufficient for navigation without leaking addresses, phone numbers, or financial data.
- **Minor Null-Safety Gap (P4):** `PushTokenController` uses `@Autowired(required = false)` for `PushTokenService` without a null check before invoking it.

---

## 8. Existing Notification Channel Compatibility

| Channel | Required Behavior | PR #160 Impact |
|---|---|---|
| **IN_APP** | Existing behavior intact | **Intact** — unchanged persistence & polling |
| **EMAIL** | Existing delivery & Resend (PR #157) intact | **Intact** — unchanged `EmailNotificationSender` / `ResendEmailNotificationSender` |
| **SMS** | Existing mock SMS intact | **Intact** — unchanged `SmsNotificationSender` |
| **PUSH** | Independent Firebase delivery | **Added** — runs independently after existing channels; failures isolated per token |

---

## 9. Parcel Lifecycle & Portal Compatibility

- **Zero changes** to `identity`, `pricing`, `delivery`, `dispatch`, `merchant`, `tracking`, or `assistant`.
- Customer, Merchant, Admin, Dispatcher, and Courier portals, parcel booking, tracking, courier assignment, hub management, walk-in intake (PR #159), delivery attempts, and return workflows are unaffected.

---

## 10. Dependency & Deployment Assessment

- **Backend:** `com.google.firebase:firebase-admin:9.4.3` (Java 21 compatible; Spring Boot 4 compatible).
- **Frontend:** `firebase: ^13.3.0` (Next.js / Node compatible; modular tree-shaken imports).
- **Approval & Docs (P3):** Per `AGENTS.md`, adding external dependencies (`firebase-admin`, `firebase`) requires team approval. `.env.example` does not yet document `SWIFTDROP_NOTIFICATION_PUSH_PROVIDER`, `SWIFTDROP_FIREBASE_SERVICE_ACCOUNT_PATH`, or `NEXT_PUBLIC_FIREBASE_*` / `NEXT_PUBLIC_FIREBASE_VAPID_KEY`.

---

## 11. Current CI Results

- **GitHub Actions (`statusCheckRollup`):** Empty (`[]`) on HEAD `87fbaf19`. No CI checks have completed, and GitHub reports `CONFLICTING` / `DIRTY`.

---

## 12. Local Test Results

- **Not executed (Blocked by Unresolved Merge Conflict):** `MigrationConventionTest.java` has active conflict markers from an in-progress local merge of `dev` (`cd5ed60`). Per strict read-only audit rules, the conflict was preserved untouched and Maven/npm test suites were not run.

---

## 13. Confirmed Issues & Potential Risks

| Severity | Type | File / Location | Description & Minimum Fix |
|---|---|---|---|
| **P2 – High** | Confirmed | `backend/src/test/java/com/swiftdrop/DatabaseMigrationIT.java` | Not updated for `V202611181000__notification_create_push_tokens.sql`; `-Pintegration` will fail. **Fix:** Update expected migration count and script list. |
| **P2 – High** | Confirmed | `backend/src/test/java/com/swiftdrop/MigrationConventionTest.java` | Merge conflict against `dev` (`cd5ed60`). **Fix:** Merge `dev` and include `V202611171300`, `V202611171400`, and `V202611181000` in order. |
| **P2 – High** | Confirmed | GitHub Actions / Local workspace | No CI run on HEAD `87fbaf19` (`statusCheckRollup: []`) and local tests unverified. **Fix:** Run full backend + frontend verification after resolving conflicts. |
| **P3 – Medium** | Confirmed | `frontend/public/firebase-messaging-sw.js` vs `frontend/package.json` | Service worker loads Firebase `11.10.0` compat while `package.json` installs `firebase@^13.3.0`. **Fix:** Align SW compat script version with installed SDK. |
| **P3 – Medium** | Confirmed | PR #160 description & `.env.example` | PR description omits implemented `NotificationEventProcessor` + frontend FCM/SW changes; `.env.example` lacks Firebase env var placeholders. **Fix:** Update PR description and `.env.example`. |
| **P3 – Medium** | Potential | `backend/pom.xml`, `frontend/package.json` | New external dependencies (`firebase-admin`, `firebase`) added; confirm team approval per `AGENTS.md`. |
| **P4 – Low** | Confirmed | `backend/src/main/java/com/swiftdrop/notification/web/PushTokenController.java` | `@Autowired(required = false)` without null-guard on `pushTokenService`. **Fix:** Use constructor injection or return `503` if null. |

---

## 14. Minimum Required Fixes

1. Merge latest `dev` (`cd5ed60`) into `feat/dev-notifications` and resolve `MigrationConventionTest.java`.
2. Update `DatabaseMigrationIT.java` to assert the new total migration count and `notification/V202611181000__notification_create_push_tokens.sql`.
3. Align the Firebase SDK version in `frontend/public/firebase-messaging-sw.js` with `frontend/package.json`.
4. Document the new Firebase env vars in `.env.example` and update the PR #160 description to match the actual diff.

---

## 15. Recommended Order of Fixes

1. **Merge `dev` & resolve `MigrationConventionTest.java`** (unblocks compilation and CI).
2. **Update `DatabaseMigrationIT.java`** (fixes `-Pintegration`).
3. **Align `firebase-messaging-sw.js` SDK version** (prevents runtime SW mismatch).
4. **Update `.env.example` & PR description** (deployment clarity + dependency sign-off).
5. **Run full local + CI verification**.

---

## 16. Post-Fix Verification Checklist

- [ ] `cd backend; .\mvnw.cmd test` — 0 failures, 0 errors (including `MigrationConventionTest`, `NotificationPushDispatchTest`, `PushTokenControllerTest`, `FcmPushNotificationSenderTest`, `NotificationPushTokenTest`).
- [ ] `cd backend; .\mvnw.cmd verify -Pintegration "-Dit.test=DatabaseMigrationIT"` — passes with all migrations including `V202611181000`.
- [ ] `cd frontend; npm run lint; npx tsc --noEmit; npm run build` — 0 errors.
- [ ] GitHub Actions `CI / backend` and `CI / frontend` both `SUCCESS` on the updated HEAD.
- [ ] *(Later, with live Firebase credentials)* Verify browser permission prompt, `POST /api/v1/notifications/push-tokens`, foreground toast, background SW notification, click navigation, and logout token cleanup.

---

## 17. Final Merge Recommendation

### Verdict: **SAFE AFTER FIXES**

PR #160 is architecturally clean, properly isolated to `notification`, preserves `IN_APP`/`EMAIL`/`SMS` channels, and uses safe `mock` defaults. Once the `MigrationConventionTest` conflict, `DatabaseMigrationIT` expectation, SW version alignment, and CI verification are completed, it is safe to merge into `dev`.
