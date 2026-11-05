package com.swiftdrop.dispatch.web;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.dispatch.application.DispatchCourierNotFoundException;
import com.swiftdrop.dispatch.application.DispatchCourierUnavailableException;

@RestControllerAdvice(basePackageClasses = DispatchExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class DispatchExceptionHandler {

    @ExceptionHandler(ParcelNotFoundException.class)
    ProblemDetail parcelNotFound(ParcelNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DispatchCourierNotFoundException.class)
    ProblemDetail courierNotFound(DispatchCourierNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({ParcelConflictException.class, DispatchCourierUnavailableException.class,
            DataIntegrityViolationException.class})
    ProblemDetail conflict(RuntimeException exception) {
        String detail = exception instanceof DataIntegrityViolationException
                ? "The parcel or courier already has an active assignment"
                : exception.getMessage();
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail invalidParameter(MethodArgumentTypeMismatchException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        problem.setProperty("invalidFields", List.of(exception.getName()));
        return problem;
    }
}
