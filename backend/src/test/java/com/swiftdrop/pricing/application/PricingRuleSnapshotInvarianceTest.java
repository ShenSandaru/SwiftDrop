package com.swiftdrop.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

/**
 * Verifies acceptance criterion SD-211 §4:
 * "A rule change does not change the fee of a parcel that already exists."
 * Verifies snapshot semantics per Contract §8.2.
 */
class PricingRuleSnapshotInvarianceTest {

    private static final UUID ORIGIN_ZONE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEST_ZONE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INITIAL_RULE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void updatingPricingRuleDoesNotMutateExistingParcelFee() {
        ZoneRepository zones = mock(ZoneRepository.class);
        PricingRuleRepository rules = mock(PricingRuleRepository.class);
        ZonePairFeeCalculator calculator = new ZonePairFeeCalculator(zones, rules);

        Zone origin = new Zone("CMB", "Colombo", true);
        ReflectionTestUtils.setField(origin, "id", ORIGIN_ZONE_ID);
        Zone dest = new Zone("KDY", "Kandy", true);
        ReflectionTestUtils.setField(dest, "id", DEST_ZONE_ID);

        when(zones.findById(ORIGIN_ZONE_ID)).thenReturn(Optional.of(origin));
        when(zones.findById(DEST_ZONE_ID)).thenReturn(Optional.of(dest));

        // Initial rule: base = 400.00, perKg = 100.00
        PricingRule rule = new PricingRule(
                ORIGIN_ZONE_ID,
                DEST_ZONE_ID,
                new BigDecimal("400.00"),
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "LKR",
                true
        );
        ReflectionTestUtils.setField(rule, "id", INITIAL_RULE_ID);
        when(rules.findByOriginZoneIdAndDestinationZoneIdAndActiveTrue(ORIGIN_ZONE_ID, DEST_ZONE_ID))
                .thenReturn(Optional.of(rule));

        // 1. Calculate fee and book parcel
        FeeQuote initialQuote = calculator.calculateFee(ORIGIN_ZONE_ID, DEST_ZONE_ID, ParcelSize.SMALL, 1000);
        assertThat(initialQuote.totalFee()).isEqualByComparingTo("500.00");

        Parcel.Fee feeSnapshot = new Parcel.Fee(
                initialQuote.totalFee(),
                initialQuote.currency(),
                initialQuote.pricingRuleId()
        );

        ParcelAddress pickup = new ParcelAddress("Sender", "+94771234567", "123 Main St", null, "Colombo", ORIGIN_ZONE_ID, null);
        ParcelAddress dropoff = new ParcelAddress("Receiver", "+94777654321", "456 Peradeniya Rd", null, "Kandy", DEST_ZONE_ID, null);

        Parcel parcel = Parcel.forCustomer(
                "SWD-20261112-9999",
                UUID.randomUUID(),
                pickup,
                dropoff,
                ParcelSize.SMALL,
                1000,
                "Fragile package",
                feeSnapshot,
                Instant.now()
        );

        // Verify initial parcel snapshot
        assertThat(parcel.getFeeAmount()).isEqualByComparingTo("500.00");
        assertThat(parcel.getFeeCurrency()).isEqualTo("LKR");
        assertThat(parcel.getPricingRuleId()).isEqualTo(INITIAL_RULE_ID);

        // 2. Admin updates the rule: base fee doubled from 400.00 to 800.00, and per-kg doubled from 100.00 to 200.00
        rule.setBaseFee(new BigDecimal("800.00"));
        rule.setPerKgFee(new BigDecimal("200.00"));

        // 3. New fee quote reflects the updated rule
        FeeQuote newQuote = calculator.calculateFee(ORIGIN_ZONE_ID, DEST_ZONE_ID, ParcelSize.SMALL, 1000);
        assertThat(newQuote.totalFee()).isEqualByComparingTo("1000.00");

        // 4. CRITICAL: Existing parcel fee remains completely untouched
        assertThat(parcel.getFeeAmount())
                .as("Existing parcel fee amount must remain invariant to subsequent pricing rule updates")
                .isEqualByComparingTo("500.00");
        assertThat(parcel.getFeeCurrency()).isEqualTo("LKR");
        assertThat(parcel.getPricingRuleId()).isEqualTo(INITIAL_RULE_ID);

        // 5. Even if the rule is deactivated entirely:
        rule.deactivate();
        assertThat(parcel.getFeeAmount()).isEqualByComparingTo("500.00");
        assertThat(parcel.getFeeCurrency()).isEqualTo("LKR");
    }
}

