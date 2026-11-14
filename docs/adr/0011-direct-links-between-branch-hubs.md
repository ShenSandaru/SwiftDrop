# 0011. Direct links between branch hubs

- Status: Proposed
- Date: 2026-11-14

## Context

[ADR 0009](0009-hub-network-delivery-attempts-and-returns.md) §1 routes every parcel between two
branches through the central hub: origin → central → destination. That is simple, but it sends
parcels between neighbouring districts on a long detour. A parcel from Badulla to Monaragala, about
60 km apart, travels to Colombo and back. The same happens for Galle and Matara, and for every other
pair of nearby branches far from the central hub. Delivery is slower and the central hub sorts
parcels that never needed to reach it.

Real couriers run direct line-haul trucks between such branches. SwiftDrop has no way to say that
two branches exchange parcels directly.

## Decision

### 1. Direct links

- `delivery` owns a **hub link** table: two hub IDs, the admin who created the link and the time.
  A link has no direction. Each pair is stored once, with the smaller hub ID first (a check
  constraint), so A–B and B–A cannot both exist.
- A link joins two **different**, **active**, **non-central** hubs. A link to the central hub is
  rejected, because every route to or from it is already direct.
- Admins (`ADMIN`) create and remove links. Dispatchers may read them. Deleting a hub removes its
  links.
- The system does not check distance. Whether a truck runs between two branches depends on roads,
  volume and cost, which the admin knows and the code does not.

### 2. Route rule

ADR 0009 §1 "Route" gains one case, checked before the central hub is used:

- If the origin and destination branches differ, neither is central and **they are linked**, the
  route is origin → destination.
- Otherwise ADR 0009 §1 applies unchanged.

Links do **not chain**. With links A–B and B–C, a parcel from A to C still travels through the
central hub unless A–C is linked too. A route stays at most three hubs long.

The route is still fixed at pickup and stored on the parcel (ADR 0009 §1). Adding or removing a link
changes only parcels picked up afterwards. A return uses the stored route reversed, so a parcel that
travelled directly also returns directly.

### 3. API

- `GET /api/v1/hubs/links` (`ADMIN`, `DISPATCHER`): all links.
- `POST /api/v1/hubs/links` (`ADMIN`): creates a link from `hubId` and `otherHubId`; `201`, or
  `400` for the same hub twice, `404` for an unknown hub, `409` for a central or inactive hub or an
  existing link.
- `DELETE /api/v1/hubs/links/{hubId}/{otherHubId}` (`ADMIN`): `204`, or `404` if the hubs are not
  linked. The order of the two IDs does not matter.

### 4. What does not change

Statuses, transitions, hub scans, attempts, returns, events and public tracking (ADR 0009 §2 to §8)
are unchanged. A direct route has the same shape as a route to or from the central hub today:
`AT_HUB` (origin) → `IN_TRANSIT` → `AT_HUB` (destination). Fees come from the zones (`pricing`) and
do not depend on the route.

This ADR amends only the route rule of ADR 0009 §1. Everything else in ADR 0009 remains in force.

## Alternatives considered

- **Regional sorting hubs** (branch → regional hub → branch). Rejected for now: routes would be
  four or more hubs long, which changes the stored route, the scan rules and the return path. Direct
  links stay valid as a shortcut inside that model if it is added later.
- **Shortest path over the links** (chaining). Rejected for now: it needs a path search and a
  variable-length stored route. One-hop links cover true neighbours, which is the reported problem.
- **Distance-based automatic links.** Rejected: `delivery` has no hub coordinates, and distance
  alone does not say whether a direct truck is worthwhile.
- **Letting the dispatcher choose the route per parcel.** Rejected: two parcels between the same
  branches could travel differently, and a wrong choice would be hard to spot.

## Consequences

- Parcels between linked branches skip the central hub: fewer scans, a shorter tracking timeline
  and less load on the central hub.
- An admin can link two distant branches by mistake. The effect is limited to parcels picked up
  while the link exists, and removing the link undoes it for new parcels.
- Links are maintained by hand. A network with many branches needs many links; multi-hop routing is
  left to a later ADR.
