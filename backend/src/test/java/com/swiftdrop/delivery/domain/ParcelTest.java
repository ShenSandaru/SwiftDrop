package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.pricing.api.ParcelSize;

class ParcelTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
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

    @ParameterizedTest
    @ValueSource(strings = {"0771234567", "+0771234567", "+94 77 123 4567", "+9411", "+947712345678901234", "abc"})
    void phoneMustBeInInternationalFormat(String phone) {
        assertThatThrownBy(() -> new ParcelAddress("Nimal", phone, "12 Temple Rd", null, "Colombo", UUID.randomUUID(),
                null)).isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(phone);
    }

    @Test
    void addressFieldsRespectColumnLengths() {
        UUID zone = UUID.randomUUID();
        assertThat(new ParcelAddress("n".repeat(200), "+94771234567", "l".repeat(200), "l".repeat(200),
                "c".repeat(100), zone, "1".repeat(10)).getCity()).hasSize(100);
        assertThatThrownBy(() -> new ParcelAddress("n".repeat(201), "+94771234567", "12 Temple Rd", null, "Colombo",
                zone, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParcelAddress("Nimal", "+94771234567", "12 Temple Rd", "l".repeat(201), "Colombo",
                zone, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParcelAddress("Nimal", "+94771234567", "12 Temple Rd", null, "c".repeat(101),
                zone, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParcelAddress("Nimal", "+94771234567", "12 Temple Rd", null, "Colombo",
                zone, "1".repeat(11))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptionIsLimitedTo200Characters() {
        assertThat(Parcel.forCustomer("SD7K3M9Q2XWB", CUSTOMER_ID, address("Colombo"), address("Kandy"),
                ParcelSize.SMALL, 1000, "d".repeat(200), FEE, NOW).getDescription()).hasSize(200);
        assertThatThrownBy(() -> Parcel.forCustomer("SD7K3M9Q2XWB", CUSTOMER_ID, address("Colombo"), address("Kandy"),
                ParcelSize.SMALL, 1000, "d".repeat(201), FEE, NOW)).isInstanceOf(IllegalArgumentException.class);
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

    @Test
    void assignedCourierWalksTheParcelToDeliveredWithConsecutiveSequences() {
        Parcel parcel = assignedParcel();
        Instant later = NOW.plusSeconds(60);

        parcel.changeStatus(ParcelStatus.PICKED_UP, COURIER_ID, null, false, later);
        parcel.changeStatus(ParcelStatus.IN_TRANSIT, COURIER_ID, null, false, later);
        parcel.changeStatus(ParcelStatus.OUT_FOR_DELIVERY, COURIER_ID, null, false, later);
        parcel.changeStatus(ParcelStatus.DELIVERED, COURIER_ID, null, true, later);

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(parcel.getLastSequence()).isEqualTo(5);
        assertThat(parcel.getUpdatedAt()).isEqualTo(later);
        assertThat(parcel.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void otherCourierOrUnassignedParcelLooksNotFound() {
        Parcel assigned = assignedParcel();
        assertThatThrownBy(() -> assigned.changeStatus(ParcelStatus.PICKED_UP, UUID.randomUUID(), null, false, NOW))
                .isInstanceOf(ParcelNotFoundException.class);

        Parcel unassigned = customerParcel(1000);
        assertThatThrownBy(() -> unassigned.changeStatus(ParcelStatus.PICKED_UP, COURIER_ID, null, false, NOW))
                .isInstanceOf(ParcelNotFoundException.class);
        assertThat(assigned.getLastSequence()).isEqualTo(1);
    }

    @Test
    void skippingAStepIsAConflictAndChangesNothing() {
        Parcel parcel = assignedParcel();

        assertThatThrownBy(() -> parcel.changeStatus(ParcelStatus.IN_TRANSIT, COURIER_ID, null, false, NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessage("Illegal status transition");
        assertThatThrownBy(() -> parcel.changeStatus(ParcelStatus.DELIVERED, COURIER_ID, null, true, NOW))
                .isInstanceOf(ParcelConflictException.class);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(parcel.getLastSequence()).isEqualTo(1);
    }

    @Test
    void failingRequiresAReasonAndOnlyFailingAcceptsOne() {
        Parcel parcel = assignedParcel();
        parcel.changeStatus(ParcelStatus.PICKED_UP, COURIER_ID, null, false, NOW);

        assertThatThrownBy(() -> parcel.changeStatus(ParcelStatus.FAILED, COURIER_ID, null, false, NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessage("A failure reason is required");
        assertThatThrownBy(() -> parcel.changeStatus(ParcelStatus.IN_TRANSIT, COURIER_ID, FailureReason.OTHER,
                false, NOW)).isInstanceOf(IllegalArgumentException.class);

        parcel.changeStatus(ParcelStatus.FAILED, COURIER_ID, FailureReason.DAMAGED, false, NOW);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.FAILED);
        assertThat(parcel.getLastSequence()).isEqualTo(3);
    }

    @Test
    void deliveringRequiresProof() {
        Parcel parcel = outForDelivery();

        assertThatThrownBy(() -> parcel.changeStatus(ParcelStatus.DELIVERED, COURIER_ID, null, false, NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessage("Proof of delivery is required");
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void finalStatusesAcceptNoFurtherChange() {
        Parcel delivered = outForDelivery();
        delivered.changeStatus(ParcelStatus.DELIVERED, COURIER_ID, null, true, NOW);
        Parcel failed = outForDelivery();
        failed.changeStatus(ParcelStatus.FAILED, COURIER_ID, FailureReason.REFUSED, false, NOW);

        for (Parcel parcel : new Parcel[] {delivered, failed}) {
            for (ParcelStatus target : ParcelStatus.values()) {
                FailureReason reason = target == ParcelStatus.FAILED ? FailureReason.OTHER : null;
                assertThatThrownBy(() -> parcel.changeStatus(target, COURIER_ID, reason, true, NOW))
                        .isInstanceOf(ParcelConflictException.class)
                        .hasMessage("Parcel is already in a final status");
            }
        }
    }

    private static Parcel outForDelivery() {
        Parcel parcel = assignedParcel();
        parcel.changeStatus(ParcelStatus.PICKED_UP, COURIER_ID, null, false, NOW);
        parcel.changeStatus(ParcelStatus.IN_TRANSIT, COURIER_ID, null, false, NOW);
        parcel.changeStatus(ParcelStatus.OUT_FOR_DELIVERY, COURIER_ID, null, false, NOW);
        return parcel;
    }

    // Assignment arrives with ParcelAssignment (Phase 5); set the fields directly until then
    private static Parcel assignedParcel() {
        Parcel parcel = customerParcel(1000);
        ReflectionTestUtils.setField(parcel, "assignedCourierId", COURIER_ID);
        ReflectionTestUtils.setField(parcel, "assignedAt", NOW);
        return parcel;
    }

    private static Parcel customerParcel(int weightGrams) {
        return Parcel.forCustomer("SD7K3M9Q2XWB", CUSTOMER_ID, address("Colombo"), address("Kandy"),
                ParcelSize.MEDIUM, weightGrams, "Books", FEE, NOW);
    }

    private static ParcelAddress address(String city) {
        return new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, city, UUID.randomUUID(), "00500");
    }
}
