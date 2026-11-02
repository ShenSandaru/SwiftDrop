package com.swiftdrop.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.swiftdrop.pricing.api.ParcelSize;

/** Invariants of the records other modules receive from {@code delivery.api}. */
class DeliveryApiContractTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");

    @Test
    void contactAndMerchantOrderNeverPrintPersonalData() {
        DeliveryContact contact = new DeliveryContact(
                "Nimal Perera", "+94771234567", "12 Temple Rd", "Flat 2", "Colombo 05", UUID.randomUUID(), "00500");
        NewMerchantParcel order = new NewMerchantParcel(
                UUID.randomUUID(), "ORD-10045", contact, contact, ParcelSize.SMALL, 2000, "Books");

        for (String text : List.of(contact.toString(), order.toString())) {
            assertThat(text).doesNotContain("Nimal", "+9477", "Temple", "Flat 2", "Colombo", "00500", "Books");
        }
        assertThat(order.toString()).contains("ORD-10045");
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 101"})
    void pageRequestRejectsOutOfRangeValues(int page, int size) {
        assertThatThrownBy(() -> new PageRequest(page, size)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageRequestAcceptsLimits() {
        assertThat(new PageRequest(0, 1).size()).isEqualTo(1);
        assertThat(new PageRequest(3, PageRequest.MAX_SIZE).size()).isEqualTo(100);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "20, 1", "21, 2", "57, 3"})
    void pageResultCountsPages(long totalItems, int expectedPages) {
        PageResult<String> result = PageResult.of(List.of(), new PageRequest(0, 20), totalItems);

        assertThat(result.totalPages()).isEqualTo(expectedPages);
        assertThat(result.totalItems()).isEqualTo(totalItems);
    }

    @Test
    void pageResultCopiesItemsAndNeverReturnsNull() {
        List<String> items = new ArrayList<>(List.of("a"));
        PageResult<String> result = PageResult.of(items, new PageRequest(0, 20), 1);
        items.add("b");

        assertThat(result.items()).containsExactly("a");
        assertThat(new PageResult<String>(null, 0, 20, 0, 0).items()).isEmpty();
    }

    @Test
    void publicTrackingViewCopiesHistory() {
        List<PublicStatusEvent> history = new ArrayList<>(List.of(new PublicStatusEvent(ParcelStatus.CREATED, NOW)));
        PublicTrackingView view = new PublicTrackingView("SD7K3M9Q2XWB", ParcelStatus.CREATED, "Colombo", "Kandy", history);
        history.add(new PublicStatusEvent(ParcelStatus.PICKED_UP, NOW));

        assertThat(view.history()).hasSize(1);
        assertThatThrownBy(() -> view.history().add(new PublicStatusEvent(ParcelStatus.FAILED, NOW)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void createdEventUsesEnvelopeConstants() {
        ParcelStatusChanged event = ParcelStatusChanged.of(UUID.randomUUID(), NOW, data(ParcelStatus.DELIVERED, 5, null));

        assertThat(event.eventType()).isEqualTo("parcel.status-changed");
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.producer()).isEqualTo("delivery");
    }

    @Test
    void failedEventRequiresReasonAndOtherStatusesRejectOne() {
        assertThat(data(ParcelStatus.FAILED, 4, FailureReason.REFUSED).failureReasonCode()).isEqualTo(FailureReason.REFUSED);
        assertThatThrownBy(() -> data(ParcelStatus.FAILED, 4, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> data(ParcelStatus.IN_TRANSIT, 3, FailureReason.OTHER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventSequenceStartsAtOne() {
        assertThatThrownBy(() -> data(ParcelStatus.CREATED, 0, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createdParcelRequiresIdentifiersAndFee() {
        assertThat(new CreatedParcel(PARCEL_ID, "SD7K3M9Q2XWB", ParcelStatus.CREATED,
                new BigDecimal("650.00"), "LKR", NOW, true).newlyCreated()).isTrue();
        assertThatThrownBy(() -> new CreatedParcel(PARCEL_ID, "SD7K3M9Q2XWB", ParcelStatus.CREATED,
                null, "LKR", NOW, true)).isInstanceOf(NullPointerException.class);
    }

    private static ParcelStatusChangedData data(ParcelStatus newStatus, int sequence, FailureReason reason) {
        return new ParcelStatusChangedData(PARCEL_ID, "SD7K3M9Q2XWB", null, newStatus, sequence,
                ParcelSource.CUSTOMER, UUID.randomUUID(), null, null, reason);
    }
}
