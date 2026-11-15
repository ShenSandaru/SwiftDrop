package com.swiftdrop.pricing.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.pricing.api.PricingNotFoundException;
import com.swiftdrop.pricing.domain.District;
import com.swiftdrop.pricing.domain.DistrictRepository;

/**
 * Reference endpoints for Sri Lanka administrative districts and cataloged postal delivery zones.
 */
@RestController
@RequestMapping("/api/v1/districts")
class DistrictController {

    private final DistrictRepository districts;

    DistrictController(@Autowired(required = false) DistrictRepository districts) {
        this.districts = districts;
    }

    /** List all 25 administrative districts with their postal zones. */
    @GetMapping
    List<DistrictResponse> listDistricts() {
        if (districts == null) {
            return List.of();
        }
        return districts.findAllWithPostalZones().stream()
                .map(DistrictResponse::from)
                .toList();
    }

    /** Get a single district by its 3-letter code (e.g. CMB, KDY). */
    @GetMapping("/{code}")
    DistrictResponse getDistrict(@PathVariable String code) {
        if (districts == null) {
            throw new PricingNotFoundException("District not found: " + code);
        }
        District district = districts.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new PricingNotFoundException("District not found: " + code));
        return DistrictResponse.from(district);
    }
}
