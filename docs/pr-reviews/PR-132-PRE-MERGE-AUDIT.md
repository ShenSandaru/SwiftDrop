# Comprehensive Pre-Merge Audit: PR #132

**Pull Request:** [#132 - feat(notification): add merchant account status notifications](https://github.com/ShenSandaru/SwiftDrop/pull/132)  
**Target Branch:** `dev` (`68667ccb24c75a172e8e281ef4bfcf34c5b944f3`)  
**Source Branch:** `feat/dev-notifications` (`a96ffbf4fb00cfe71f223abe41a10ede74d6f3c6`)  
**Audit Date:** November 14, 2026  
**Auditor:** SwiftDrop Architecture & Quality Agent (Independent Audit)  

---

## Executive Summary

This audit assesses GitHub Pull Request #132 submitted by author **sewmini12 (SEWMINI KAVINDYA)**. While PR #132 is titled *"feat(notification): add merchant account status notifications"*, it actually introduces two tightly coupled capabilities:
1. **Backend Merchant Lifecycle Notifications:** Asynchronous, transactional event consumption (`MerchantStatusChanged`) generating non-parcel in-app notifications with idempotent database insertion.
2. **Full Frontend Notification Experience:** Global notification context (`NotificationContext`), persistent bell badge polling in the navigation header (`NotificationBell`), and a dedicated `/notifications` dashboard with tab filtering (All vs. Unread), paginated feeds, and bulk "mark all as read" actions.

### Final Verdict: `CHANGES REQUESTED` (Merge Blocked)

PR #132 cannot be safely merged into `dev` in its current state due to two blocking issues:
1. **Active Merge Conflict:** A 3-way merge conflict exists in `frontend/components/nav-header.tsx` caused by concurrent changes from PR #123 (`feat/user-profiles`, merged into `dev` via commit `6aafc99` / `68667cc`).
2. **Flyway Migration Convention Test Failure:** PR #132's `MigrationConventionTest.java` was branched from an older base commit (`4e3384a`) and hardcodes an assertion list of known migrations that omits PR #123's migrations (`V202611081400` and `V202611111330`). Merging without reconciling this test will immediately break the CI build on `dev`.

Beyond these two blockers, the core architectural implementation (event handling, transactional boundaries, idempotency, ADR 0003 updates, and frontend component modularity) is clean, secure, and follows SwiftDrop conventions.

---

## 1. PR Information & Metadata

| Field | Detail |
| :--- | :--- |
| **PR Number & Title** | `#132`: `feat(notification): add merchant account status notifications` |
| **Author** | `sewmini12` (SEWMINI KAVINDYA) |
| **Source Branch** | `feat/dev-notifications` |
| **Target Branch** | `dev` |
| **PR Status** | `OPEN` |
| **Head Commit SHA** | `a96ffbf4fb00cfe71f223abe41a10ede74d6f3c6` |
| **Target Branch SHA** | `68667ccb24c75a172e8e281ef4bfcf34c5b944f3` |
| **Merge Base SHA** | `4e3384a22d5d2271eb218d8591509cec8eee8152` |
| **Mergeability** | `CONFLICTING` (Cannot be automatically merged by GitHub) |
| **Review Status** | No formal review submitted yet (`reviews: []`) |
| **CI / Status Checks** | `[]` (Blocked on conflict) |
| **Total Changed Files**| 20 files (+1,603 lines, -13 lines) |

### Changed Files Breakdown

```text
Backend (13 files):
  - backend/src/main/java/com/swiftdrop/merchant/api/MerchantStatusChanged.java (MODIFIED)
  - backend/src/main/java/com/swiftdrop/merchant/application/MerchantAdminService.java (MODIFIED)
  - backend/src/main/java/com/swiftdrop/notification/application/MerchantStatusEventListener.java (ADDED)
  - backend/src/main/java/com/swiftdrop/notification/application/NotificationEventProcessor.java (MODIFIED)
  - backend/src/main/java/com/swiftdrop/notification/domain/NotificationRecord.java (MODIFIED)
  - backend/src/main/java/com/swiftdrop/notification/domain/NotificationRecordRepository.java (MODIFIED)
  - backend/src/main/resources/db/migration/notification/V202611141000__notification_allow_non_parcel_notifications.sql (ADDED)
  - backend/src/test/java/com/swiftdrop/MigrationConventionTest.java (MODIFIED)
  - backend/src/test/java/com/swiftdrop/ModuleBoundaryTest.java (MODIFIED)
  - backend/src/test/java/com/swiftdrop/notification/application/MerchantStatusEventListenerTest.java (ADDED)
  - backend/src/test/java/com/swiftdrop/notification/application/NotificationReconciliationServiceTest.java (MODIFIED)
  - backend/src/test/java/com/swiftdrop/notification/domain/NotificationEntityUnitTest.java (MODIFIED)
  - backend/src/test/java/com/swiftdrop/notification/domain/NotificationPersistenceIT.java (MODIFIED)

Documentation (1 file):
  - docs/adr/0003-backend-modules-and-boundaries.md (MODIFIED)

Frontend (6 files):
  - frontend/app/layout.tsx (MODIFIED)
  - frontend/app/notifications/page.tsx (ADDED)
  - frontend/components/nav-header.tsx (MODIFIED - CONFLICT)
  - frontend/components/notification-bell.tsx (ADDED)
  - frontend/lib/notification-context.tsx (ADDED)
  - frontend/lib/notifications.ts (ADDED)
```

---

## 2. Change Categorization & Feature Architecture

```mermaid
flowchart TD
    subgraph Merchant Module
        MAS[MerchantAdminService] -->|publishes eventId, userId, toStatus| MSC[MerchantStatusChanged]
    end

    subgraph Notification Module
        MSC -->|@TransactionalEventListener AFTER_COMMIT| MSEL[MerchantStatusEventListener]
        MSEL -->|findUser & check enabled| UD[UserDirectory (Identity API)]
        MSEL -->|insertIfAbsent ON CONFLICT DO NOTHING| DB[(PostgreSQL: notification_records)]
    end

    subgraph Frontend Client
        NB[NotificationBell Component] -->|useNotifications| NC[NotificationContext]
        NP[NotificationsPage /notifications] -->|api calls| NA[notifications.ts API client]
        NA -->|GET /api/v1/notifications| BE_API[NotificationController]
        NA -->|PATCH /api/v1/notifications/{id}/read| BE_API
        NA -->|POST /api/v1/notifications/read-all| BE_API
        NC -->|Polling 30s + window focus| NA
    end
```

### Detailed Categorization:

1. **Merchant Module Updates:**
   - Adds `UUID eventId` to `MerchantStatusChanged` domain event record. Provides backward-compatible constructor with `UUID.randomUUID()` fallback.
   - Updates `MerchantAdminService.approve(...)` and `reject(...)` to generate a explicit `UUID eventId` and pass it to `eventPublisher.publishEvent(...)`.
2. **Notification Module Integration:**
   - Adds `MerchantStatusEventListener` annotated with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` and `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
   - Resolves recipient user status via `UserDirectory.findUser(userId)` to ensure the merchant user exists and is `enabled()`.
   - Maps `APPROVED` and `REJECTED` transitions to user-friendly titles and non-leaking messages.
3. **Database & Persistence:**
   - Flyway migration `V202611141000__notification_allow_non_parcel_notifications.sql` drops `NOT NULL` on `parcel_id` and `tracking_number`, and relaxes the tracking number regex check to `(tracking_number IS NULL OR tracking_number ~ '^SD[0-9A-HJKMNP-TV-Z]{10}$')`.
   - Native query `insertIfAbsent(...)` in `NotificationRecordRepository` implements atomic `INSERT ... ON CONFLICT (event_id, channel, recipient_user_id) DO NOTHING`.
   - Entity refactored with explicit factory methods: `NotificationRecord.forParcel(...)` (strict non-null validation) and `NotificationRecord.forAccount(...)`.
4. **Architecture & Boundaries (ADR 0003):**
   - Permits `notification -> merchant` dependency in ADR 0003 and validates it via ArchUnit in `ModuleBoundaryTest.java`.
5. **Frontend Notification Subsystem:**
   - Dedicated notification types and API client (`notifications.ts`).
   - Context provider (`notification-context.tsx`) with optimistic local updates, unread badge counter, and 30-second background polling with tab visibility triggers.
   - Header bell widget (`notification-bell.tsx`) with dynamic unread badge, popover quick-view dropdown, mark-read click handler, and navigation link to full feed.
   - Route `/notifications` (`notifications/page.tsx`) with tabs, skeleton loaders, and pagination.

---

## 3. Backend Technical Audit

### Architectural Compliance & Boundaries
- **Modular Monolith Integrity:** The notification module consumes the public API event `com.swiftdrop.merchant.api.MerchantStatusChanged` and public query interface `com.swiftdrop.identity.api.UserDirectory`. No internal merchant domain entities or repositories are referenced.
- **ADR 0003 Alignment:** ADR 0003 specifically defines directional module dependencies. PR #132 formally documents `notification -> merchant` in `docs/adr/0003-backend-modules-and-boundaries.md` and updates `ModuleBoundaryTest.java`.
- **Transaction Boundaries:**
  - `MerchantStatusEventListener` uses `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. This ensures notifications are never written if the merchant approval/rejection transaction rolls back.
  - It specifies `@Transactional(propagation = Propagation.REQUIRES_NEW)` so the notification insert executes in its own independent transaction.
- **Idempotency & Race Handling:**
  - Employs a dual-layer idempotency defense:
    1. Pre-check: `recordRepository.existsByEventIdAndChannelAndRecipientUserId(...)`.
    2. Atomic DB-level insertion: `ON CONFLICT (event_id, channel, recipient_user_id) DO NOTHING` via `insertIfAbsent(...)`.

### Domain Logic & Repositories
- `NotificationRecord` enforces domain invariant separation through static factory methods `forParcel(...)` and `forAccount(...)`.
- `NotificationRecordRepository` maintains clean Spring Data specifications while using native SQL safely for the `ON CONFLICT` atomic upsert pattern.

---

## 4. Frontend Technical Audit

### Route, Layout & Context
- `frontend/app/layout.tsx` wraps all child components within `<NotificationProvider>`, nested inside `<AuthProvider>`. This ensures authenticated state is available throughout the tree.
- `frontend/app/notifications/page.tsx` implements client-side route guard:
  ```typescript
  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.replace(`/login?redirect=${encodeURIComponent("/notifications")}`);
    }
  }, [isAuthLoading, isAuthenticated, router]);
  ```
- **Error & Loading States:** Provides visual skeletons during feed loading, spinner state on "Mark All as Read", and user-friendly error banners via `formatApiError(err)`.
- **Accessibility:** Uses appropriate ARIA labels (`aria-label="Notifications"`, `aria-expanded`, `aria-hidden`) on the notification bell trigger and dropdown popovers.

### API Contract Alignment
Frontend TypeScript contracts in `frontend/lib/notifications.ts` precisely match the backend response DTOs (`NotificationResponse`, `PageResponse`, `UnreadCountResponse`) produced by `NotificationController.java`.

---

## 5. Database and Migration Audit

### Migration File: `V202611141000__notification_allow_non_parcel_notifications.sql`

```sql
-- 1. Relax NOT NULL constraint on parcel_id and tracking_number
ALTER TABLE notification_records 
    ALTER COLUMN parcel_id DROP NOT NULL,
    ALTER COLUMN tracking_number DROP NOT NULL;

