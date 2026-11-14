package com.swiftdrop.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.pricing.api.PricingConflictException;
import com.swiftdrop.pricing.api.PricingNotFoundException;
import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.pricing.web.PricingRuleRequest;
import com.swiftdrop.pricing.web.PricingRuleResponse;

class PricingRuleAdministrationTest {

    private static final UUID ORIGIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private PricingRuleRepository pricingRules;
    private ZoneRepository zones;
    private PricingRuleAdministration administration;

    private Zone originZone;
    private Zone destZone;

    @BeforeEach
    void setUp() {
        pricingRules = mock(PricingRuleRepository.class);
        zones = mock(ZoneRepository.class);
        administration = new PricingRuleAdministration(pricingRules, zones);

        originZone = new Zone("CMB", "Colombo");
        ReflectionTestUtils.setField(originZone, "id", ORIGIN_ID);

        destZone = new Zone("KDY", "Kandy");
        ReflectionTestUtils.setField(destZone, "id", DEST_ID);

        when(zones.findById(ORIGIN_ID)).thenReturn(Optional.of(originZone));
        when(zones.findById(DEST_ID)).thenReturn(Optional.of(destZone));
        when(zones.findAll()).thenReturn(List.of(originZone, destZone));
    }

    @Test
    void listReturnsAllRulesWithEnrichedZoneNames() {
        PricingRule rule = new PricingRule(ORIGIN_ID, DEST_ID, new BigDecimal("400.00"), new BigDecimal("100.00"));
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        when(pricingRules.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(rule));

        List<PricingRuleResponse> responses = administration.list();

        assertThat(responses).hasSize(1);
        PricingRuleResponse response = responses.get(0);
        assertThat(response.originZoneCode()).isEqualTo("CMB");
        assertThat(response.originZoneName()).isEqualTo("Colombo");
        assertThat(response.destinationZoneCode()).isEqualTo("KDY");
        assertThat(response.destinationZoneName()).isEqualTo("Kandy");
        assertThat(response.baseFee()).isEqualByComparingTo("400.00");
    }

    @Test
    void createSavesNewPricingRule() {
        when(pricingRules.existsByOriginZoneIdAndDestinationZoneId(ORIGIN_ID, DEST_ID)).thenReturn(false);
        when(pricingRules.saveAndFlush(any(PricingRule.class))).thenAnswer(inv -> {
            PricingRule r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
            return r;
        });

        PricingRuleRequest request = new PricingRuleRequest(
                ORIGIN_ID,
                DEST_ID,
                new BigDecimal("450.00"),
                new BigDecimal("120.00"),
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                new BigDecimal("30.00"),
                "LKR",
                true,
                null
        );

        PricingRuleResponse response = administration.create(request);

        assertThat(response.originZoneId()).isEqualTo(ORIGIN_ID);
        assertThat(response.destinationZoneId()).isEqualTo(DEST_ID);
        assertThat(response.baseFee()).isEqualByComparingTo("450.00");
        assertThat(response.perKgFee()).isEqualByComparingTo("120.00");
        verify(pricingRules).saveAndFlush(any(PricingRule.class));
    }

    @Test
    void createRejectsDuplicateZonePairWith409() {
        when(pricingRules.existsByOriginZoneIdAndDestinationZoneId(ORIGIN_ID, DEST_ID)).thenReturn(true);

        PricingRuleRequest request = new PricingRuleRequest(
                ORIGIN_ID,
                DEST_ID,
                new BigDecimal("400.00"),
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "LKR",
                true,
                null
        );

        assertThatThrownBy(() -> administration.create(request))
                .isInstanceOf(PricingConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createRejectsUnknownZoneWith404() {
        UUID unknownZoneId = UUID.randomUUID();
        when(zones.findById(unknownZoneId)).thenReturn(Optional.empty());

        PricingRuleRequest request = new PricingRuleRequest(
                unknownZoneId,
                DEST_ID,
                new BigDecimal("400.00"),
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "LKR",
                true,
                null
        );

        assertThatThrownBy(() -> administration.create(request))
                .isInstanceOf(PricingNotFoundException.class)
                .hasMessageContaining("Origin zone not found");
    }

    @Test
    void updateModifiesPricingRuleWhenValid() {
        UUID ruleId = UUID.randomUUID();
        PricingRule existing = new PricingRule(ORIGIN_ID, DEST_ID, new BigDecimal("400.00"), new BigDecimal("100.00"));
        ReflectionTestUtils.setField(existing, "id", ruleId);
        ReflectionTestUtils.setField(existing, "version", 1L);

        when(pricingRules.findById(ruleId)).thenReturn(Optional.of(existing));
        when(pricingRules.saveAndFlush(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

        PricingRuleRequest request = new PricingRuleRequest(
                ORIGIN_ID,
                DEST_ID,
                new BigDecimal("500.00"),
                new BigDecimal("150.00"),
                new BigDecimal("25.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "LKR",
                true,
                1L
        );

        PricingRuleResponse response = administration.update(ruleId, request);

        assertThat(response.baseFee()).isEqualByComparingTo("500.00");
        assertThat(response.perKgFee()).isEqualByComparingTo("150.00");
        assertThat(response.smallSizeSurcharge()).isEqualByComparingTo("25.00");
    }

    @Test
    void updateRejectsVersionConflictWith409() {
        UUID ruleId = UUID.randomUUID();
        PricingRule existing = new PricingRule(ORIGIN_ID, DEST_ID, new BigDecimal("400.00"), new BigDecimal("100.00"));
        ReflectionTestUtils.setField(existing, "id", ruleId);
        ReflectionTestUtils.setField(existing, "version", 2L);

        when(pricingRules.findById(ruleId)).thenReturn(Optional.of(existing));

        PricingRuleRequest request = new PricingRuleRequest(
                ORIGIN_ID,
                DEST_ID,
                new BigDecimal("500.00"),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "LKR",
                true,
                1L // outdated version
        );

        assertThatThrownBy(() -> administration.update(ruleId, request))
                .isInstanceOf(PricingConflictException.class)
                .hasMessageContaining("modified by another transaction");
    }
}

