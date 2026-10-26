package com.swiftdrop.identity.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.identity.application.AccountService;

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

    /** Staff account creation by an administrator. Creates a courier account. */
    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
    UserResponse createStaff(@Valid @RequestBody RegisterUserRequest request) {
        return UserResponse.from(
                accounts.registerCourier(request.fullName(), request.email(), request.password()));
    }

    /** Lists all users. Restricted to administrators. */
    @GetMapping
    @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
    List<UserResponse> listUsers() {
        return accounts.findAllUsers().stream()
                .map(UserResponse::from)
                .toList();
    }

    /** The authenticated user's own account. */
    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return accounts.findById(UUID.fromString(jwt.getSubject()))
                .map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
