package com.swiftdrop.tracking.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.tracking.application.TooManyStreamsException;
import com.swiftdrop.tracking.application.UnknownTrackingNumberException;

/**
 * Errors of the tracking stream. Answered with a status and no body: the client asked for
 * {@code text/event-stream}, so a JSON problem body could not be negotiated anyway, and
 * {@code EventSource} only looks at the status.
 */
@RestControllerAdvice(basePackageClasses = TrackingExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class TrackingExceptionHandler {

    @ExceptionHandler(UnknownTrackingNumberException.class)
    ResponseEntity<Void> unknown() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(TooManyStreamsException.class)
    ResponseEntity<Void> tooMany() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "30").build();
    }
}
