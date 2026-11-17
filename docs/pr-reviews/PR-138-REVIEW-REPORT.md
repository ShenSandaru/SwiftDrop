# PR #138 Integration Review Report

**PR:** [#138 — feat(notification): add courier assignment notifications](https://github.com/ShenSandaru/SwiftDrop/pull/138)  
**Author:** `sewmini12` (SEWMINI KAVINDYA)  
**Source Branch:** `feat/dev-notifications` (`ebdd000c810fdc016cd2163f3065defb13a35aa9`)  
**Target Branch:** `dev`  
**Audit Date:** 2026-11-14  
**Audit Mode:** Read-Only  

---

## 1. PR Overview

- **Status:** `OPEN`
- **Mergeable / MergeStateStatus:** `MERGEABLE` / `BLOCKED`
- **Scope:** `21` files changed (`+1,423 / -78`), all in `backend/` (0 frontend files, 0 Flyway migrations).
- **CI Status:**
  - `frontend`: **PASS** (1m 27s)
  - `backend`: **FAIL** (1m 37s) — `1,425` tests run, `1` failure (`NotificationEventProcessorTest.customerDisabledOrMissingAdvancesWatermarkWithoutNotification:362`).
- **Local Git State at Audit Time:** Branch `feat/dev-notifications` at `ebdd000c810fdc016cd2163f3065defb13a35aa9`, clean working tree (`git status --short` empty, `git ls-files -u` empty).

---

## 2. Feature Explanation

PR #138 implements in-app notifications for couriers when a dispatcher assigns, reassigns, or cancels a parcel assignment (pickup, delivery, or return). It uses Spring application events (`ParcelCourierAssigned`, `ParcelCourierAssignmentCancelled`) published by `ParcelAssignmentService` and consumed asynchronously after transaction commit by `CourierAssignmentEventListener` in the `notification` module. It also removes the redundant logging-only `MerchantStatusEventListener` from the `merchant` module and adds merchant application submitted notifications.

---

## 3. Changed Files and Scope

- **`delivery` module:** `ParcelCourierAssigned.java`, `ParcelCourierAssignmentCancelled.java`, `ParcelAssignmentService.java`, `ParcelHubService.java`, `ParcelAssignmentServiceTest.java`
- **`identity` module:** `UserDirectory.java`, `AccountService.java`
- **`merchant` module:** `MerchantApplicationSubmitted.java`, `MerchantRegistrationService.java`, `MerchantStatusEventListener.java` (removed), `MerchantRegistrationServiceTest.java`
- **`notification` module:** `CourierAssignmentEventListener.java`, `MerchantApplicationEventListener.java`, `NotificationEventProcessor.java`, `NotificationTemplateResolver.java`, plus 6 test classes (`CourierAssignmentEventListenerTest`, `MerchantApplicationEventListenerTest`, `NotificationEventListenerTest`, `NotificationEventProcessorTest`, `NotificationReconciliationServiceTest`, `NotificationConsumerConfigurationTest`)

---

## 4. Backend Review

- **Module Boundaries:** Cleanly respects ADR 0003 modular-monolith boundaries (`delivery.api` / `merchant.api` / `identity.api` → `notification.application`).
- **Transaction & Idempotency:** `CourierAssignmentEventListener` processes events after commit in a separate transaction, validates the courier via `UserDirectory`, skips missing/disabled users, and uses the existing notification repository idempotency mechanism.
- **Confirmed Defect:** `NotificationEventProcessorTest.customerDisabledOrMissingAdvancesWatermarkWithoutNotification:362` fails in CI (`verifyNoInteractions(templateResolver)`), indicating `templateResolver` is invoked even when the customer is disabled/missing.

---

## 5. Frontend Review

- No frontend files were modified in PR #138. `frontend` CI passed (`1m 27s`).

---

## 6. Database and Migration Review

- No Flyway migrations were added or modified. No migration version collisions.

---

## 7. Security Review

- `CourierAssignmentEventListener` validates that the target courier exists and is enabled via `UserDirectory` before creating notifications. No new public endpoints or authorization bypasses introduced.

---

## 8. Test and CI Results

- **`frontend` CI:** **PASS** (`1m 27s`).
- **`backend` CI:** **FAIL** (`1m 37s`) — `Tests run: 1425, Failures: 1, Errors: 0, Skipped: 0`:
  - Failed test: `NotificationEventProcessorTest.customerDisabledOrMissingAdvancesWatermarkWithoutNotification:362` (`verifyNoInteractions(templateResolver)`).

---

## 9. Integration and Regression Risks

- **PR #136 / PR #139:** PR #138 touches only `delivery`, `identity`, `merchant`, and `notification` — no file overlap with PR #136 (`pricing`) or PR #139 (`customer/new`, `DeliveryZoneController`).
- **Reliability note:** As documented in the PR description, in-process Spring application events are used; if the application crashes between commit and listener execution, the notification is lost (acceptable per PR scope).

---

## 10. Findings Classified by Priority

- **P1 — High (`NotificationEventProcessorTest.java:362` / `NotificationEventProcessor.java`):**
  - *Problem:* `NotificationEventProcessorTest.customerDisabledOrMissingAdvancesWatermarkWithoutNotification` fails on `verifyNoInteractions(templateResolver)`.
  - *Why it matters:* Blocks `backend` CI and indicates `templateResolver` is called unnecessarily when a customer is disabled or missing.
  - *Suggested fix:* Check `userDirectory.findUser(...)` (and enabled status) before calling `templateResolver`, or update the test if template resolution is intentionally performed earlier.

---

## 11. Positive Aspects

- Clean modular-monolith event contracts (`ParcelCourierAssigned`, `ParcelCourierAssignmentCancelled`, `MerchantApplicationSubmitted`).
- Thorough unit test coverage for `CourierAssignmentEventListener`, `MerchantApplicationEventListener`, and `ParcelAssignmentService`.
- Zero Flyway migration conflicts and clean removal of the redundant `MerchantStatusEventListener`.

---

## 12. Recommended Fixes

1. Fix `NotificationEventProcessor` / `NotificationEventProcessorTest.customerDisabledOrMissingAdvancesWatermarkWithoutNotification:362` so `backend` CI passes (`1425/1425`).

---

## 13. Final Verdict

**Verdict: READY AFTER FIXES**

PR #138 is well-structured, respects module boundaries, and has no conflicts with `dev`, PR #136, or PR #139, but `backend` CI is currently blocked by 1 unit test failure in `NotificationEventProcessorTest.customerDisabledOrMissingAdvancesWatermarkWithoutNotification:362`.
