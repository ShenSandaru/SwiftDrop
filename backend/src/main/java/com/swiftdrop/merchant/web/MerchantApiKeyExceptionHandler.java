package com.swiftdrop.merchant.web;

import java.net.URI;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.merchant.application.ApiKeyLimitReachedException;
import com.swiftdrop.merchant.application.ApiKeyNotFoundException;

/**
 * Problem Details for API-key management errors. Validation and access errors are handled by
 * {@link MerchantExceptionHandler}, which covers the whole package.
 */
@RestControllerAdvice(assignableTypes = MerchantApiKeyController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class MerchantApiKeyExceptionHandler {

    @ExceptionHandler(ApiKeyNotFoundException.class)
    ProblemDetail handleNotFound(ApiKeyNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(ApiKeyLimitReachedException.class)
    ProblemDetail handleLimit(ApiKeyLimitReachedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        problem.setType(URI.create("https://swiftdrop.com/problems/api-key-limit"));
        return problem;
    }
}
