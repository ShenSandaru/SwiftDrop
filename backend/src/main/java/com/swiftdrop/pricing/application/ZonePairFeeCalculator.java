package com.swiftdrop.pricing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;

/**
 * Zone-pair fee calculation (contract §5.2):
 * {@code fee = base_fee + per_kg_fee × ceil(weightGrams / 1000) + size_surcharge(size)},
 * rounded HALF_UP to 2 decimals once, at the end.
 */
@Service
@Transactional(readOnly = true)
class ZonePairFeeCalculator implements FeeCalculator {

    static final int MIN_WEIGHT_GRAMS = 1;
    static final int MAX_WEIGHT_GRAMS = 30_000;
    private static final int GRAMS_PER_KG = 1000;

    private final ZoneRepository zones;
    private final PricingRuleRepository rules;

    ZonePairFeeCalculator(ZoneRepository zones, PricingRuleRepository rules) {
        this.zones = zones;
        this.rules = rules;
    }

    @Override
    public FeeQuote calculateFee(UUID originZoneId, UUID destinationZoneId, ParcelSize size, int weightGrams) {
        Objects.requireNonNull(originZoneId, "originZoneId");
        Objects.requireNonNull(destinationZoneId, "destinationZoneId");
        Objects.requireNonNull(size, "size");
        if (weightGrams < MIN_WEIGHT_GRAMS || weightGrams > MAX_WEIGHT_GRAMS) {
            throw new IllegalArgumentException("weightGrams must be between 1 and 30000");
        }

        // An inactive zone must not be priced even if its rule is still active
        if (!isActiveZone(originZoneId) || !isActiveZone(destinationZoneId)) {
            throw new RouteNotPricedException();
        }
        PricingRule rule = rules
                .findByOriginZoneIdAndDestinationZoneIdAndActiveTrue(originZoneId, destinationZoneId)
                .orElseThrow(RouteNotPricedException::new);

        // Every started kilogram is charged: 1 g → 1 kg, 1001 g → 2 kg
        long chargeableKg = (weightGrams + GRAMS_PER_KG - 1) / GRAMS_PER_KG;
        BigDecimal baseFee = rule.getBaseFee();
        BigDecimal weightFee = rule.getPerKgFee().multiply(BigDecimal.valueOf(chargeableKg));
        BigDecimal sizeFee = surcharge(rule, size);
        BigDecimal total = baseFee.add(weightFee).add(sizeFee);

        return new FeeQuote(
                money(total),
                rule.getCurrency(),
                money(baseFee),
                money(weightFee),
                money(sizeFee),
                rule.getId());
    }

    private boolean isActiveZone(UUID zoneId) {
        return zones.findById(zoneId).map(zone -> zone.isActive()).orElse(false);
    }

    private static BigDecimal surcharge(PricingRule rule, ParcelSize size) {
        return switch (size) {
            case SMALL -> rule.getSmallSizeSurcharge();
            case MEDIUM -> rule.getMediumSizeSurcharge();
            case LARGE -> rule.getLargeSizeSurcharge();
        };
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
