# PR #142 — CI Fix and Verification Report

**PR:** [#142 — feat(notification): add email and mock SMS notification channels](https://github.com/ShenSandaru/SwiftDrop/pull/142)  
**Branch:** `feat/dev-notifications` (`5435abd6ac6e57d63266c5f77364d2527d87ac23`)  
**Date:** 2026-11-14  

---

## 1. Files Modified

1. `backend/src/test/java/com/swiftdrop/MigrationConventionTest.java`:
   - Added `("202611141200", "notification add retry columns", "notification/V202611141200__notification_add_retry_columns.sql")` in chronological order.
2. `backend/src/test/java/com/swiftdrop/notification/infrastructure/NotificationConsumerConfigurationTest.java`:
   - Added `.withBean(EmailNotificationSender.class, () -> mock(EmailNotificationSender.class))` and `.withBean(SmsNotificationSender.class, () -> mock(SmsNotificationSender.class))` to `withMockedDependencies()`.

---

## 2. Verification Results

- **`git diff --check`:** **PASS** (exit `0`).
- **Unit Tests (`.\mvnw.cmd test` via `verify -Pintegration`):** **PASS** (`0` failures, `0` errors).
- **Integration Tests (`.\mvnw.cmd verify -Pintegration`):** **FAIL** (`Tests run: 123, Failures: 2, Errors: 10, Skipped: 0`):
  - `DatabaseMigrationIT` (1 failure): Expected migration list does not include `202611141200__notification_add_retry_columns.sql`.
  - `NotificationEventProcessorIT` (5 errors): `JpaSystemException: current transaction is aborted, commands ignored until end of transaction block` on `update notification_records set delivery_status=? where id=?`.
  - `NotificationEventListenerIT` (3 errors): `ConditionTimeout` waiting for notification persistence.
  - `NotificationReconciliationIT.pendingEventRecoveredFromParcelEvents` (1 failure): `expected: 1 but was: 0`.
  - `ParcelAuthorizationMatrixIT.createPeopleAndParcels` (2 errors): `DELETE FROM users` violates `notification_records_recipient_user_id_fkey`.

---

## 3. Conclusion

The two unit-test CI blockers (`MigrationConventionTest` and `NotificationConsumerConfigurationTest`) are fixed and all **unit tests pass**. However, **`verify -Pintegration` revealed 12 integration-test failures/errors** in `DatabaseMigrationIT` and `Notification*IT` that require changes beyond the two authorized test files (including investigating the aborted-transaction SQL error in `NotificationEventProcessor` / `NotificationRecord`).
