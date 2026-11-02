package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.pricing.api.ParcelSize;

class ParcelTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");
    private static final Parcel.Fee FEE = new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID());

    @Test
    void customerParcelStartsCreatedWithSequenceOneAndCustomerAsOwnerAndCreator() {
        Parcel parcel = customerParcel(2000);

        assertThat(parcel.getSource()).isEqualTo(ParcelSource.CUSTOMER);
        assertThat(parcel.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(parcel.getMerchantId()).isNull();
        assertThat(parcel.getMerchantOrderReference()).isNull();
        assertThat(parcel.getCreatedBy()).isEqualTo(CUSTOMER_ID);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(parcel.getLastSequence()).isEqualTo(1);
        assertThat(parcel.getAssignedCourierId()).isNull();
        assertThat(parcel.getFeeAmount()).isEqualByComparingTo("650.00");
        assertThat(parcel.getFeeCurrency()).isEqualTo("LKR");
        assertThat(parcel.getCreatedAt()).isEqualTo(NOW);
        assertThat(parcel.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void merchantParcelHasMerchantOwnerTrimmedReferenceAndNoCreatingUser() {
        Parcel parcel = Parcel.forMerchant("SD7K3M9Q2XWB", MERCHANT_ID, "  ORD-10045 ", address("Colombo"),
                address("Kandy"), ParcelSize.SMALL, 1000, null, FEE, NOW);

        assertThat(parcel.getSource()).isEqualTo(ParcelSource.MERCHANT);
        assertThat(parcel.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(parcel.getMerchantOrderReference()).isEqualTo("ORD-10045");
        assertThat(parcel.getCustomerId()).isNull();
        assertThat(parcel.getCreatedBy()).isNull();
    }

    @Test
    void merchantParcelRequiresOrderReference() {
        assertThatThrownBy(() -> Parcel.forMerchant("SD7K3M9Q2XWB", MERCHANT_ID, " ", address("Colombo"),
                address("Kandy"), ParcelSize.SMALL, 1000, null, FEE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Parcel.forMerchant("SD7K3M9Q2XWB", MERCHANT_ID, null, address("Colombo"),
                address("Kandy"), ParcelSize.SMALL, 1000, null, FEE, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 30_001})
    void weightOutsideLimitsIsRejected(int weightGrams) {
        assertThatThrownBy(() -> customerParcel(weightGrams)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 30_000})
    void weightLimitsAreAccepted(int weightGrams) {
        assertThat(customerParcel(weightGrams).getWeightGrams()).isEqualTo(weightGrams);
    }

    @Test
    void blankDescriptionIsStoredAsNull() {
        Parcel parcel = Parcel.forCustomer("SD7K3M9Q2XWB", CUSTOMER_ID, address("Colombo"), address("Kandy"),
                ParcelSize.SMALL, 1000, "   ", FEE, NOW);

        assertThat(parcel.getDescription()).isNull();
    }

    @Test
    void feeRejectsNegativeAmountsExtraDecimalsAndBadCurrency() {
        UUID ruleId = UUID.randomUUID();
        assertThatThrownBy(() -> new Parcel.Fee(new BigDecimal("-1.00"), "LKR", ruleId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Parcel.Fee(new BigDecimal("1.005"), "LKR", ruleId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Parcel.Fee(new BigDecimal("1.00"), "LK", ruleId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addressTrimsTextAndRejectsBlankRequiredFields() {
        ParcelAddress trimmed = new ParcelAddress(" Nimal ", " +94771234567 ", " 12 Temple Rd ", " ", " Colombo ",
                UUID.randomUUID(), "");

        assertThat(trimmed.getContactName()).isEqualTo("Nimal");
        assertThat(trimmed.getLine2()).isNull();
        assertThat(trimmed.getPostalCode()).isNull();
        assertThatThrownBy(() -> new ParcelAddress(" ", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNeverContainsPersonalData() {
        Parcel parcel = Parcel.forCustomer("SD7K3M9Q2XWB", CUSTOMER_ID, address("Colombo"), address("Kandy"),
                ParcelSize.SMALL, 1000, "Books", FEE, NOW);

        for (String text : new String[] {parcel.toString(), parcel.getPickup().toString()}) {
            assertThat(text).doesNotContain("Nimal", "+9477", "Temple", "Colombo", "Kandy", "00500", "Books");
        }
        assertThat(parcel.toString()).contains("SD7K3M9Q2XWB");
    }

    private static Parcel customerParcel(int weightGrams) {
        return Parcel.forCustomer("SD7K3M9Q2XWB", CUSTOMER_ID, address("Colombo"), address("Kandy"),
                ParcelSize.MEDIUM, weightGrams, "Books", FEE, NOW);
    }

    private static ParcelAddress address(String city) {
        return new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, city, UUID.randomUUID(), "00500");
    }
}
