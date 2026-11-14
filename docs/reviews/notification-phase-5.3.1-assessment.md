# Phase 5.3.1 Technical Assessment & Implementation Design: Notification REST API

**Target Module**: `notification` (`web` package and application query services)  
**Related Modules**: `identity.api`  
**Framework Versions**: Spring Boot 4.1.2, Spring Security 7.0.10, Java 21  
**Status**: Technical Assessment & Implementation Design (Read-Only)  
**Date**: November 2026

---

## 1. Context and Objective

In Phases 5.1 through 5.2.5, we implemented and verified the backend ingestion pipeline:
- Event envelope parsing and idempotency.
- PostgreSQL sequence watermarks and gap reconciliation.
- RabbitMQ listener container with method-level transaction isolation (Pattern A).
- Background pending-event reconciliation worker.
- Persistent storage of user-facing notifications in `notification_records`.

Currently, the `notification` module has no web package or REST endpoints.
**Phase 5.3.1 Objective**: Design secure, idiomatic REST endpoints for authenticated users to:
1. Retrieve their paginated notification feed (sorted newest first).
2. Query their unread notification count badge (backed by PostgreSQL partial index).
3. Mark an individual notification as read with strict recipient-ownership verification.
4. Mark all unread notifications as read for the authenticated user.

---

## 2. API Design & Endpoint Contracts

All endpoints will be mapped under `/api/v1/notifications` conforming to project conventions (see `com.swiftdrop.delivery.web.ParcelController` and `com.swiftdrop.shared.web`).

### 2.1 Endpoint 1: Get Notification Feed
- **Method / Path**: `GET /api/v1/notifications`
- **Security**: `@PreAuthorize("isAuthenticated()")`
- **Query Parameters**:
  - `page`: optional integer (default: `0`, clamped via `PageLimit.clampPage(page)`)
  - `size`: optional integer (default: `20`, clamped via `PageLimit.clampSize(size)`, max: `100`)
  - `unreadOnly`: optional boolean (default: `false`). When `true`, filters unread in-app notifications.
- **Processing**:
  - Resolve caller `UUID recipientUserId` directly from `@AuthenticationPrincipal Jwt jwt` (`UUID.fromString(jwt.getSubject())`). Never accept a `userId` parameter from the client.
  - Query repository with `PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))`.
  - Maps to `ix_notification_records_user_feed (recipient_user_id, created_at DESC)`.
- **Response**: `200 OK` with `PageResponse<NotificationResponse>`:
  ```json
  {
    "items": [
      {
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "parcelId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "trackingNumber": "SD1234567890",
        "title": "Out for Delivery",
        "message": "Your parcel SD1234567890 is out for delivery (Attempt 1).",
        "channel": "IN_APP",
        "isRead": false,
        "readAt": null,
        "createdAt": "2026-11-13T10:15:30Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalItems": 1,
    "totalPages": 1
  }
  ```

### 2.2 Endpoint 2: Get Unread Notification Count
- **Method / Path**: `GET /api/v1/notifications/unread-count`
- **Security**: `@PreAuthorize("isAuthenticated()")`
- **Processing**:
  - Resolve `UUID recipientUserId = Callers.userId(jwt)`.
  - Invokes `recordRepository.countByRecipientUserIdAndChannelAndIsReadFalse(recipientUserId, NotificationChannel.IN_APP)`.
  - Leverages the partial index `ix_notification_records_user_unread (recipient_user_id) WHERE channel = 'IN_APP' AND is_read = FALSE`.
- **Response**: `200 OK`:
  ```json
  {
    "unreadCount": 3
  }
  ```