-- 2. Update tracking number format constraint to apply only when tracking_number is populated
ALTER TABLE notification_records 
    DROP CONSTRAINT ck_notification_records_tracking_number;

ALTER TABLE notification_records 
    ADD CONSTRAINT ck_notification_records_tracking_number 
        CHECK (tracking_number IS NULL OR tracking_number ~ '^SD[0-9A-HJKMNP-TV-Z]{10}$');
```

### Safety & Compatibility Evaluation:
1. **Naming & Ordering:** Follows the project's Flyway timestamp convention (`VYYYYMMDDHHMM__description.sql`). Timestamp `202611141000` is strictly chronological after `V202611131900__identity_add_phone_number_to_users.sql` and `V202611111330__identity_user_avatar.sql`.
2. **PostgreSQL Compatibility:** `ALTER TABLE ... ALTER COLUMN ... DROP NOT NULL` is metadata-only in PostgreSQL and does not rewrite tables or lock the database significantly.
3. **Roll-Forward Compatibility:** All existing parcel notifications retain their non-null `parcel_id` and `tracking_number`. The updated check constraint maintains strict validation for any record where `tracking_number` is provided.
4. **Data Integrity:** The unique constraint `uq_notification_records_event_channel_user` (`event_id, channel, recipient_user_id`) remains fully enforced, ensuring idempotency for non-parcel records as well.

---

## 6. Security & Authorization Audit

| Area | Evaluation | Status |
| :--- | :--- | :--- |
| **Authentication & Authorization** | Backend endpoints (`/api/v1/notifications/**`) require authenticated user credentials (`@AuthenticationPrincipal UserPrincipal`). Callers can only query, read, or mark their own notifications (`recipient_user_id == principal.getId()`). | **PASS** |
| **Information Disclosure** | Internal rejection reasons (`event.reason()`) submitted by administrators are intentionally omitted from customer-facing notification messages to prevent leakage of internal review notes. | **PASS** |
| **Disabled Account Check** | `MerchantStatusEventListener` explicitly checks `userOpt.get().enabled()` before persisting notifications, avoiding spam or notification creation for deactivated accounts. | **PASS** |
| **SQL Injection Risks** | The native query in `insertIfAbsent(...)` uses strictly typed Spring Data named parameters (`:id`, `:eventId`, etc.). No dynamic SQL concatenation is used. | **PASS** |
| **XSS Prevention** | Notification content in `notifications/page.tsx` and `notification-bell.tsx` is rendered via React JSX text nodes, automatically escaping HTML entities. | **PASS** |

---

## 7. Integration with the Latest `dev` Branch

### Merge Conflicts & Overlapping Commits

1. **Conflict in `frontend/components/nav-header.tsx`:**
   - Both PR #123 (`feat/user-profiles`, merged into `dev`) and PR #132 modified the desktop and mobile navigation header menus.
   - `dev` added user profile links (`/profile`) and avatar images (`user.hasAvatar ? <img src={getUserAvatarUrl(...)} /> : <span ...>{initial}</span>`).
   - `PR #132` added the `<NotificationBell />` component beside the user name and mobile user initial.
   - **Resolution Strategy:** Merge both changes so that the header renders both the `<NotificationBell />` and the profile avatar/link.

2. **Conflict in `MigrationConventionTest.java`:**
   - `dev` includes entries for `202611081400` (`identity_user_profiles_and_addresses.sql`) and `202611111330` (`identity_user_avatar.sql`).
   - PR #132's branch omitted those entries and added `202611141000`.
   - Running `MigrationConventionTest` against `dev` with PR #132's file results in:
     ```text
     [ERROR] MigrationConventionTest.flywayDiscoversAllModuleMigrationsFromConfiguredLocations:102 
     Expecting ArrayList to contain exactly (in any order):
       [... "202611141000" ...]
     but some elements were not expected:
       ["202611081400", "202611111330"]
     ```
   - **Resolution Strategy:** Keep all migration tuples in `MigrationConventionTest.java` so both identity profile migrations and the notification migration are included.

---

## 8. Testing and Verification Results

### Backend Verification
- **Compilation:** `.\mvnw.cmd test-compile` exited with code `0` (270 main classes, 112 test classes).
- **Unit & Boundary Tests:**
  - `com.swiftdrop.ModuleBoundaryTest`: 10 passed, 0 failures.
  - `com.swiftdrop.notification.application.MerchantStatusEventListenerTest`: 7 passed, 0 failures.
  - `com.swiftdrop.notification.application.NotificationReconciliationServiceTest`: 4 passed, 0 failures.
  - `com.swiftdrop.notification.domain.NotificationEntityUnitTest`: 5 passed, 0 failures.
  - Total: **26 tests run, 0 failures**.

### Frontend Verification
- **Linter:** `npm run lint` passed with 0 errors or warnings.
- **Next.js Type Check Note:** Running `npx tsc --noEmit` on the PR branch initially flagged missing types for `app/profile/page.js` because `.next` cache from the previous aborted merge retained references to `dev`. Once `dev` is merged, `app/profile/page.tsx` will be present.

---

## 9. Review Findings & Issues

### Finding 1: Merge Conflict in `frontend/components/nav-header.tsx`
- **Severity:** `High` (Blocks merge)
- **Affected File:** `frontend/components/nav-header.tsx`
- **Problem:** Both PR #123 and PR #132 concurrently edit `NavHeader` to mount new controls (profile avatar vs. notification bell). Git cannot automatically reconcile this file.
- **Suggested Resolution:** Rebase `feat/dev-notifications` on `origin/dev` or perform a merge of `origin/dev` into `feat/dev-notifications`, combining the `<NotificationBell />` with the avatar image and `/profile` link.

### Finding 2: `MigrationConventionTest` Missing `dev` Migrations
- **Severity:** `High` (Blocks merge / Breaks CI on `dev`)
- **Affected File:** `backend/src/test/java/com/swiftdrop/MigrationConventionTest.java`
- **Problem:** PR #132's `MigrationConventionTest` hardcodes the expected list of migrations based on an older branch point, missing `V202611081400` and `V202611111330`.
- **Suggested Resolution:** During the merge resolution, include tuples for `202611081400`, `202611111330`, and `202611141000` in `MigrationConventionTest.java`.

### Finding 3: Polling Interval Jitter & Exponential Backoff
- **Severity:** `Low` (Non-blocking improvement)
- **Affected File:** `frontend/lib/notification-context.tsx`
- **Observation:** `NotificationProvider` uses a fixed 30-second interval (`window.setInterval`) to query `/api/v1/notifications/unread-count`.
- **Suggested Resolution:** In a future sprint, consider implementing a slight jitter (±3s) or WebSocket / Server-Sent Events (SSE) to reduce periodic database load when many users are active.

---

## 10. Final PR Verdict & Merge Recommendation

### Verdict: `CHANGES REQUESTED` (Fix Conflicts & Re-test)

| Checklist Item | Status | Notes |
| :--- | :---: | :--- |
| **New Features Functional** |  | Merchant notifications and in-app bell/feed work as designed. |
| **Architecture & Conventions** |  | Follows modular monolith, ADR 0003 updated and tested. |
| **Security & Authorization** |  | Strong ownership checks, no sensitive data leaks. |
| **Database Migration Safety** |  | Migration `V202611141000` is non-destructive and backward compatible. |
| **Clean Merge with `dev`** |  | Conflict in `nav-header.tsx`. |
| **CI / Automated Tests** |  | `MigrationConventionTest` fails against `dev` without update. |

### Required Actions Before Merging:
1. Merge the latest `origin/dev` into `feat/dev-notifications` (or rebase).
2. Resolve the conflict in `frontend/components/nav-header.tsx` to include both the Notification Bell and the Profile link/avatar.
3. Ensure `MigrationConventionTest.java` includes all migrations from both branches.
4. Run `mvnw test` and `npm run build` to confirm clean compilation and zero test failures.
5. Push the resolved commit to `feat/dev-notifications` to enable GitHub auto-merge.
