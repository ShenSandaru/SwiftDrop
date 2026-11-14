# PR #138 – Fix and Verification Report

**PR:** [#138 — feat(notification): add courier assignment notifications](https://github.com/ShenSandaru/SwiftDrop/pull/138)  
**Branch:** `feat/dev-notifications` (`ebdd000c810fdc016cd2163f3065defb13a35aa9`)  
**Date:** 2026-11-14  

---

## 1. Root Cause

- `NotificationEventProcessor.applyEvent(ParcelStatusChanged)` (`backend/src/main/java/com/swiftdrop/notification/application/NotificationEventProcessor.java:246`) now calls `notifyDispatchersIfApplicable(event)` after `createInAppNotification(event, customerId)`.
- `notifyDispatchersIfApplicable(event)` (lines 250–251) invokes `templateResolver.resolveForDispatcher(event)` even when the customer recipient is disabled or missing.
- `NotificationEventProcessorTest.customerDisabledOrMissingAdvancesWatermarkWithoutNotification` (line 362) still asserted `verifyNoInteractions(templateResolver)` from before dispatcher notifications were added, causing the single CI failure.
- **Conclusion:** The **test assertion** was outdated; `NotificationEventProcessor` intentionally checks `templateResolver.resolveForDispatcher(event)` for dispatcher notifications while skipping `templateResolver.resolve(event)` and `recordRepository` writes for the disabled/missing customer.

---

## 2. Files Changed & Exact Fix

- `backend/src/test/java/com/swiftdrop/notification/application/NotificationEventProcessorTest.java` (line 362):
  - Replaced `verifyNoInteractions(templateResolver);` with `verify(templateResolver, never()).resolve(any());` while preserving `verify(consumerStateRepository).save(any());` and `verifyNoInteractions(recordRepository);`.

---

## 3. Verification Results

- **Focused test (`.\mvnw.cmd "-Dtest=NotificationEventProcessorTest" test`):**
  - `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0` (**BUILD SUCCESS**, `36.691 s`).
- **Full backend unit suite (`.\mvnw.cmd test`):**
  - `Tests run: 1425, Failures: 0, Errors: 0, Skipped: 0` (**BUILD SUCCESS**, `02:09 min`).

---

## 4. Remaining Risks

- None in the backend unit test suite. No production code, frontend files, or Flyway migrations were modified.