### 2.3 Endpoint 3: Mark Single Notification as Read
- **Method / Path**: `PATCH /api/v1/notifications/{id}/read`
- **Security**: `@PreAuthorize("isAuthenticated()")`
- **Path Variable**: `UUID id`
- **Processing**:
  - Resolve `UUID recipientUserId = Callers.userId(jwt)`.
  - Atomically fetch notification by `id` AND `recipientUserId`:
    `recordRepository.findByIdAndRecipientUserId(id, recipientUserId)`
  - **Ownership & 404 Security Rule**: If the record does not exist OR belongs to another user, return `404 Not Found` (via `ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found")`). Never return 403 Forbidden to prevent leaking the existence of other users' notifications!
  - **Idempotency**: If `record.isRead()` is already `true`, return `200 OK` without database mutation.
  - If `false`, call `record.markAsRead(clock.instant())`, save, and return `200 OK` with updated `NotificationResponse`.
- **Response**: `200 OK` with `NotificationResponse`.

### 2.4 Endpoint 4: Mark All Notifications as Read
- **Method / Path**: `POST /api/v1/notifications/read-all`
- **Security**: `@PreAuthorize("isAuthenticated()")`
- **Processing**:
  - Resolve `UUID recipientUserId = Callers.userId(jwt)`.
  - Executes bulk update query in repository:
    `recordRepository.markAllAsReadForUser(recipientUserId, clock.instant())`.
  - Returns count of updated records.
- **Response**: `200 OK`:
  ```json
  {
    "updatedCount": 3
  }
  ```

---

## 3. Security, Authentication & User-ID Resolution

### 3.1 Security Filter Chain & Token Resolution
- SwiftDrop uses stateless JWT bearer tokens validated by `com.swiftdrop.config.SecurityConfiguration`.
- In `SecurityConfiguration.java`:
  ```java
  .requestMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/auth/tokens", ...).permitAll()
  .anyRequest().authenticated()
  ```
  Any endpoint under `/api/v1/notifications` is automatically protected and requires a valid bearer token.
- **User-ID Resolution**:
  The JWT subject contains the authenticated user's UUID.
  We will introduce `Callers.userId(Jwt jwt)` within the notification web layer:
  ```java
  final class Callers {
      private Callers() {}
      static UUID userId(Jwt jwt) {
          return UUID.fromString(jwt.getSubject());
      }
  }
  ```
  Client requests cannot provide, override, or spoof the user ID.

### 3.2 Authorization & Anti-Tampering Protections
- **Zero Cross-Tenant Leakage**:
  Every query method in `NotificationRecordRepository` or `NotificationQueryService` requires `recipientUserId` as a mandatory parameter derived from the security context:
  - `findByRecipientUserId(callerId, pageable)`
  - `findByIdAndRecipientUserId(notificationId, callerId)`
  - `countByRecipientUserIdAndChannelAndIsReadFalse(callerId, IN_APP)`
  - `markAllAsReadForUser(callerId, readAt)`
- An attacker guessing or iterating UUIDs cannot read, count, or modify any other user's notification records.

---

## 4. Repository & Database Requirements

### 4.1 Required Repository Methods in `NotificationRecordRepository`
Inspect existing `NotificationRecordRepository`:
```java
// Already exists:
Page<NotificationRecord> findByRecipientUserId(UUID recipientUserId, Pageable pageable);
Page<NotificationRecord> findByRecipientUserIdAndChannelAndIsReadFalse(UUID recipientUserId, NotificationChannel channel, Pageable pageable);
long countByRecipientUserIdAndChannelAndIsReadFalse(UUID recipientUserId, NotificationChannel channel);
```
**Methods to Add**:
1. Single notification lookup scoped by owner:
   ```java
   Optional<NotificationRecord> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);
   ```
2. Bulk mark-all-as-read:
   ```java
   @Modifying
   @Query("UPDATE NotificationRecord r SET r.isRead = true, r.readAt = :readAt "
        + "WHERE r.recipientUserId = :recipientUserId AND r.isRead = false AND r.channel = 'IN_APP'")
   int markAllAsReadForRecipient(@Param("recipientUserId") UUID recipientUserId, @Param("readAt") Instant readAt);
   ```

