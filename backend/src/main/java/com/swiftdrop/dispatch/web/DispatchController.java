package com.swiftdrop.dispatch.web;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.delivery.api.DispatchParcelSummary;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelAssignmentHistory;
import com.swiftdrop.delivery.api.ParcelAssignmentSummary;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.dispatch.application.DispatchService;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/dispatch")
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
class DispatchController {

    private final DispatchService dispatch;

    DispatchController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @GetMapping("/parcels")
    PageResponse<DispatchParcelSummary> parcels(
            @RequestParam(required = false) ParcelStatus status,
            @RequestParam(required = false) ParcelSource source,
            @RequestParam(required = false) Boolean assigned,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        com.swiftdrop.delivery.api.PageRequest request = pageRequest(page, size);
        return toResponse(dispatch.parcels(status, source, assigned, search, request), request);
    }

    @GetMapping("/awaiting-delivery-assignments")
    PageResponse<DispatchParcelSummary> awaitingDeliveryAssignments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        com.swiftdrop.delivery.api.PageRequest request = pageRequest(page, size);
        return toResponse(dispatch.awaitingDeliveryAssignment(request), request);
    }

    @GetMapping("/active-assignments")
    PageResponse<ParcelAssignmentSummary> activeAssignments(
            @RequestParam ParcelAssignmentType type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        com.swiftdrop.delivery.api.PageRequest request = pageRequest(page, size);
        return toResponse(dispatch.activeAssignments(type, request), request);
    }

    @GetMapping("/courier-workloads")
    Map<UUID, Long> courierWorkloads() {
        return dispatch.courierWorkloads();
    }

    @GetMapping("/parcels/{id}/assignment-history")
    ParcelAssignmentHistory assignmentHistory(@PathVariable UUID id) {
        return dispatch.assignmentHistory(id);
    }

    @PostMapping("/parcel-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    ParcelAssignmentSummary assign(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DispatchAssignmentRequest request) {
        return dispatch.assign(request.parcelId(), request.courierId(), UUID.fromString(jwt.getSubject()),
                request.type());
    }

    private static com.swiftdrop.delivery.api.PageRequest pageRequest(Integer page, Integer size) {
        return new com.swiftdrop.delivery.api.PageRequest(
                page != null ? PageLimit.clampPage(page) : PageLimit.DEFAULT_PAGE_NUMBER,
                size != null ? PageLimit.clampSize(size) : PageLimit.DEFAULT_PAGE_SIZE);
    }

    private static <T> PageResponse<T> toResponse(PageResult<T> result,
            com.swiftdrop.delivery.api.PageRequest request) {
        return PageResponse.of(result.items(), result.page(), request.size(), result.totalItems());
    }
}
