package com.swiftdrop.identity.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.identity.application.AddressService;
import com.swiftdrop.identity.domain.UserAddress;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
@PreAuthorize("hasRole('CUSTOMER')")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<UserAddressResponse> getAddresses(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return addressService.getAddresses(userId).stream()
                .map(UserAddressResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<UserAddressResponse> createAddress(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAddressRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserAddress created = addressService.createAddress(userId, request);
        return ResponseEntity.created(URI.create("/api/v1/users/me/addresses/" + created.getId()))
                .body(UserAddressResponse.from(created));
    }

    @PutMapping("/{addressId}")
    public UserAddressResponse updateAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId,
            @Valid @RequestBody UpdateAddressRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return UserAddressResponse.from(addressService.updateAddress(userId, addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        addressService.deleteAddress(userId, addressId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{addressId}/default")
    public UserAddressResponse setDefaultAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return UserAddressResponse.from(addressService.setDefaultAddress(userId, addressId));
    }
}

