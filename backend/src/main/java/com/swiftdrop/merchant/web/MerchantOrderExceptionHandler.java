package com.swiftdrop.merchant.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Problem Details for the merchant order API. Invalid input from delivery answers a generic 400, as
 * for customer bookings, so internal messages never reach merchant systems. Body validation errors
 * ({@code invalidFields}) come from {@link MerchantExceptionHandler}; an unpriced route answers 409
 * from pricing's handler.
 */
@RestControllerAdvice(assignableTypes = MerchantOrderController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class MerchantOrderExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(MerchantOrderNotFoundException.class)
    ProblemDetail notFound(MerchantOrderNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }
}