### 4.2 Database Migrations Evaluation
- Does Phase 5.3.1 require new Flyway migrations?
  **No.**
  Migration `V202611131000__notification_create_tables.sql` already created:
  - `ck_notification_records_read_consistency CHECK ((is_read = TRUE) = (read_at IS NOT NULL))`
  - Index 1: `ix_notification_records_user_feed (recipient_user_id, created_at DESC)`
  - Index 2: `ix_notification_records_user_unread (recipient_user_id) WHERE channel = 'IN_APP' AND is_read = FALSE`
  All proposed queries are 100% covered by these existing indexes and constraints.

---

## 5. Architecture & Module Boundary Verification

- **Module Boundary Compliance (ADR 0003)**:
  - The notification web package will reside in `com.swiftdrop.notification.web`.
  - It depends only on:
    - `com.swiftdrop.notification.application`
    - `com.swiftdrop.notification.domain`
    - `com.swiftdrop.shared.web` (`PageResponse`, `PageLimit`, `GlobalExceptionHandler`)
    - Spring Security and OAuth2 JWT classes.
  - No foreign packages (such as `delivery.internal` or `identity.internal`) are imported.
  - Compliant with `ModuleBoundaryTest`.

---

## 6. Implementation Scope: Classes to Create or Modify

### 6.1 Files to Create
1. **`com.swiftdrop.notification.web.NotificationResponse.java`**
   - Record DTO returned by feed and read endpoints. Exposes: `id`, `parcelId`, `trackingNumber`, `title`, `message`, `channel`, `isRead`, `readAt`, `createdAt`.
2. **`com.swiftdrop.notification.web.UnreadCountResponse.java`**
   - Record DTO: `record UnreadCountResponse(long unreadCount)`.
3. **`com.swiftdrop.notification.web.BulkReadResponse.java`**
   - Record DTO: `record BulkReadResponse(int updatedCount)`.
4. **`com.swiftdrop.notification.web.Callers.java`**
   - Helper to extract `UUID userId(Jwt jwt)`.
5. **`com.swiftdrop.notification.application.NotificationQueryService.java`**
   - Service coordinating read operations and bulk updates with `@Transactional(readOnly = true)` and `@Transactional`.
6. **`com.swiftdrop.notification.web.NotificationController.java`**
   - `@RestController` exposing the 4 endpoints under `/api/v1/notifications`.
7. **`com.swiftdrop.notification.web.NotificationControllerTest.java`** (Unit Test)
   - Spring MVC mock tests verifying authorization rules, DTO mapping, and parameter clamping.
8. **`com.swiftdrop.notification.web.NotificationControllerIT.java`** (Integration Test)
   - End-to-end integration tests using real PostgreSQL Testcontainers and MockMvc with valid JWT tokens:
     - Feed retrieval with pagination.
     - Unread count calculation.
     - Single mark-as-read and 404 on accessing another user's notification.
     - Mark-all-as-read.

### 6.2 Files to Modify
1. **`com.swiftdrop.notification.domain.NotificationRecordRepository.java`**
   - Add `findByIdAndRecipientUserId` and `@Modifying @Query markAllAsReadForRecipient`.

---

## 7. Error Handling & Edge Cases

| Scenario | Handled Response |
|---|---|
| Unauthenticated request (no JWT token) | `401 Unauthorized` (handled by Spring Security) |
| User requests another user's notification ID | `404 Not Found` (never 403, preventing ID enumeration) |
| Non-existent notification ID | `404 Not Found` |
| Negative or excessive page/size parameters | Clamped automatically via `PageLimit.clampPage` and `PageLimit.clampSize` |
| Notification already marked as read | Idempotent `200 OK` with existing `readAt` timestamp |
| Mark all as read when 0 unread exist | `200 OK` with `updatedCount: 0` |

---

## 8. Implementation Recommendation

The proposed Phase 5.3.1 design:
- Completes the missing user-facing presentation layer for in-app notifications.
- Fully aligns with project conventions in `delivery.web.ParcelController`.
- Requires **zero database schema modifications**.
- Enforces strict tenant isolation and prevents ID enumeration.

Proceed to implementation of Phase 5.3.1 upon approval.

