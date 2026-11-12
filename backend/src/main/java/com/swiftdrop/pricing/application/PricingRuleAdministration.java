package com.swiftdrop.pricing.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.pricing.api.PricingConflictException;
import com.swiftdrop.pricing.api.PricingNotFoundException;
import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.pricing.web.PricingRuleRequest;
import com.swiftdrop.pricing.web.PricingRuleResponse;

/**
 * Administrative operations on pricing rules (ADM-3, contract §4.2).
 * Admin-only: create, update, and manage fee calculation rules between zone pairs.
 */
@Service
public class PricingRuleAdministration {

    private final PricingRuleRepository pricingRules;
    private final ZoneRepository zones;

    public PricingRuleAdministration(PricingRuleRepository pricingRules, ZoneRepository zones) {
        this.pricingRules = pricingRules;
        this.zones = zones;
    }

    @Transactional(readOnly = true)
    public List<PricingRuleResponse> list() {
        List<PricingRule> rules = pricingRules.findAllByOrderByCreatedAtDesc();
        Map<UUID, Zone> zoneMap = zones.findAll().stream()
                .collect(Collectors.toMap(Zone::getId, Function.identity(), (a, b) -> a));

        return rules.stream()
                .map(rule -> PricingRuleResponse.from(
                        rule,
                        zoneMap.get(rule.getOriginZoneId()),
                        zoneMap.get(rule.getDestinationZoneId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PricingRuleResponse get(UUID id) {
        PricingRule rule = findRule(id);
        Zone origin = zones.findById(rule.getOriginZoneId()).orElse(null);
        Zone destination = zones.findById(rule.getDestinationZoneId()).orElse(null);
        return PricingRuleResponse.from(rule, origin, destination);
    }

    @Transactional
    public PricingRuleResponse create(PricingRuleRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Zone origin = zones.findById(request.originZoneId())
                .orElseThrow(() -> new PricingNotFoundException("Origin zone not found"));
        Zone destination = zones.findById(request.destinationZoneId())
                .orElseThrow(() -> new PricingNotFoundException("Destination zone not found"));

        if (pricingRules.existsByOriginZoneIdAndDestinationZoneId(request.originZoneId(), request.destinationZoneId())) {
            throw new PricingConflictException("A pricing rule for this origin and destination zone pair already exists");
        }

        PricingRule rule = new PricingRule(
                request.originZoneId(),
                request.destinationZoneId(),
                request.baseFee(),
                request.perKgFee(),
                request.smallSizeSurchargeOrDefault(),
                request.mediumSizeSurchargeOrDefault(),
                request.largeSizeSurchargeOrDefault(),
                request.currencyOrDefault(),
                request.activeOrDefault()
        );

        PricingRule saved = save(rule);
        return PricingRuleResponse.from(saved, origin, destination);
    }

    @Transactional
    public PricingRuleResponse update(UUID id, PricingRuleRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        PricingRule rule = findRule(id);

        long expectedVersion = request.version() != null ? request.version() : 0L;
        if (rule.getVersion() != expectedVersion) {
            throw new PricingConflictException("Pricing rule has been modified by another transaction. Please reload.");
        }

        Zone origin = zones.findById(request.originZoneId())
                .orElseThrow(() -> new PricingNotFoundException("Origin zone not found"));
        Zone destination = zones.findById(request.destinationZoneId())
                .orElseThrow(() -> new PricingNotFoundException("Destination zone not found"));

        boolean pairChanged = !rule.getOriginZoneId().equals(request.originZoneId())
                || !rule.getDestinationZoneId().equals(request.destinationZoneId());

        if (pairChanged) {
            pricingRules.findByOriginZoneIdAndDestinationZoneId(request.originZoneId(), request.destinationZoneId())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new PricingConflictException("A pricing rule for this origin and destination zone pair already exists");
                    });
            rule.setOriginZoneId(request.originZoneId());
            rule.setDestinationZoneId(request.destinationZoneId());
        }

        rule.setBaseFee(request.baseFee());
        rule.setPerKgFee(request.perKgFee());
        rule.setSmallSizeSurcharge(request.smallSizeSurchargeOrDefault());
        rule.setMediumSizeSurcharge(request.mediumSizeSurchargeOrDefault());
        rule.setLargeSizeSurcharge(request.largeSizeSurchargeOrDefault());
        rule.setCurrency(request.currencyOrDefault());
        rule.setActive(request.activeOrDefault());

        PricingRule saved = save(rule);
        return PricingRuleResponse.from(saved, origin, destination);
    }

    private PricingRule findRule(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return pricingRules.findById(id)
                .orElseThrow(() -> new PricingNotFoundException("Pricing rule not found"));
    }

    private PricingRule save(PricingRule rule) {
        try {
            return pricingRules.saveAndFlush(rule);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new PricingConflictException("Pricing rule has been modified by another transaction. Please reload.");
        } catch (DataIntegrityViolationException e) {
            throw new PricingConflictException("The pricing rule conflicts with an existing rule (duplicate origin and destination pair)");
        }
    }
}

