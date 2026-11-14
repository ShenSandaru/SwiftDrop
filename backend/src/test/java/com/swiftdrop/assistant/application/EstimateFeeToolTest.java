package com.swiftdrop.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.api.ServiceZone;
import com.swiftdrop.pricing.api.ZoneDirectory;

class EstimateFeeToolTest {

    private static final UUID COLOMBO = UUID.randomUUID();
    private static final UUID KANDY = UUID.randomUUID();

    private FeeCalculator calculator;
    private EstimateFeeTool tool;

    @BeforeEach
    void setUp() {
        ZoneDirectory zones = mock(ZoneDirectory.class);
        when(zones.findActiveZones()).thenReturn(List.of(
                new ServiceZone(COLOMBO, "CMB", "Colombo"), new ServiceZone(KANDY, "KDY", "Kandy")));
        calculator = mock(FeeCalculator.class);
        tool = new EstimateFeeTool(zones, calculator);
    }

    private void priced(ParcelSize size, int grams) {
        when(calculator.calculateFee(COLOMBO, KANDY, size, grams)).thenReturn(new FeeQuote(
                new BigDecimal("650.00"), "LKR", new BigDecimal("400.00"), new BigDecimal("200.00"),
                new BigDecimal("50.00"), UUID.randomUUID()));
    }

    @Test
    void quotesTwoKilogramsToKandyWithTheBreakdown() {
        priced(ParcelSize.SMALL, 2000);

        Map<String, Object> result = tool.execute(Map.of("fromCity", "colombo", "toCity", " KANDY ", "weightKg", 2));

        assertThat(result).containsEntry("totalFee", "650.00").containsEntry("currency", "LKR")
                .containsEntry("from", "Colombo").containsEntry("to", "Kandy")
                .containsEntry("size", "SMALL").containsEntry("chargedKg", 2);
    }

    @Test
    void matchesAZoneByCodeAndRoundsPartialKilogramsUp() {
        priced(ParcelSize.MEDIUM, 1500);

        Map<String, Object> result = tool.execute(
                Map.of("fromCity", "CMB", "toCity", "KDY", "weightKg", 1.5, "size", "medium"));

        assertThat(result).containsEntry("chargedKg", 2).containsEntry("size", "MEDIUM");
        verify(calculator).calculateFee(COLOMBO, KANDY, ParcelSize.MEDIUM, 1500);
    }

    @Test
    void namesTheServedAreasWhenACityIsUnknown() {
        Map<String, Object> result = tool.execute(Map.of("fromCity", "Colombo", "toCity", "Paris", "weightKg", 1));

        assertThat(result).containsKey("error").containsEntry("areas", List.of("Colombo", "Kandy"));
        verifyNoInteractions(calculator);
    }

    @Test
    void rejectsWeightsOutsideTheLimitsWithoutPricing() {
        for (Object weight : List.of(0, -1, 30.001, 31, "two")) {
            assertThat(tool.execute(Map.of("fromCity", "Colombo", "toCity", "Kandy", "weightKg", weight)))
                    .as("weight %s", weight).containsKey("error");
        }
        assertThat(tool.execute(Map.of("fromCity", "Colombo", "toCity", "Kandy"))).containsKey("error");
        verifyNoInteractions(calculator);
    }

    @Test
    void rejectsAnUnknownSize() {
        assertThat(tool.execute(Map.of("fromCity", "Colombo", "toCity", "Kandy", "weightKg", 1, "size", "HUGE")))
                .containsKey("error");
        verifyNoInteractions(calculator);
    }

    @Test
    void explainsAnUnpricedRouteInsteadOfFailing() {
        when(calculator.calculateFee(eq(COLOMBO), eq(KANDY), any(), eq(1000)))
                .thenThrow(new IllegalStateException("No pricing for this route"));

        assertThat(tool.execute(Map.of("fromCity", "Colombo", "toCity", "Kandy", "weightKg", 1)))
                .containsEntry("error", "No price is set for that route.");
    }
}
