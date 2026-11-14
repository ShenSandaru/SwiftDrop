package com.swiftdrop.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.swiftdrop.pricing.api.ParcelSize;

class NewMerchantParcelTest {

    private static final DeliveryContact CONTACT =
            new DeliveryContact("Nimal", "+94771234567", "1 Main St", null, "Colombo", UUID.randomUUID(), null);

    @Test
    @DisplayName("withSizeName maps every size name to the pricing size")
    void knownSizes() {
        for (ParcelSize size : ParcelSize.values()) {
            assertThat(order(size.name()).size()).isEqualTo(size);
        }
    }

    @Test
    @DisplayName("A missing size is left for delivery's validation")
    void missingSize() {
        assertThat(order(null).size()).isNull();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "small", "HUGE", " SMALL"})
    @DisplayName("Unknown size names are rejected without naming internal classes")
    void unknownSizes(String name) {
        assertThatThrownBy(() -> order(name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be one of [SMALL, MEDIUM, LARGE]");
    }

    private static NewMerchantParcel order(String sizeName) {
        return NewMerchantParcel.withSizeName(UUID.randomUUID(), "ORD-1", CONTACT, CONTACT, sizeName, 500, null);
    }
}
