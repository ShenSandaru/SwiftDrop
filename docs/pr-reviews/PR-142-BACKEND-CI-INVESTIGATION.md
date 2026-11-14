# PR #142 — Backend CI Failure Investigation

**PR:** [#142 — feat(notification): add email and mock SMS notification channels](https://github.com/ShenSandaru/SwiftDrop/pull/142)  
**Source Branch:** `feat/dev-notifications` (`5435abd6ac6e57d63266c5f77364d2527d87ac23`)  
**CI Run:** `43613783087` / Job `132504303890` (`backend` **FAIL**, `1m 24s`; `frontend` **PASS**, `1m 20s`)  
**Date:** 2026-11-14  

---

## 1. Exact GitHub Actions Failure Summary

`./mvnw --batch-mode --no-transfer-progress clean verify` failed in Maven Surefire with:
- **`Tests run: 1433, Failures: 5, Errors: 0, Skipped: 0`**

### Failure 1: `MigrationConventionTest` (1 failure — Confirmed)
- `MigrationConventionTest.flywayDiscoversAllModuleMigrationsFromConfiguredLocations:102`
- **Root Cause:** PR #142 added `backend/src/main/resources/db/migration/notification/V202611141200__notification_add_retry_columns.sql`, but did not add `("202611141200", "notification add retry columns", "notification/V202611141200__notification_add_retry_columns.sql")` to the expected migration list in `MigrationConventionTest.java`.

### Failures 2–5: `NotificationConsumerConfigurationTest` (4 failures — Confirmed)
- `NotificationConsumerConfigurationTest.consumerAndReconciliationEnabledWhenBothPropertiesMissing:46`
- `NotificationConsumerConfigurationTest.consumerAndReconciliationEnabledWhenConsumerTrueAndReconciliationMissing:60`
- `NotificationConsumerConfigurationTest.consumerEnabledAndReconciliationDisabledWhenConsumerTrueAndReconciliationFalse:104`
- `NotificationConsumerConfigurationTest.bothEnabledWhenConsumerTrueAndReconciliationTrue:120`
- **Root Cause:** `NotificationEventProcessor` now depends on `EmailNotificationSender` and `SmsNotificationSender` (introduced in PR #142), which are not registered/mocked in `NotificationConsumerConfigurationTest`'s `ApplicationContextRunner`, causing the `ApplicationContext` assertions in those 4 tests to fail.

---

## 2. Impact on Existing Modules & Functionality

- **Existing modules (`delivery`, `identity`, `merchant`, `pricing`, `tracking`, `assistant`) and `frontend/`:** **Unaffected.** Both failures are confined to `MigrationConventionTest` and `NotificationConsumerConfigurationTest` in `notification`.
- **Database migration compatibility:** `V202611141200__notification_add_retry_columns.sql` is chronologically valid (`202611141200` between `202611141100` and `202611151000`).

---

## 3. Recommended Minimal Fixes

1. Register `("202611141200", "notification add retry columns", "notification/V202611141200__notification_add_retry_columns.sql")` in `MigrationConventionTest.java` in chronological order.
2. Provide/mock `EmailNotificationSender` and `SmsNotificationSender` in `NotificationConsumerConfigurationTest.java` so `NotificationEventProcessor` can instantiate in the `ApplicationContextRunner`.
3. Re-run `.\mvnw.cmd test` and `.\mvnw.cmd verify -Pintegration` after applying the fixes.
