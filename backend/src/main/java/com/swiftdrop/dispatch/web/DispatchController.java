package com.swiftdrop.dispatch.web;

import java.util.List;
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
import com.swiftdrop.delivery.api.Staff;
import com.swiftdrop.dispatch.application.DispatchService;
import com.swiftdrop.identity.api.IdentityClaims;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/dispatch")
@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
class DispatchController {

    private final DispatchService dispatch;

    DispatchController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @GetMapping("/parcels")
    PageResponse<DispatchParcelSummary> parcels(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ParcelStatus status,
            @RequestParam(required = false) ParcelSource source,
            @RequestParam(required = false) Boolean assigned,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID hubId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        com.swiftdrop.delivery.api.PageRequest request = pageRequest(page, size);
        return toResponse(dispatch.parcels(status, source, assigned, search, hubId, staff(jwt), request), request);
    }

    @GetMapping("/active-assignments")
    PageResponse<ParcelAssignmentSummary> activeAssignments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam ParcelAssignmentType type,
            @RequestParam(required = false) UUID hubId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        com.swiftdrop.delivery.api.PageRequest request = pageRequest(page, size);
        return toResponse(dispatch.activeAssignments(type, hubId, staff(jwt), request), request);
    }

    @GetMapping("/courier-workloads")
    Map<UUID, Long> courierWorkloads(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID hubId) {
        return dispatch.courierWorkloads(hubId, staff(jwt));
    }

    @GetMapping("/couriers")
    List<DispatchCourierResponse> couriers(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID hubId) {
        return dispatch.activeCouriers(hubId, staff(jwt)).stream()
                .map(DispatchCourierResponse::from)
                .toList();
    }

    @GetMapping("/parcels/{id}/assignment-history")
    ParcelAssignmentHistory assignmentHistory(@PathVariable UUID id) {
        return dispatch.assignmentHistory(id);
    }

    @PostMapping("/parcel-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    ParcelAssignmentSummary assign(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DispatchAssignmentRequest request) {
        return dispatch.assign(request.parcelId(), request.courierId(), staff(jwt), request.type());
    }

    // ---- Hub network (ADR 0009 §3, §6); each answers 204, or 404 / 409 from delivery's rules ----

    /** Scans a parcel in at the hub it is expected at. */
    @PostMapping("/parcels/{id}/hub-scans")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void scanAtHub(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody HubScanRequest request) {
        dispatch.scanAtHub(id, request.hubId(), staff(jwt));
    }

    /** Sends a parcel from its current hub to the next hub of its route. */
    @PostMapping("/parcels/{id}/transit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void sendToNextHub(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        dispatch.sendToNextHub(id, staff(jwt));
    }

    /** Starts the return to the sender after the last allowed attempt or a refusal. */
    @PostMapping("/parcels/{id}/return")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void startReturn(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        dispatch.startReturn(id, staff(jwt));
    }

    /** Ends a parcel as lost or damaged. */
    @PostMapping("/parcels/{id}/failure")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markFailed(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ParcelFailureRequest request) {
        dispatch.markFailed(id, request.reason(), request.note(), staff(jwt));
    }

    /** Cancels a parcel that has not been picked up. */
    @PostMapping("/parcels/{id}/cancellation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        dispatch.cancel(id, staff(jwt));
    }

    /** The caller as recorded in the parcel history: admin if the token has the role, else dispatcher. */
    private static Staff staff(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<String> roles = jwt.getClaimAsStringList(IdentityClaims.ROLES);
        return roles != null && roles.contains("ADMIN") ? Staff.admin(userId) : Staff.dispatcher(userId);
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
