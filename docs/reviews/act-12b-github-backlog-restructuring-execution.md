# ACT-12B: GitHub Backlog Restructuring & Epic Organization (Phase C1 Execution Report)

- **Document ID:** ACT-12B
- **Date:** 2026-11-04
- **Branch / Commit:** `dev` at `a67da3d` (`docs: add act-11 remaining feature development roadmap`)
- **Repository:** `ShenSandaru / SwiftDrop` (`https://github.com/ShenSandaru/SwiftDrop`)
- **Execution Mode:** Phase C1 Execution Complete (Epics & New Issues Created; Cross-References Linked; Phase C2 Closures **NOT** Executed)

---

## 1. Executive Summary

This report documents the live execution of **ACT-12B Phase C1** for the SwiftDrop project, reorganizing the backlog around the five core development epics approved in the ACT-12A proposal.

In strict compliance with the user's authorization instructions:
1. **Five Parent Epics** were created on GitHub (`#100` through `#104`) with structured markdown task lists.
2. **Eleven Approved New Issues** (`#89` through `#99`) were created following duplicate verification, including the explicit dependency clarification for `NEW-01` (PostgreSQL session advisory locking depending on `#52` and `#53`).
3. **Forty-five Existing Issues** were mapped and linked to their parent epics via cross-reference comments, preserving their original bodies, labels, and discussions.
4. **Assigned Prerequisites Preserved:** Issue `#35` retained its assignee (`ShaliniBangamuwage`) and `#47` retained its assignees (`Ashankavinda277`, `ThulaniThehara`).
5. **Phase C2 (Ticket Closures) WAS NOT EXECUTED:** In accordance with the explicit instruction (*"Do NOT close any existing issues"*), all 16 closure candidates remain strictly **OPEN**.
6. **No commits or pushes** have been performed. Local `dev` branch state is preserved.

---

## 2. Before-and-After Backlog Metrics

| Metric | Before ACT-12B | After Phase C1 Execution | Delta / Notes |
|---|:---:|:---:|---|
| **Total Issues in Namespace** | 71 (`#8`–`#78`) | **87** (`#8`–`#104`) | +16 issues (+11 new issues, +5 epics) |
| **Total Open Issues** | 70 | **86** | All newly created issues are currently open |
| **Total Closed Issues** | 1 (`#11`) | **1** (`#11`) | Historical closed issue `#11` strictly preserved |
| **Total Parent Epics** | 0 | **5** (`#100`–`#104`) | Created with label `epic` and task lists |
| **Newly Created Tasks** | 0 | **11** (`#89`–`#99`) | Single-responsibility architectural issues |
| **Assigned Open Issues** | 5 | **5** | Zero modifications to assignees |
| **Closed Candidates Closed** | 0 | **0** | **Phase C2 was NOT executed; 16 candidates remain open** |

---

## 3. Five Created Parent Epics

The five approved parent epics were created with descriptive scopes, success criteria, and interactive GitHub task lists:

| Epic ID | GitHub Issue # | Title & URL | Labels Applied | Linked Child Issues |
|---|:---:|---|---|:---:|
| **EPIC-01** | **#100** | [EPIC-01: Identity & Authentication Completion](https://github.com/ShenSandaru/SwiftDrop/issues/100) | `epic`, `identity`, `security` | `#9`, `#90`, `#91`, `#92` |
| **EPIC-02** | **#101** | [EPIC-02: Dispatch Module Completion](https://github.com/ShenSandaru/SwiftDrop/issues/101) | `epic`, `delivery`, `admin` | `#19`, `#35`, `#36`, `#37`, `#38`, `#93` |
| **EPIC-03** | **#102** | [EPIC-03: Role-Based Dashboard Completion](https://github.com/ShenSandaru/SwiftDrop/issues/102) | `epic`, `frontend` | `#14`, `#15`, `#22`, `#23`, `#24`, `#25`, `#26`, `#27`, `#28`, `#30`, `#31`, `#32`, `#33`, `#34`, `#40`, `#44`, `#45`, `#50`, `#60`, `#61`, `#96`, `#97` |
| **EPIC-04** | **#103** | [EPIC-04: Merchant Module Completion](https://github.com/ShenSandaru/SwiftDrop/issues/103) | `epic`, `api`, `security` | `#39`, `#41`, `#42`, `#43`, `#46`, `#47`, `#48`, `#49`, `#51`, `#94`, `#95` |
| **EPIC-05** | **#104** | [EPIC-05: RabbitMQ & Notification Integration](https://github.com/ShenSandaru/SwiftDrop/issues/104) | `epic`, `messaging`, `infrastructure` | `#20`, `#52`, `#53`, `#54`, `#55`, `#56`, `#57`, `#58`, `#59`, `#62`, `#89`, `#98` |

