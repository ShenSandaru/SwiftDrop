package com.swiftdrop.dispatch.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.dispatch.application.DispatchCourierNotFoundException;
import com.swiftdrop.dispatch.application.DispatchCourierUnavailableException;

class DispatchExceptionHandlerTest {

    private final DispatchExceptionHandler handler = new DispatchExceptionHandler();

    @Test
    void optimisticLockingFailureReturnsConflict409() {
        OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Stale object state");
        ProblemDetail problem = handler.conflict(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("The parcel was modified by another request; please refresh and try again");
    }

    @Test
    void dataIntegrityViolationReturnsConflict409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Unique constraint violation");
        ProblemDetail problem = handler.conflict(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("The parcel or courier already has an active assignment");
    }

    @Test
    void parcelConflictReturnsConflict409() {
        ParcelConflictException ex = new ParcelConflictException("The parcel is already assigned to another courier");
        ProblemDetail problem = handler.conflict(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("The parcel is already assigned to another courier");
    }

    @Test
    void courierUnavailableReturnsConflict409() {
        DispatchCourierUnavailableException ex = new DispatchCourierUnavailableException("The courier account is disabled");
        ProblemDetail problem = handler.conflict(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("The courier account is disabled");
    }

    @Test
    void parcelNotFoundReturnsNotFound404() {
        ParcelNotFoundException ex = new ParcelNotFoundException();
        ProblemDetail problem = handler.parcelNotFound(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void courierNotFoundReturnsNotFound404() {
        DispatchCourierNotFoundException ex = new DispatchCourierNotFoundException();
        ProblemDetail problem = handler.courierNotFound(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
}

