package com.swiftdrop.pricing.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.pricing.application.RouteNotPricedException;

/**
 * Maps pricing failures to safe RFC 9457 bodies. Not limited to pricing controllers: delivery's
 * booking endpoints call {@code FeeCalculator} and must return the same 409 for an unpriced route.
 * Ordered before {@code GlobalExceptionHandler}, whose catch-all would otherwise answer 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PricingExceptionHandler {

    @ExceptionHandler(RouteNotPricedException.class)
    ProblemDetail routeNotPriced(RouteNotPricedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
