package com.swiftdrop.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PricingRuleTest {

    private static final UUID ORIGIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void newPricingRuleHasExpectedDefaultsAndInvariants() {
        PricingRule rule = new PricingRule(
                ORIGIN_ID,
                DEST_ID,
                new BigDecimal("450.00"),
                new BigDecimal("100.00")
        );

        assertThat(rule.getOriginZoneId()).isEqualTo(ORIGIN_ID);
        assertThat(rule.getDestinationZoneId()).isEqualTo(DEST_ID);
        assertThat(rule.getBaseFee()).isEqualByComparingTo("450.00");
        assertThat(rule.getPerKgFee()).isEqualByComparingTo("100.00");
        assertThat(rule.getSmallSizeSurcharge()).isEqualByComparingTo("0.00");
        assertThat(rule.getMediumSizeSurcharge()).isEqualByComparingTo("0.00");
        assertThat(rule.getLargeSizeSurcharge()).isEqualByComparingTo("0.00");
        assertThat(rule.getCurrency()).isEqualTo("LKR");
        assertThat(rule.isActive()).isTrue();
        assertThat(rule.getVersion()).isZero();
        assertThat(rule.getCreatedAt()).isNotNull();
        assertThat(rule.getId()).isNull();
    }

    @Test
    void customSurchargesAndCurrencyAreNormalized() {
        PricingRule rule = new PricingRule(
                ORIGIN_ID,
                DEST_ID,
                new BigDecimal("500.00"),
                new BigDecimal("120.00"),
                new BigDecimal("20.00"),
                new BigDecimal("50.00"),
                new BigDecimal("100.00"),
                "  lkr  ",
                false
        );

        assertThat(rule.getSmallSizeSurcharge()).isEqualByComparingTo("20.00");
        assertThat(rule.getMediumSizeSurcharge()).isEqualByComparingTo("50.00");
        assertThat(rule.getLargeSizeSurcharge()).isEqualByComparingTo("100.00");
        assertThat(rule.getCurrency()).isEqualTo("LKR");
        assertThat(rule.isActive()).isFalse();
    }

    @Test
    void rejectsNullOriginOrDestination() {
        assertThatThrownBy(() -> new PricingRule(null, DEST_ID, BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("originZoneId");

        assertThatThrownBy(() -> new PricingRule(ORIGIN_ID, null, BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("destinationZoneId");
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> new PricingRule(ORIGIN_ID, DEST_ID, new BigDecimal("-1.00"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseFee must be non-negative");

        assertThatThrownBy(() -> new PricingRule(ORIGIN_ID, DEST_ID, BigDecimal.TEN, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("perKgFee must be non-negative");

        assertThatThrownBy(() -> new PricingRule(
                ORIGIN_ID, DEST_ID, BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("-5.00"), BigDecimal.ZERO, BigDecimal.ZERO, "LKR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("smallSizeSurcharge must be non-negative");

        assertThatThrownBy(() -> new PricingRule(
                ORIGIN_ID, DEST_ID, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ZERO, new BigDecimal("-10.00"), BigDecimal.ZERO, "LKR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mediumSizeSurcharge must be non-negative");

        assertThatThrownBy(() -> new PricingRule(
                ORIGIN_ID, DEST_ID, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-15.00"), "LKR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("largeSizeSurcharge must be non-negative");
    }

    @Test
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> new PricingRule(
                ORIGIN_ID, DEST_ID, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new PricingRule(
                ORIGIN_ID, DEST_ID, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "LK"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3-character ISO code");

        assertThatThrownBy(() -> new PricingRule(
                ORIGIN_ID, DEST_ID, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "LKRR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3-character ISO code");
    }

    @Test
    void pricingRuleCanBeDeactivatedAndReactivated() {
        PricingRule rule = new PricingRule(ORIGIN_ID, DEST_ID, BigDecimal.TEN, BigDecimal.ONE);
        assertThat(rule.isActive()).isTrue();

        rule.deactivate();
        assertThat(rule.isActive()).isFalse();

        rule.activate();
        assertThat(rule.isActive()).isTrue();
    }
}

