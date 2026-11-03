# Delivery and tracking authorization matrix

Evidence for **TST-2** (authorization tests, including "a courier cannot access another
courier's deliveries") and backlog item **SD-316**. Every parcel and tracking endpoint is called by
every kind of caller against real data in a real PostgreSQL database.

- Test: `backend/src/test/java/com/swiftdrop/delivery/ParcelAuthorizationMatrixIT.java`
  (59 matrix rows, plus 2 tests on list contents)
- Built on the team's matrix harness (`backend/src/test/java/com/swiftdrop/test/security`)
- Last run: 2026-11-03, **61 tests, 0 failures**

Run it (needs Docker):

```bash
cd backend
./mvnw -Pintegration verify -Dit.test=ParcelAuthorizationMatrixIT
```

## Rules being verified

| Answer | Meaning |
|---|---|
| **200 / 201** | Allowed |
| **401** | Not logged in |
| **403** | The caller's role may never use this endpoint |
| **404** | The parcel exists, but this caller neither owns it nor is assigned to it. The answer is the same as for a parcel that does not exist, so parcel IDs cannot be probed (ADR 0006 §7). |
| **409** | Allowed caller, but the lifecycle rules refuse the step. The matrix sends such steps on purpose, so it can run repeatedly without changing data; successful steps are tested in `CourierFlowIT`. |

## Callers

| Caller | Description |
|---|---|
| Anonymous | No login |
| Owner | The customer who booked the parcel |
| Other customer | Another customer |
| Assigned courier | The courier the parcel is assigned to |
| Other courier | Another courier (the brief's example) |
| Admin | Dispatcher/admin |
| Merchant | Logged-in merchant user |
| Courier + customer | One account with both roles, owning and assigned to nothing: two roles do not add up to more access |

## Results

| Endpoint | Owner | Other customer | Assigned courier | Other courier | Courier + customer | Admin | Merchant | Anonymous |
|---|---|---|---|---|---|---|---|---|
| Book `POST /api/v1/parcels` | 201 | · | 403 | · | 201 | 403 | 403 | 401 |
| List `GET /api/v1/parcels` | 200 (own only) | 200 (own only)\* | 403 | · | · | 200 (all) | 403 | 401 |
| Detail `GET /api/v1/parcels/{id}` | 200 | **404** | 200 | **404** | **404** | 200 | 403 | 401 |
| Receipt `GET …/{id}/receipt` | 200 | **404** | 403 | · | · | 200 | 403 | 401 |
| Status change `POST …/{id}/status-changes` | 403 | · | 409 (rules ran) | **404** | **404** | 403 | 403 | 401 |
| Proof upload `PUT …/{id}/proof` | 403 | · | 409 (rules ran) | **404** | · | 403 | 403 | 401 |
| Proof photo `GET …/{id}/proof/photo` | 200 | **404** | 200 | **404** | · | 200 | 403 | 401 |
| Courier run `GET /api/v1/courier/parcels` | 403 | · | 200 (own only)\* | 200 (own only)\* | · | 403 | 403 | 401 |
| Public tracking `GET /api/v1/tracked-parcels/{n}` | 200 | 200 | · | 200 | · | · | 200 | 200 |
| Live stream `GET …/{n}/events` | · | · | · | · | · | · | · | 200 |
| Unknown tracking number | · | · | · | · | · | · | · | 404 |
| Write to public tracking `POST …/{n}` | · | · | · | · | · | · | · | 401 |

`·` = not a separate row (covered by the same rule in another column). \* checked by the two
content tests: the other customer's list and the other courier's run do not contain the parcels.

## Notes

- Hiding links in the frontend is only for convenience; every rule above is enforced by the
  backend.
- Public tracking returns only status, cities and times, with no names, phones, addresses, notes
  or proof (ADR 0007).
- Spring validates a request body before the role check runs, so a forbidden caller who sends an
  *invalid* body gets 400 instead of 403. Nothing about any parcel is revealed. The matrix uses
  valid bodies so that it tests the role rules themselves. A URL rule in `SecurityConfiguration`
  would make the 403 come first, if the team wants that.
