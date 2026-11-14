package com.swiftdrop.merchant.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.merchant.application.RedeliveryRefusedException;
import com.swiftdrop.merchant.application.WebhookDeliveryNotFoundException;

/** Problem Details for webhook delivery management; other errors use {@link MerchantExceptionHandler}. */
@RestControllerAdvice(assignableTypes = {WebhookDeliveryController.class, AdminWebhookDeliveryController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
class WebhookDeliveryExceptionHandler {

    @ExceptionHandler(WebhookDeliveryNotFoundException.class)
    ProblemDetail notFound(WebhookDeliveryNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(RedeliveryRefusedException.class)
    ProblemDetail refused(RedeliveryRefusedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }
}
