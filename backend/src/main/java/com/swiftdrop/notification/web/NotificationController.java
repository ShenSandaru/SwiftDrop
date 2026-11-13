package com.swiftdrop.notification.web;

import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.notification.application.NotificationQueryService;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * REST controller exposing in-app notifications for authenticated users.
 * Endpoints:
 * - GET /api/v1/notifications
 * - GET /api/v1/notifications/unread-count
 * - PATCH /api/v1/notifications/{id}/read
 * - POST /api/v1/notifications/read-all
 */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationQueryService queryService;

    public NotificationController(NotificationQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    /**
     * Retrieves the paginated notification feed for the authenticated caller.
     */
    @GetMapping
    public PageResponse<NotificationResponse> getFeed(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        UUID recipientUserId = Callers.userId(jwt);
        int pageNumber = page != null ? PageLimit.clampPage(page) : PageLimit.DEFAULT_PAGE_NUMBER;
        int pageSize = size != null ? PageLimit.clampSize(size) : PageLimit.DEFAULT_PAGE_SIZE;

        Page<NotificationRecord> records = queryService.getFeed(recipientUserId, unreadOnly, pageNumber, pageSize);
        return PageResponse.of(
                records.getContent().stream().map(NotificationResponse::from).toList(),
                records.getNumber(),
                records.getSize(),
                records.getTotalElements());
    }

    /**
     * Returns the unread notification count for the authenticated caller.
     */
    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        UUID recipientUserId = Callers.userId(jwt);
        return new UnreadCountResponse(queryService.getUnreadCount(recipientUserId));
    }

    /**
     * Marks a specific notification as read.
     * Returns 404 if nonexistent or owned by another user. Idempotent.
     */
    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID recipientUserId = Callers.userId(jwt);
        NotificationRecord record = queryService.markAsRead(id, recipientUserId);
        return NotificationResponse.from(record);
    }

    /**
     * Marks all unread in-app notifications as read for the authenticated caller.
     */
    @PostMapping("/read-all")
    public BulkReadResponse markAllAsRead(@AuthenticationPrincipal Jwt jwt) {
        UUID recipientUserId = Callers.userId(jwt);
        int updated = queryService.markAllAsRead(recipientUserId);
        return new BulkReadResponse(updated);
    }
}

