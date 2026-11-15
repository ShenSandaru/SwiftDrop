# PR #156 — Parcel Lifecycle Audit & Verification Summary

**PR:** [#156 — `feat: add hub seed file`](https://github.com/ShenSandaru/SwiftDrop/pull/156)  
**Source branch:** `feat/hub-seed-file` → **Target branch:** `dev`  
**Date:** 2026-11-15  
**Final Verdict:** **SAFE TO MERGE** (All parcel lifecycle checks, unit tests, `MigrationConventionTest`, and `DatabaseMigrationIT` pass)

---

## 1. PR Summary

PR #156 adds two idempotent Flyway seed migrations so all **25 Sri Lankan district hubs** are provisioned on initial database startup, with **Colombo Branch (`CMB`)** configured as the central hub (`central = TRUE`) and the remaining **24 districts** configured as regional branch hubs (`central = FALSE`) linked to primary delivery zones:
- `backend/src/main/resources/db/migration/pricing/V202611161100__pricing_seed_district_primary_zones.sql`
- `backend/src/main/resources/db/migration/delivery/V202611161200__delivery_seed_district_hubs.sql`

---

## 2. Parcel Lifecycle Regression Audit

- **Status Transitions:** No regression. No status enum values, transition rules, or validation logic are modified.
- **Assignment & Dispatch:** No regression. `Hub`, `HubLink`, `HubStaff`, and dispatch assignment logic are untouched; pre-seeding the 25 district hubs enables hub-network routing out of the box.
- **Courier & Customer Workflows:** No regression. No frontend or backend workflow code is modified.
- **Tracking & Notifications:** No regression. No event publishers, listeners, status history, or notification consumers are modified.
- **API & Module Compatibility:** No regression. No REST endpoints, DTOs, or module boundaries are modified.
- **Database & Migrations:** `V202611161100` and `V202611161200` respect `ck_hubs_role`, `uq_hubs_single_central`, and use `NOT EXISTS` + `ON CONFLICT DO NOTHING` to preserve existing hubs and zones.

---

## 3. Fixes Applied for CI / Test Alignment

1. **`backend/src/test/java/com/swiftdrop/MigrationConventionTest.java`** (`+8/-0` lines)
   - Registered `202611161100` (`pricing seed district primary zones`) and `202611161200` (`delivery seed district hubs`) in chronological order between `202611161000` and `202611171000`.
2. **`backend/src/test/java/com/swiftdrop/DatabaseMigrationIT.java`** (`+9/-3` lines)
   - Updated expected migration count from `35` to `37`.
   - Registered `pricing/V202611161100__pricing_seed_district_primary_zones.sql` (index `32`) and `delivery/V202611161200__delivery_seed_district_hubs.sql` (index `33`), shifting `32..34` to `34..36`.

No production code, Flyway SQL migrations, or parcel lifecycle logic were modified.

---

## 4. Final Verification Results

| Check / Command | Result | Notes |
| :--- | :--- | :--- |
| `.\mvnw.cmd test -Dtest=MigrationConventionTest` | **PASSED** (`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`) | Both migration convention tests pass. |
| `.\mvnw.cmd verify -Pintegration "-Dit.test=DatabaseMigrationIT"` (surefire unit tests) | **PASSED** | All backend unit tests ran and passed. |
| `.\mvnw.cmd verify -Pintegration "-Dit.test=DatabaseMigrationIT"` (failsafe integration test) | **PASSED** (`Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`) | All 37 Flyway migrations applied and Hibernate schema validation succeeded. |
| `git diff --check` | **PASSED** (exit code `0`, no output) | No whitespace or formatting issues. |