---

## 4. Eleven Created New Issues

All 11 proposed new issues were verified against existing issues to prevent duplicates and created with complete specifications:

| Planning ID | GitHub Issue # | Title & URL | Parent Epic / Stream | Labels |
|---|:---:|---|---|---|
| **NEW-01** | **#89** | [SD-511: PostgreSQL Session Advisory Lock for OutboxRelay Leader Election](https://github.com/ShenSandaru/SwiftDrop/issues/89) | EPIC-05 | `backend`, `messaging`, `database` |
| **NEW-02** | **#90** | [SD-108: UserDirectory Facade Integration across Consuming Business Modules](https://github.com/ShenSandaru/SwiftDrop/issues/90) | EPIC-01 | `backend`, `identity`, `security` |
| **NEW-03** | **#91** | [SD-109: Validate User Enabled Status on Active JWT Requests (SEC-01)](https://github.com/ShenSandaru/SwiftDrop/issues/91) | EPIC-01 | `backend`, `identity`, `security` |
| **NEW-04** | **#92** | [SD-110: Frontend Google OAuth Callback Routing and Context Synchronization](https://github.com/ShenSandaru/SwiftDrop/issues/92) | EPIC-01 | `frontend`, `identity`, `security` |
| **NEW-05** | **#93** | [SD-317: Dispatch Courier Assignment Concurrency and Optimistic Locking Engine](https://github.com/ShenSandaru/SwiftDrop/issues/93) | EPIC-02 | `backend`, `delivery`, `database` |
| **NEW-06** | **#94** | [SD-414: Merchant Idempotency Key Storage and Deduplication Table](https://github.com/ShenSandaru/SwiftDrop/issues/94) | EPIC-04 | `backend`, `database`, `api` |
| **NEW-07** | **#95** | [SD-415: AES-256-GCM Encryption Vault for Webhook Signing Secrets (SEC-04)](https://github.com/ShenSandaru/SwiftDrop/issues/95) | EPIC-04 | `backend`, `security`, `database` |
| **NEW-08** | **#96** | [SD-604: Admin Overview Dashboard and Merchant Approval Portal UI](https://github.com/ShenSandaru/SwiftDrop/issues/96) | EPIC-03 | `frontend`, `admin` |
| **NEW-09** | **#97** | [SD-605: Admin Dead-Letter Queue Inspection and Replay UI](https://github.com/ShenSandaru/SwiftDrop/issues/97) | EPIC-03 | `frontend`, `admin`, `messaging` |
| **NEW-10** | **#98** | [SD-512: Decouple Ordered Consumer SPI from delivery.api Module Boundary](https://github.com/ShenSandaru/SwiftDrop/issues/98) | EPIC-05 | `backend`, `messaging` |
| **NEW-11** | **#99** | [SD-705: Frontend Automated Component Testing Setup (Vitest & RTL)](https://github.com/ShenSandaru/SwiftDrop/issues/99) | Supporting Eng. | `frontend`, `quality`, `test` |

### Special Dependency Clarification Applied to #89 (NEW-01)
As explicitly requested, Issue `#89` incorporates:
- **Dependency on `#52`:** Declares RabbitMQ exchange and queue bindings.
- **Dependency on `#53`:** Scheduled OutboxRelay execution mechanism.
- **Concurrency Defense:** Mandatory implementation of `pg_try_advisory_lock(0x53444F55544258L)` to ensure leader election and eliminate duplicate event publishing across parallel multi-instance clusters.

---

## 5. Existing Issues Linked to Parent Epics

All 45 existing open issues mapped in the dry-run received a cross-reference comment connecting them directly to their parent epic, without altering their descriptions, labels, or historical comments:

### EPIC-01 Links (1 Existing)
- `#9`: Linked to `#100`

### EPIC-02 Links (5 Existing)
- `#19`: Linked to `#101`
- `#35`: Linked to `#101` *(Assigned to `ShaliniBangamuwage` — Assignee preserved)*
- `#36`: Linked to `#101`
- `#37`: Linked to `#101`
- `#38`: Linked to `#101`

