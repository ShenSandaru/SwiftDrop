package com.swiftdrop.tracking.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Puts the rate limit in front of every public tracking endpoint, in whichever module it lives. */
@Configuration(proxyBeanMethods = false)
class TrackingWebConfiguration implements WebMvcConfigurer {

    static final String PUBLIC_TRACKING = "/api/v1/tracked-parcels/**";

    private final TrackingRateLimitInterceptor rateLimit;

    TrackingWebConfiguration(TrackingRateLimitInterceptor rateLimit) {
        this.rateLimit = rateLimit;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimit).addPathPatterns(PUBLIC_TRACKING);
    }
}
