package com.swiftdrop.merchant.application;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSummary;

/**
 * Merchant order intake (SD-405). Parcels, fees, validation and idempotency per order reference stay
 * in {@code delivery} (D-25); this service only passes orders on for an API-key-authenticated merchant.
 */
@Service
public class MerchantOrderService {

    private static final Logger log = LoggerFactory.getLogger(MerchantOrderService.class);

    private final ParcelCreation parcelCreation;
    private final ParcelQueries parcelQueries;

    public MerchantOrderService(ParcelCreation parcelCreation, ParcelQueries parcelQueries) {
        this.parcelCreation = parcelCreation;
        this.parcelQueries = parcelQueries;
    }

    /**
     * Creates the parcel, or returns the existing one if this merchant already used the reference.
     *
     * @throws IllegalArgumentException if a field is missing or invalid
     */
    public CreatedParcel create(NewMerchantParcel order) {
        CreatedParcel parcel = parcelCreation.createMerchantParcel(order);
        if (parcel.newlyCreated()) {
            log.info("Merchant order created: merchantId={}, merchantOrderReference={}, parcelId={}",
                    order.merchantId(), order.merchantOrderReference(), parcel.parcelId());
        }
        return parcel;
    }

    /** The merchant's own order with this reference; another merchant's order is not found. */
    public Optional<ParcelSummary> find(UUID merchantId, String merchantOrderReference) {
        return parcelQueries.findByMerchantReference(merchantId, merchantOrderReference);
    }
}
