package com.swiftdrop.identity.web;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.identity.application.AccountService;
import com.swiftdrop.identity.application.InvalidRoleException;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final AccountService accounts;

    UserController(AccountService accounts) {
        this.accounts = accounts;
    }

    /** Public customer registration. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return UserResponse.from(
                accounts.registerCustomer(request.fullName(), request.email(), request.password()));
    }

    /** Staff account creation by an administrator (compatibility endpoint). Creates a courier account. */
    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
    UserResponse createStaff(@Valid @RequestBody RegisterUserRequest request) {
        return UserResponse.from(
                accounts.registerCourier(request.fullName(), request.email(), request.password()));
    }

    /**
     * Lists users with pagination and optional role filtering. Restricted to administrators.
     * When page, size, and role are omitted, returns an unpaged list for backwards compatibility.
     */
    @GetMapping
    @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
    ResponseEntity<?> listUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String role
    ) {
        if (page == null && size == null && role == null) {
            return ResponseEntity.ok(accounts.findAllUsers().stream()
                    .map(UserResponse::from)
                    .toList());
        }
        Role roleFilter = null;
        if (role != null && !role.isBlank()) {
            try {
                roleFilter = Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new InvalidRoleException("Invalid role filter: " + role);
            }
        }
        int pageNum = page != null ? Math.max(0, page) : PageLimit.DEFAULT_PAGE_NUMBER;
        int pageSize = size != null ? PageLimit.clampSize(size) : PageLimit.DEFAULT_PAGE_SIZE;
        Page<User> userPage = accounts.findUsers(pageNum, pageSize, roleFilter);
        List<UserResponse> items = userPage.getContent().stream().map(UserResponse::from).toList();
        return ResponseEntity.ok(PageResponse.of(items, userPage.getNumber(), pageSize, userPage.getTotalElements()));
    }

    /**
     * Enables or disables a user account. Restricted to administrators.
     * An administrator cannot disable their own account.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
    UserResponse updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID currentAdminId = UUID.fromString(jwt.getSubject());
        return UserResponse.from(accounts.updateUserStatus(id, request.enabled(), currentAdminId));
    }

    /** The authenticated user's own account. Rejected if the account is disabled. */
    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        User user = accounts.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }
        return UserResponse.from(user);
    }
}
