package com.swiftdrop.identity.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.identity.application.EmailAlreadyRegisteredException;
import com.swiftdrop.identity.application.InvalidCredentialsException;
import com.swiftdrop.identity.application.InvalidExchangeCodeException;
import com.swiftdrop.identity.application.InvalidPasswordException;
import com.swiftdrop.identity.application.InvalidRoleException;
import com.swiftdrop.identity.application.SelfDisablingException;

/**
 * Safe RFC 9457 error bodies for the identity endpoints; rejected values are never echoed. The
 * project-wide error format is still open (docs/architecture.md, section 5).
 */
@RestControllerAdvice(basePackageClasses = IdentityExceptionHandler.class)
class IdentityExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException e) {
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField).distinct().sorted().toList();
        return invalid("Invalid request", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(InvalidPasswordException.class)
    ProblemDetail invalidPassword(InvalidPasswordException e) {
        return invalid(e.getMessage(), List.of("password"));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail duplicate(EmailAlreadyRegisteredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(InvalidExchangeCodeException.class)
    ProblemDetail invalidExchangeCode(InvalidExchangeCodeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(InvalidRoleException.class)
    ProblemDetail invalidRole(InvalidRoleException e) {
        return invalid(e.getMessage(), List.of("role"));
    }

    @ExceptionHandler(SelfDisablingException.class)
    ProblemDetail selfDisabling(SelfDisablingException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    private static ProblemDetail invalid(String detail, List<String> fields) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("invalidFields", fields);
        return problem;
    }
}
