package com.swiftdrop.pricing.web;

import java.util.List;

import com.swiftdrop.pricing.domain.District;
import com.swiftdrop.pricing.domain.DistrictPostalZone;

/**
 * Representation of an administrative district and its cataloged delivery zones.
 */
public record DistrictResponse(
        String code,
        String name,
        String province,
        String postalCode,
        String defaultBranchName,
        Double latitude,
        Double longitude,
        List<PostalZoneResponse> zones
) {

    public record PostalZoneResponse(
            String code,
            String name,
            String postalCode
    ) {
        public static PostalZoneResponse from(DistrictPostalZone zone) {
            return new PostalZoneResponse(zone.getCode(), zone.getName(), zone.getPostalCode());
        }
    }

    public static DistrictResponse from(District district) {
        List<PostalZoneResponse> zoneResponses = district.getPostalZones().stream()
                .map(PostalZoneResponse::from)
                .toList();
        return new DistrictResponse(
                district.getCode(),
                district.getName(),
                district.getProvince(),
                district.getPostalCode(),
                district.getDefaultBranchName(),
                district.getLatitude(),
                district.getLongitude(),
                zoneResponses
        );
    }
}

