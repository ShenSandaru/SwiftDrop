package com.swiftdrop.shared.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Project-wide RFC 9457 Problem Details error handler for common web exceptions across all modules
 * (contract §3.3, SD-002).
 *
 * <p>Ordered with lowest precedence so that module-specific exception handlers (such as
 * {@code IdentityExceptionHandler}) take precedence for their own packages and custom exceptions.
 * Personal data, stack traces, and internal exception messages are never leaked in error bodies.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException e) {
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        problem.setProperty("invalidFields", fields);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail optimisticLock() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The resource was updated concurrently; please reload and try again");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(ErrorResponseException.class)
    ProblemDetail springWebError(ErrorResponseException e) {
        return e.getBody();
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled exception caught by global handler", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }
}