### EPIC-03 Links (20 Existing)
- `#14`, `#15`: Linked to `#102`
- `#22`, `#23`, `#24`, `#25`, `#26`: Linked to `#102`
- `#27`, `#28`: Linked to `#102`
- `#30`, `#31`, `#32`, `#33`, `#34`: Linked to `#102`
- `#40`: Linked to `#102`
- `#44`, `#45`: Linked to `#102`
- `#50`: Linked to `#102`
- `#60`, `#61`: Linked to `#102`

### EPIC-04 Links (9 Existing)
- `#39`, `#41`, `#42`, `#43`: Linked to `#103`
- `#46`: Linked to `#103`
- `#47`: Linked to `#103` *(Assigned to `Ashankavinda277`, `ThulaniThehara` — Assignees preserved)*
- `#48`, `#49`: Linked to `#103`
- `#51`: Linked to `#103`

### EPIC-05 Links (10 Existing)
- `#20`: Linked to `#104`
- `#52`, `#53`, `#54`, `#55`, `#56`, `#57`, `#58`, `#59`: Linked to `#104`
- `#62`: Linked to `#104`

---

## 6. Assigned Issues Preservation Verification

Live query verification confirms that all pre-existing issue assignments remain 100% unaltered:

| Issue # | Title | Verified Live Assignees | Status |
|:---:|---|---|:---:|
| **#16** | SD-205: Implement secure tracking-number generator | `dhananjaya-hbc` | **Preserved (OPEN)** |
| **#17** | SD-204: Implement Parcel aggregate and schema | `dhananjaya-hbc` | **Preserved (OPEN)** |
| **#18** | SD-301: Implement delivery status state machine | `dhananjaya-hbc` | **Preserved (OPEN)** |
| **#35** | SD-305: Implement courier profile management schema | `ShaliniBangamuwage` | **Preserved (OPEN)** |
| **#47** | SD-409: Implement SSRF-safe HTTP client | `Ashankavinda277`, `ThulaniThehara` | **Preserved (OPEN)** |

---

## 7. Closure Candidates Status (Phase C2 Not Executed)

As instructed, **zero issues were closed**. All 16 verified close candidates remain open:

- `#8` (`SD-004`): **OPEN**
- `#12` (`SD-202`): **OPEN**
- `#19` (`SD-302`): **OPEN**
- `#20` (`SD-303`): **OPEN**
- `#21` (`SD-304`): **OPEN**
- `#22` (`SD-309`): **OPEN**
- `#23` (`SD-310`): **OPEN**
- `#24` (`SD-311`): **OPEN**
- `#25` (`SD-312`): **OPEN**
- `#26` (`SD-313`): **OPEN**
- `#29` (`SD-316`): **OPEN**
- `#30` (`SD-206`): **OPEN**
- `#31` (`SD-207`): **OPEN**
- `#32` (`SD-208`): **OPEN**
- `#34` (`SD-210`): **OPEN**
- `#60` (`SD-510`): **OPEN**

*Phase C2 remains pending explicit subsequent user authorization.*

---

## 8. Preserved Supporting Engineering Issues

The following 22 engineering, infrastructure, test, and documentation issues remain independent and active under **Supporting Engineering & Project Governance**:

- **CI/CD & Quality:** `#8`, `#29`, `#63`, `#64`
- **Docker & Deployment:** `#10`, `#65`, `#66`, `#67`, `#68`
- **Pricing & Tracking Core Foundations:** `#12`, `#13`, `#21`
- **Academic System Design Reports & Submission:** `#69`, `#70`, `#71`, `#72`, `#73`, `#74`, `#75`, `#76`, `#77`, `#78`
- **Frontend Automated Testing:** `#99` (`SD-705`)

---

## 9. Conclusion & Next Steps

Phase C1 execution is complete, verified, and recorded:
1. Five parent epics (`#100`–`#104`) provide a clear, hierarchical organization for the remaining engineering roadmap.
2. Eleven single-responsibility new issues (`#89`–`#99`) eliminate architectural gaps in concurrency, security, and testing.
3. Zero code was altered; git state `a67da3d` is fully preserved.
4. When authorized, **Phase C2** may be performed to add formal closure comments and close the 16 verified completed tickets.

