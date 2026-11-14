package com.swiftdrop.assistant.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.api.ServiceZone;
import com.swiftdrop.pricing.api.ZoneDirectory;

/** What it costs to send a parcel between two areas: the same calculation as booking. */
@Component
class EstimateFeeTool implements AssistantTool {

    static final String NAME = "estimate_fee";
    private static final BigDecimal GRAMS_PER_KG = BigDecimal.valueOf(1000);
    private static final int MAX_WEIGHT_GRAMS = 30_000;

    private final ZoneDirectory zones;
    private final FeeCalculator feeCalculator;

    EstimateFeeTool(ZoneDirectory zones, FeeCalculator feeCalculator) {
        this.zones = zones;
        this.feeCalculator = feeCalculator;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME,
                "Estimate the delivery fee for a parcel between two cities. Every started kilogram is charged.",
                Map.of("type", "object",
                        "properties", Map.of(
                                "fromCity", Map.of("type", "string", "description", "Pickup city, for example Colombo"),
                                "toCity", Map.of("type", "string", "description", "Drop-off city, for example Kandy"),
                                "weightKg", Map.of("type", "number", "description", "Parcel weight in kilograms, up to 30"),
                                "size", Map.of("type", "string", "enum", List.of("SMALL", "MEDIUM", "LARGE"),
                                        "description", "Parcel size; SMALL when the customer does not say")),
                        "required", List.of("fromCity", "toCity", "weightKg")));
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        List<ServiceZone> active = zones.findActiveZones();
        Optional<ServiceZone> from = findZone(active, arguments.get("fromCity"));
        Optional<ServiceZone> to = findZone(active, arguments.get("toCity"));
        if (from.isEmpty() || to.isEmpty()) {
            return Map.of("error", "SwiftDrop does not deliver to or from that city.",
                    "areas", active.stream().map(ServiceZone::name).toList());
        }
        Optional<Integer> grams = toGrams(arguments.get("weightKg"));
        if (grams.isEmpty()) {
            return Map.of("error", "The weight must be more than 0 and at most 30 kg.");
        }
        Optional<ParcelSize> size = toSize(arguments.get("size"));
        if (size.isEmpty()) {
            return Map.of("error", "The size must be SMALL, MEDIUM or LARGE.");
        }
        FeeQuote quote;
        try {
            quote = feeCalculator.calculateFee(from.get().id(), to.get().id(), size.get(), grams.get());
        } catch (RuntimeException e) {
            // The only expected failure is an unpriced route; its type is internal to pricing
            return Map.of("error", "No price is set for that route.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from.get().name());
        result.put("to", to.get().name());
        result.put("size", size.get().name());
        result.put("chargedKg", (grams.get() + 999) / 1000);
        result.put("totalFee", quote.totalFee().toPlainString());
        result.put("currency", quote.currency());
        result.put("baseFee", quote.baseFee().toPlainString());
        result.put("weightFee", quote.weightFee().toPlainString());
        result.put("sizeFee", quote.sizeFee().toPlainString());
        return result;
    }

    private static Optional<ServiceZone> findZone(List<ServiceZone> active, Object city) {
        if (!(city instanceof String text) || text.isBlank()) {
            return Optional.empty();
        }
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        return active.stream()
                .filter(zone -> zone.name().toLowerCase(Locale.ROOT).equals(wanted)
                        || zone.code().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
    }

    private static Optional<Integer> toGrams(Object weightKg) {
        if (!(weightKg instanceof Number number)) {
            return Optional.empty();
        }
        BigDecimal grams = new BigDecimal(number.toString()).multiply(GRAMS_PER_KG)
                .setScale(0, RoundingMode.CEILING);
        if (grams.signum() <= 0 || grams.compareTo(BigDecimal.valueOf(MAX_WEIGHT_GRAMS)) > 0) {
            return Optional.empty();
        }
        return Optional.of(grams.intValueExact());
    }

    private static Optional<ParcelSize> toSize(Object size) {
        if (size == null) {
            return Optional.of(ParcelSize.SMALL);
        }
        try {
            return Optional.of(ParcelSize.valueOf(size.toString().trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
