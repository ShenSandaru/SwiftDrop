package com.swiftdrop.merchant.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.merchant.application.DuplicateRegistrationException;
import com.swiftdrop.merchant.domain.IllegalStateTransitionException;
import com.swiftdrop.merchant.domain.WebhookEndpointLimitExceededException;
import com.swiftdrop.merchant.domain.WebhookEndpointNotFoundException;

/**
 * Centralized exception handler providing safe RFC 9457 Problem Details for merchant module endpoints.
 * Never echoes raw sensitive input or stack traces.
 */
@RestControllerAdvice(basePackageClasses = MerchantExceptionHandler.class)
public class MerchantExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request content");
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://swiftdrop.com/problems/invalid-request"));
        problem.setProperty("invalidFields", fields);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(DuplicateRegistrationException.class)
    ProblemDetail handleDuplicate(DuplicateRegistrationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        problem.setType(URI.create("https://swiftdrop.com/problems/duplicate-registration"));
        return problem;
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    ProblemDetail handleIllegalTransition(IllegalStateTransitionException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        problem.setType(URI.create("https://swiftdrop.com/problems/illegal-state-transition"));
        problem.setProperty("fromStatus", e.getFromStatus());
        problem.setProperty("toStatus", e.getToStatus());
        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "This merchant record was modified concurrently by another administrator. Please refresh and try again.");
        problem.setTitle("Conflict");
        problem.setType(URI.create("https://swiftdrop.com/problems/concurrent-modification"));
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problem.setTitle("Forbidden");
        problem.setType(URI.create("https://swiftdrop.com/problems/access-denied"));
        return problem;
    }

    @ExceptionHandler(WebhookEndpointLimitExceededException.class)
    ProblemDetail handleEndpointLimit(WebhookEndpointLimitExceededException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        problem.setType(URI.create("https://swiftdrop.com/problems/webhook-limit-exceeded"));
        problem.setProperty("maxLimit", e.getMaxLimit());
        return problem;
    }

    @ExceptionHandler(WebhookEndpointNotFoundException.class)
    ProblemDetail handleEndpointNotFound(WebhookEndpointNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Not Found");
        problem.setType(URI.create("https://swiftdrop.com/problems/webhook-endpoint-not-found"));
        problem.setProperty("endpointId", e.getEndpointId());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }
}

