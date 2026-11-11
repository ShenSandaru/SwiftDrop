package com.swiftdrop.identity.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.identity.domain.UserAddress;
import com.swiftdrop.identity.domain.UserAddressRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.identity.web.CreateAddressRequest;
import com.swiftdrop.identity.web.UpdateAddressRequest;

@Service
public class AddressService {

    private final UserAddressRepository addresses;
    private final UserRepository users;

    public AddressService(UserAddressRepository addresses, UserRepository users) {
        this.addresses = addresses;
        this.users = users;
    }

    public List<UserAddress> getAddresses(UUID userId) {
        return addresses.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
    }

    public UserAddress createAddress(UUID userId, CreateAddressRequest request) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(request, "request");

        if (!users.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        List<UserAddress> existing = addresses.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
        boolean isDefault = request.isDefault() || existing.isEmpty();

        if (isDefault) {
            for (UserAddress a : existing) {
                if (a.isDefault()) {
                    a.setDefault(false);
                    addresses.save(a);
                }
            }
        }

        UserAddress address = new UserAddress(
                userId,
                request.label(),
                request.recipientName(),
                request.phoneNumber(),
                request.streetAddress(),
                request.city(),
                request.postalCode(),
                isDefault);

        return addresses.saveAndFlush(address);
    }

    public UserAddress updateAddress(UUID userId, UUID addressId, UpdateAddressRequest request) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(addressId, "addressId");
        Objects.requireNonNull(request, "request");

        UserAddress address = addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        if (request.isDefault() && !address.isDefault()) {
            for (UserAddress a : addresses.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)) {
                if (a.isDefault() && !a.getId().equals(addressId)) {
                    a.setDefault(false);
                    addresses.save(a);
                }
            }
            address.setDefault(true);
        } else if (!request.isDefault() && address.isDefault()) {
            address.setDefault(false);
        }

        address.setLabel(request.label());
        address.setRecipientName(request.recipientName());
        address.setPhoneNumber(request.phoneNumber());
        address.setStreetAddress(request.streetAddress());
        address.setCity(request.city());
        address.setPostalCode(request.postalCode());

        return addresses.saveAndFlush(address);
    }

    public void deleteAddress(UUID userId, UUID addressId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(addressId, "addressId");

        UserAddress address = addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        addresses.delete(address);
    }

    public UserAddress setDefaultAddress(UUID userId, UUID addressId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(addressId, "addressId");

        UserAddress address = addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        for (UserAddress a : addresses.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)) {
            if (a.isDefault() && !a.getId().equals(addressId)) {
                a.setDefault(false);
                addresses.save(a);
            }
        }
        address.setDefault(true);
        return addresses.saveAndFlush(address);
    }
}

