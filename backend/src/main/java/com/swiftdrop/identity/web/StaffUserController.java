package com.swiftdrop.identity.web;

import java.util.Locale;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.identity.application.AccountService;
import com.swiftdrop.identity.application.InvalidRoleException;
import com.swiftdrop.identity.domain.Role;

/**
 * Administrative controller for staff user creation (SD-102, contract §4.2).
 */
@RestController
@RequestMapping("/api/v1/staff-users")
class StaffUserController {

    private final AccountService accounts;

    StaffUserController(AccountService accounts) {
        this.accounts = accounts;
    }

    /**
     * Creates a staff account (COURIER or DISPATCHER_ADMIN).
     * Restricted to administrators.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
    UserResponse createStaff(@Valid @RequestBody CreateStaffUserRequest request) {
        Role role = Role.COURIER;
        if (request.role() != null && !request.role().isBlank()) {
            try {
                role = Role.valueOf(request.role().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new InvalidRoleException("Staff account role must be COURIER or DISPATCHER_ADMIN");
            }
        }
        return UserResponse.from(
                accounts.registerStaff(request.fullName(), request.email(), request.password(), role));
    }
}
