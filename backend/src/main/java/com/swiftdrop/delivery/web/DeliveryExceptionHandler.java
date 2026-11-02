package com.swiftdrop.delivery.web;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;

/**
 * Safe RFC 9457 bodies for delivery's own endpoints (ADR 0006 §7). Limited to this package; other
 * modules map {@code delivery.api} exceptions themselves. Ordered before
 * {@code GlobalExceptionHandler}, whose catch-all would otherwise answer 500.
 */
@RestControllerAdvice(basePackageClasses = DeliveryExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class DeliveryExceptionHandler {

    @ExceptionHandler(ParcelNotFoundException.class)
    ProblemDetail notFound(ParcelNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** The message names the broken rule only, never parcel data. */
    @ExceptionHandler(ParcelConflictException.class)
    ProblemDetail conflict(ParcelConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Domain validation that passed the request annotations; the message is not echoed. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
    }

    /** A malformed path or query value, such as a bad UUID or an unknown status. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        problem.setProperty("invalidFields", List.of(e.getName()));
        return problem;
    }
}
