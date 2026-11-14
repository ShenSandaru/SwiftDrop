package com.swiftdrop.pricing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.domain.District;
import com.swiftdrop.pricing.domain.DistrictRepository;
import com.swiftdrop.pricing.domain.PricingConfig;
import com.swiftdrop.pricing.domain.PricingConfigRepository;
import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

/**
 * Zone-pair fee calculation:
 * 1. If an explicit custom rule is present in pricing_rules, it takes precedence.
 * 2. Otherwise, dynamic fair distance pricing is computed automatically via
 *    the global PricingConfig parameters and district coordinates:
 *    {@code distanceFee = min(standardBaseFee + max(0, distanceKm - 30) * ratePerKm, maxCap)}
 *    {@code fee = distanceFee + perKgFee * ceil(weightGrams / 1000) + sizeSurcharge(size)}.
 */
@Service
@Transactional(readOnly = true)
class ZonePairFeeCalculator implements FeeCalculator {

    static final int MIN_WEIGHT_GRAMS = 1;
    static final int MAX_WEIGHT_GRAMS = 30_000;
    private static final int GRAMS_PER_KG = 1000;
    public static final double MAX_ISLAND_DISTANCE_KM = 520.0;

    private final ZoneRepository zones;
    private final PricingRuleRepository rules;
    private final PricingConfigRepository configRepository;
    private final DistrictRepository districtRepository;

    public ZonePairFeeCalculator(ZoneRepository zones, PricingRuleRepository rules) {
        this(zones, rules, null, null);
    }

    @Autowired
    public ZonePairFeeCalculator(
            ZoneRepository zones,
            PricingRuleRepository rules,
            @Autowired(required = false) PricingConfigRepository configRepository,
            @Autowired(required = false) DistrictRepository districtRepository
    ) {
        this.zones = zones;
        this.rules = rules;
        this.configRepository = configRepository;
        this.districtRepository = districtRepository;
    }

    @Override
    public FeeQuote calculateFee(UUID originZoneId, UUID destinationZoneId, ParcelSize size, int weightGrams) {
        Objects.requireNonNull(originZoneId, "originZoneId");
        Objects.requireNonNull(destinationZoneId, "destinationZoneId");
        Objects.requireNonNull(size, "size");
        if (weightGrams < MIN_WEIGHT_GRAMS || weightGrams > MAX_WEIGHT_GRAMS) {
            throw new IllegalArgumentException("weightGrams must be between 1 and 30000");
        }

        // Both zones must exist and be active
        Zone originZone = zones.findById(originZoneId).orElseThrow(RouteNotPricedException::new);
        Zone destinationZone = zones.findById(destinationZoneId).orElseThrow(RouteNotPricedException::new);
        if (!originZone.isActive() || !destinationZone.isActive()) {
            throw new RouteNotPricedException();
        }

        // 1. Check for an explicit pricing rule override
        if (rules != null) {
            Optional<PricingRule> explicitRule = rules.findByOriginZoneIdAndDestinationZoneIdAndActiveTrue(
                    originZoneId, destinationZoneId);
            if (explicitRule.isPresent()) {
                PricingRule rule = explicitRule.get();
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
        }

        // 2. Automated dynamic calculation from global pricing configuration
        PricingConfig config = (configRepository != null)
                ? configRepository.findById(PricingConfig.DEFAULT_ID).orElseGet(PricingConfig::defaultConfig)
                : PricingConfig.defaultConfig();

        double roadDistanceKm = computeRoadDistanceKm(originZone, destinationZone);
        double freeKm = config.getFreeKmAllowance().doubleValue();
        double extraKm = Math.max(0.0, roadDistanceKm - freeKm);

        // Proportional line-haul scaling: (maxCap - standardBaseFee) / (520km - freeKm)
        // Guarantees that only the furthest hub distance in Sri Lanka (Matara <-> Jaffna ~520km) reaches maxCap.
        BigDecimal effectiveRatePerKm = config.getRatePerKm();
        double maxExtraKm = Math.max(1.0, MAX_ISLAND_DISTANCE_KM - freeKm);
        if (config.getMaxCap().compareTo(config.getStandardBaseFee()) > 0) {
            effectiveRatePerKm = config.getMaxCap().subtract(config.getStandardBaseFee())
                    .divide(BigDecimal.valueOf(maxExtraKm), 4, RoundingMode.HALF_UP);
        }

        BigDecimal distanceAddition = effectiveRatePerKm.multiply(BigDecimal.valueOf(extraKm));
        BigDecimal baseDeliveryFee = config.getStandardBaseFee()
                .add(distanceAddition)
                .min(config.getMaxCap());

        long chargeableKg = (weightGrams + GRAMS_PER_KG - 1) / GRAMS_PER_KG;
        BigDecimal weightFee = config.getPerKgFee().multiply(BigDecimal.valueOf(chargeableKg));
        BigDecimal sizeFee = switch (size) {
            case SMALL -> config.getSmallSizeSurcharge();
            case MEDIUM -> config.getMediumSizeSurcharge();
            case LARGE -> config.getLargeSizeSurcharge();
        };

        BigDecimal total = baseDeliveryFee.add(weightFee).add(sizeFee);

        UUID syntheticRuleId = UUID.nameUUIDFromBytes(
                ("DYNAMIC:" + originZone.getId() + ":" + destinationZone.getId()).getBytes()
        );

        return new FeeQuote(
                money(total),
                config.getCurrency(),
                money(baseDeliveryFee),
                money(weightFee),
                money(sizeFee),
                syntheticRuleId
        );
    }

    private double computeRoadDistanceKm(Zone origin, Zone dest) {
        if (origin.getId().equals(dest.getId())) {
            return 0.0;
        }
        if (districtRepository == null) {
            return 0.0;
        }
        District oDistrict = resolveDistrict(origin);
        District dDistrict = resolveDistrict(dest);
        if (oDistrict == null || dDistrict == null) {
            return 0.0;
        }
        if (oDistrict.getCode().equalsIgnoreCase(dDistrict.getCode())) {
            return 0.0;
        }
        if (oDistrict.getLatitude() == null || oDistrict.getLongitude() == null
                || dDistrict.getLatitude() == null || dDistrict.getLongitude() == null) {
            return 0.0;
        }

        double lat1 = oDistrict.getLatitude().doubleValue();
        double lon1 = oDistrict.getLongitude().doubleValue();
        double lat2 = dDistrict.getLatitude().doubleValue();
        double lon2 = dDistrict.getLongitude().doubleValue();

        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double straightLineKm = earthRadiusKm * c;
        return Math.round(straightLineKm * 1.25 * 10.0) / 10.0;
    }

    private District resolveDistrict(Zone zone) {
        if (zone == null || districtRepository == null) {
            return null;
        }
        String code = zone.getCode();
        if (code != null) {
            String districtPrefix = code.contains("-") ? code.substring(0, code.indexOf("-")).trim() : code.trim();
            Optional<District> byCode = districtRepository.findByCodeIgnoreCase(districtPrefix);
            if (byCode.isPresent()) {
                return byCode.get();
            }
        }
        String name = zone.getName();
        if (name != null) {
            Optional<District> byName = districtRepository.findByNameIgnoreCase(name.trim());
            if (byName.isPresent()) {
                return byName.get();
            }

            for (District d : districtRepository.findAll()) {
                if (name.toLowerCase().contains(d.getName().toLowerCase())
                        || d.getName().toLowerCase().contains(name.toLowerCase())) {
                    return d;
                }
            }
        }
        return null;
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
