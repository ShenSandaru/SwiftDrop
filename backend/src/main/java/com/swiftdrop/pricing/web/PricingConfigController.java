package com.swiftdrop.pricing.web;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.pricing.domain.PricingConfig;
import com.swiftdrop.pricing.domain.PricingConfigRepository;

/**
 * REST API for viewing and modifying global pricing configuration parameters.
 * Admin-only for updates.
 */
@RestController
@RequestMapping("/api/v1/pricing-config")
class PricingConfigController {

    private final PricingConfigRepository configRepository;

    PricingConfigController(PricingConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @GetMapping
    PricingConfigResponse get() {
        PricingConfig config = configRepository.findById(PricingConfig.DEFAULT_ID)
                .orElseGet(() -> configRepository.save(PricingConfig.defaultConfig()));
        return PricingConfigResponse.from(config);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    PricingConfigResponse update(@Valid @RequestBody PricingConfigRequest request) {
        PricingConfig config = configRepository.findById(PricingConfig.DEFAULT_ID)
                .orElseGet(() -> new PricingConfig(
                        PricingConfig.DEFAULT_ID,
                        request.standardBaseFee(),
                        request.freeKmAllowance(),
                        request.ratePerKm(),
                        request.maxCap(),
                        request.perKgFee(),
                        request.smallSizeSurcharge(),
                        request.mediumSizeSurcharge(),
                        request.largeSizeSurcharge(),
                        request.currency()
                ));

        config.update(
                request.standardBaseFee(),
                request.freeKmAllowance(),
                request.ratePerKm(),
                request.maxCap(),
                request.perKgFee(),
                request.smallSizeSurcharge(),
                request.mediumSizeSurcharge(),
                request.largeSizeSurcharge(),
                request.currency()
        );

        return PricingConfigResponse.from(configRepository.save(config));
    }
}

