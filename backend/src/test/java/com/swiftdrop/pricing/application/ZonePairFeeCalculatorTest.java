package com.swiftdrop.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

class ZonePairFeeCalculatorTest {

    private static final UUID COLOMBO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID KANDY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RULE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private ZoneRepository zones;
    private PricingRuleRepository rules;
    private ZonePairFeeCalculator calculator;

    @BeforeEach
    void setUp() {
        zones = mock(ZoneRepository.class);
        rules = mock(PricingRuleRepository.class);
        calculator = new ZonePairFeeCalculator(zones, rules);
        givenZone(COLOMBO, "CMB", "Colombo", true);
        givenZone(KANDY, "KDY", "Kandy", true);
        givenRule(new PricingRule(COLOMBO, KANDY, new BigDecimal("400.00"), new BigDecimal("100.00"),
                new BigDecimal("0.00"), new BigDecimal("50.00"), new BigDecimal("150.00"), "LKR"));
    }

    @Test
    void quoteContainsBreakdownTotalCurrencyAndRule() {
        FeeQuote quote = calculator.calculateFee(COLOMBO, KANDY, ParcelSize.MEDIUM, 2000);

        assertThat(quote.baseFee()).isEqualByComparingTo("400.00");
        assertThat(quote.weightFee()).isEqualByComparingTo("200.00");
        assertThat(quote.sizeFee()).isEqualByComparingTo("50.00");
        assertThat(quote.totalFee()).isEqualByComparingTo("650.00");
        assertThat(quote.totalFee().scale()).isEqualTo(2);
        assertThat(quote.currency()).isEqualTo("LKR");
        assertThat(quote.pricingRuleId()).isEqualTo(RULE_ID);
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "999, 1",
        "1000, 1",
        "1001, 2",
        "2500, 3",
        "30000, 30"
    })
    void everyStartedKilogramIsCharged(int weightGrams, int chargeableKg) {
        FeeQuote quote = calculator.calculateFee(COLOMBO, KANDY, ParcelSize.SMALL, weightGrams);

        assertThat(quote.weightFee()).isEqualByComparingTo(new BigDecimal("100.00").multiply(BigDecimal.valueOf(chargeableKg)));
    }

    @ParameterizedTest
    @CsvSource({
        "SMALL, 0.00",
        "MEDIUM, 50.00",
        "LARGE, 150.00"
    })
    void sizeSelectsItsSurcharge(ParcelSize size, String expectedSurcharge) {
        FeeQuote quote = calculator.calculateFee(COLOMBO, KANDY, size, 1000);

        assertThat(quote.sizeFee()).isEqualByComparingTo(expectedSurcharge);
    }

    @Test
    void fractionalRuleAmountsAreRoundedHalfUpToTwoDecimals() {
        givenRule(new PricingRule(COLOMBO, KANDY, new BigDecimal("400.005"), new BigDecimal("0.00")));

        FeeQuote quote = calculator.calculateFee(COLOMBO, KANDY, ParcelSize.SMALL, 1000);

        assertThat(quote.totalFee()).isEqualTo(new BigDecimal("400.01"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 30_001})
    void weightOutsideLimitsIsRejected(int weightGrams) {
        assertThatThrownBy(() -> calculator.calculateFee(COLOMBO, KANDY, ParcelSize.SMALL, weightGrams))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingArgumentsAreRejected() {
        assertThatThrownBy(() -> calculator.calculateFee(null, KANDY, ParcelSize.SMALL, 1000))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.calculateFee(COLOMBO, null, ParcelSize.SMALL, 1000))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.calculateFee(COLOMBO, KANDY, null, 1000))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void routeWithoutActiveRuleIsNotPriced() {
        when(rules.findByOriginZoneIdAndDestinationZoneIdAndActiveTrue(COLOMBO, KANDY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calculator.calculateFee(COLOMBO, KANDY, ParcelSize.SMALL, 1000))
                .isInstanceOf(RouteNotPricedException.class)
                .hasMessage("No pricing for this route");
    }

    @Test
    void unknownZoneIsNotPriced() {
        UUID unknown = UUID.randomUUID();
        when(zones.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calculator.calculateFee(unknown, KANDY, ParcelSize.SMALL, 1000))
                .isInstanceOf(RouteNotPricedException.class);
    }

    @Test
    void inactiveZoneIsNotPricedEvenWithActiveRule() {
        givenZone(KANDY, "KDY", "Kandy", false);

        assertThatThrownBy(() -> calculator.calculateFee(COLOMBO, KANDY, ParcelSize.SMALL, 1000))
                .isInstanceOf(RouteNotPricedException.class);
    }

    @Test
    void routesAreDirectional() {
        when(rules.findByOriginZoneIdAndDestinationZoneIdAndActiveTrue(KANDY, COLOMBO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calculator.calculateFee(KANDY, COLOMBO, ParcelSize.SMALL, 1000))
                .isInstanceOf(RouteNotPricedException.class);
    }

    private void givenZone(UUID id, String code, String name, boolean active) {
        Zone zone = new Zone(code, name, active);
        ReflectionTestUtils.setField(zone, "id", id);
        when(zones.findById(id)).thenReturn(Optional.of(zone));
    }

    private void givenRule(PricingRule rule) {
        ReflectionTestUtils.setField(rule, "id", RULE_ID);
        when(rules.findByOriginZoneIdAndDestinationZoneIdAndActiveTrue(rule.getOriginZoneId(), rule.getDestinationZoneId()))
                .thenReturn(Optional.of(rule));
    }
}
