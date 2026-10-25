# SwiftDrop project requirements

This is the repository's summary of the official assignment brief, *Assignment Guideline: Parcel
Delivery & Courier Management System* (4 pages). The original PDF is course material and is not
stored in the repository; this document is the repository's requirements summary. Section names
below follow the brief's headings. Requirement IDs (for example `CUS-1`) were added by the team for
traceability; they are not part of the brief.

Everything in sections 1 to 12 is stated by the brief. Section 13 is the team's architecture
decision, not an assignment requirement. Where the brief gives no detail (fee formula, report
definitions, coverage threshold, receipt format and similar), none is invented here; see
[Not specified by the brief](#not-specified-by-the-brief).

## 1. Assignment overview

**Objective (brief: "Objective").** Design and implement a fully functional Parcel Delivery &
Courier Management System that lets customers and merchants send and track parcels, lets
dispatchers assign deliveries, and lets couriers update delivery progress in real time. It should
also integrate with external systems, as enterprise applications rarely operate in isolation. The
team designs, implements, secures, containerizes, tests and
deploys a modern enterprise application.

- **Weightage:** 20 marks.
- **Group size:** 10 members.

**Scenario (brief: "Scenario").** SwiftDrop is a growing courier company operating island-wide. It
manages bookings through phone calls and spreadsheets, and customers cannot see where their parcels
are. SwiftDrop also wants partner online stores (merchants) to create delivery orders automatically
from their own e-commerce platforms.

## 2. Customer requirements

Brief: "Assignment Overview, 1. Customer Functionality".

| ID | Requirement |
|---|---|
| CUS-1 | Secure signup and login, including "Login with Google" (OAuth 2.0) |
| CUS-2 | Create a delivery request with pickup address, drop-off address and parcel size/weight |
| CUS-3 | View an estimated delivery fee before confirming |
| CUS-4 | Track parcel status in real time through the sequence **Created → Picked Up → In Transit → Out for Delivery → Delivered / Failed** |
| CUS-5 | View delivery history and download a delivery receipt |
| CUS-6 | Mobile-friendly tracking page accessible via a public tracking number |

## 3. Merchant requirements

Brief: "Assignment Overview, 2. Merchant (Business) Functionality".

| ID | Requirement |
|---|---|
| MER-1 | Merchant registration with an admin approval step |
| MER-2 | Create delivery orders via a secured API using **API keys or OAuth 2.0 client credentials** |
| MER-3 | Receive status updates through webhooks when a parcel status changes |
| MER-4 | Dashboard showing all orders and their delivery states |

## 4. Courier requirements

Brief: "Assignment Overview, 3. Courier Functionality".

| ID | Requirement |
|---|---|
| COU-1 | Login and authentication |
| COU-2 | View assigned deliveries for the day |
| COU-3 | Update parcel status at each stage, with a delivery note or proof (for example recipient name or photo upload) |
| COU-4 | Mark failed deliveries with a reason |

## 5. Dispatcher/Admin requirements

Brief: "Assignment Overview, 4. Dispatcher/Admin Functionality".

| ID | Requirement |
|---|---|
| ADM-1 | View all unassigned parcels and assign them to couriers |
| ADM-2 | Monitor courier workloads (number of active deliveries per courier) |
| ADM-3 | Manage users, merchants, and delivery zones/pricing |
| ADM-4 | View basic reports: deliveries per day, success/failure rate |

## 6. System requirements

Brief: "Assignment Overview, 5. System Requirements".

| ID | Requirement |
|---|---|
| SYS-1 | Real-time updates using WebSockets, server-sent events or polling |
| SYS-2 | Role-based access control for Customer, Merchant, Courier and Dispatcher/Admin |
| SYS-3 | JWT-based authentication for the application, plus OAuth 2.0 for social login or merchant API access |
| SYS-4 | At least one asynchronous integration pattern (for example publish/subscribe via a message broker such as RabbitMQ or Kafka) for status-change events, which trigger notifications and merchant webhooks |
| SYS-5 | A notification service (email or a mock SMS service) that consumes status events |
| SYS-6 | Containerized deployment: frontend, backend services, database and message broker |
| SYS-7 | Secure handling of personal data (addresses, phone numbers): input validation, hashed passwords, no sensitive data in error messages or logs |

## 7. Design documentation

Brief: "Requirements, 1. Design Documentation (20 marks)".

| ID | Deliverable |
|---|---|
| DES-1 | Use case diagrams for customer, merchant, courier and dispatcher interactions |
| DES-2 | ER diagram covering users, roles, merchants, parcels, status history, couriers, zones and pricing |
| DES-3 | Architecture proposal (for example layered, microservices, or modular monolith with a BFF) with justification, clearly showing the presentation, business logic and data layers |
| DES-4 | Integration design: which integration patterns were used (request/reply, publish/subscribe, broker, etc.) and why |
| DES-5 | Sequence/activity diagrams for: creating a delivery, merchant API order creation, courier status update triggering notifications, and the OAuth 2.0 login flow |
| DES-6 | A short note on the design patterns and principles applied (for example SOLID, DRY, Repository, Strategy for pricing, Observer for events) |

## 8. Implementation

Brief: "Requirements, 2. Implementation (50 marks)".

| ID | Requirement |
|---|---|
| IMP-1 | Frontend: responsive UI using React, Angular or Vue |
| IMP-2 | Backend: core logic using a suitable framework (Spring Boot, Node.js/Express, Django, etc.) |
| IMP-3 | APIs: secure REST or GraphQL APIs with proper versioning, validation and centralized error handling |
| IMP-4 | Integration: message broker for status events, a notification consumer, and merchant webhooks with retry on failure |
| IMP-5 | Real-time updates: live parcel tracking via WebSockets/SSE |
| IMP-6 | Code quality: meaningful exception handling, no duplicated logic, configuration via environment variables (no hard-coded secrets) |
| IMP-7 | Containerization: Dockerize all services, and use docker-compose for local orchestration |

## 9. Testing

Brief: "Requirements, 3. Testing (15 marks)".

| ID | Requirement |
|---|---|
| TST-1 | Unit and integration tests for backend services, including at least one test for the event/integration flow |
| TST-2 | Tests for authorization rules (for example, a courier cannot access another courier's deliveries) |
| TST-3 | Measurable code coverage, with test results included in the submission |

## 10. Presentation/Demo

Brief: "Requirements, 4. Presentation/Demo (15 marks)".

| ID | Requirement |
|---|---|
| DEM-1 | Live demo: customer login (including Google login), delivery creation and real-time tracking |
| DEM-2 | Live demo: merchant creating an order via the API and receiving a webhook |
| DEM-3 | Live demo: dispatcher assigning a parcel; courier updating status |
| DEM-4 | Live demo: notification triggered by a status change |
| DEM-5 | Walkthrough of architecture, diagrams, integration choices and security decisions |
| DEM-6 | Each member can explain the part they contributed |

## 11. Bonus marks (optional)

Brief: "Bonus Marks". These are **optional**; they are not mandatory requirements.

| ID | Bonus |
|---|---|
| BON-1 | Kubernetes deployment (+5 marks): deploy the full system (frontend, backend services, database and broker) on Kubernetes using ConfigMaps, Secrets, Service definitions and Ingress. Helm charts are a plus. |
| BON-2 | AI Delivery Assistant (+5 marks): a Generative AI chatbot that answers queries such as "Where is my parcel?" or "How much to send 2 kg to Kandy?" by calling the backend APIs. Exposing these capabilities as MCP tools is encouraged. |

BON-1 conflicts with the current project rule that excludes Kubernetes (see
[ADR 0001](adr/0001-modular-monolith-in-a-monorepo.md) and the
[agent guidelines](ai-agent-guidelines.md#project-rules)). Pursuing it needs a separate, explicitly
reviewed ADR.

## 12. Submission requirements

Brief: "Submission Requirements".

| ID | Item |
|---|---|
| SUB-1 | Source code repository link, with a README containing setup instructions |
| SUB-2 | Design documentation (PDF) |
| SUB-3 | Test reports and coverage summary |
| SUB-4 | A contribution sheet listing each member's responsibilities |

## Not specified by the brief

The brief does not define the following. They are open team decisions, tracked in
[docs/architecture.md](architecture.md#14-open-questions):

- How courier and dispatcher/admin accounts are created (only customer signup and merchant
  registration are stated).
- The meaning of "assigned deliveries for the day" (there is no scheduling concept).
- Report definitions: which date "deliveries per day" counts by, and how the success/failure rate
  is calculated.
- The fee formula, the inputs beyond addresses and size/weight, and how delivery zones are defined.
- The receipt format, photo-upload limits and storage, and whether proof is mandatory.
- Status rules beyond the sequence (cancellation, reassignment, retries after failure, overrides,
  the stages from which a delivery can fail).
- The notification recipients and whether email or mock SMS is used.
- A code-coverage threshold and the deployment target beyond docker-compose.

## 13. Requirement-to-module mapping (team decision)

**Team architecture decision, not an assignment requirement.** The mapping follows the modules
recorded in the accepted [ADR 0003](adr/0003-backend-modules-and-boundaries.md). None of these
modules is implemented yet.

| Requirements | Owning module(s) |
|---|---|
| CUS-1, COU-1, SYS-2, SYS-3 (application JWT, Google login), ADM-3 (users) | `identity` |
| CUS-3, ADM-3 (zones/pricing) | `pricing` |
| CUS-2, CUS-4 (statuses and history), CUS-5, CUS-6 (lookup), COU-2, COU-3, COU-4, ADM-2 (counts), ADM-4 | `delivery` |
| ADM-1, ADM-2 (workload view) | `dispatch` (records the assignment in `delivery`) |
| MER-1, MER-2, MER-3, MER-4, ADM-3 (merchants), IMP-4 (webhook retries) | `merchant` |
| SYS-5, IMP-4 (notification consumer), DEM-4 | `notification` |
| SYS-1, IMP-5, CUS-4 and CUS-6 (live updates) | `tracking` |
| SYS-4 (status events) | published by `delivery`, consumed by `notification` and `merchant` |
| SYS-7, IMP-3, IMP-6 | all modules; error format in the technical `shared` package |
| SYS-6, IMP-7 | infrastructure (`compose.yaml` and Dockerfiles), not a module |
| DES-*, TST-*, DEM-*, SUB-* | team deliverables across the project |
