package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentSummary;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelAssignmentRecord;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * SD-317: Multi-threaded unit concurrency tests verifying optimistic locking,
 * thread-safety, and collision detection on courier assignments.
 */
class DispatchAssignmentConcurrencyTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID DISPATCHER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private ParcelRepository parcels;
    private ParcelAssignmentRepository assignments;
    private ParcelAssignmentService service;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelRepository.class);
        assignments = mock(ParcelAssignmentRepository.class);
        service = new ParcelAssignmentService(parcels, assignments, Clock.fixed(NOW, ZoneOffset.UTC));

        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        parcel = Parcel.forCustomer("SD7K3M9Q2XWB", UUID.randomUUID(), address, address, ParcelSize.SMALL, 1000, null,
                new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW.minusSeconds(60));
        when(parcels.findById(PARCEL_ID)).thenReturn(Optional.of(parcel));
        when(assignments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void concurrentAssignmentAttemptsOnSameParcel_oneWinsAndOneFailsWithOptimisticLockException() throws Exception {
        UUID courier1 = UUID.randomUUID();
        UUID courier2 = UUID.randomUUID();

        // Simulate JPA optimistic locking: only the first saveAndFlush succeeds; the second sees stale version
        AtomicBoolean parcelFlushed = new AtomicBoolean(false);
        when(parcels.saveAndFlush(any())).thenAnswer(call -> {
            if (!parcelFlushed.compareAndSet(false, true)) {
                throw new OptimisticLockingFailureException("Row was updated or deleted by another transaction");
            }
            return call.getArgument(0);
        });

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        UUID[] couriers = new UUID[] {courier1, courier2};
        List<Callable<String>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final UUID courier = couriers[i];
            tasks.add(() -> {
                readyLatch.countDown();
                if (!startLatch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timeout waiting for startLatch");
                }
                try {
                    service.assignCourier(PARCEL_ID, courier, DISPATCHER_ID, ParcelAssignmentType.PICKUP);
                    return "SUCCESS";
                } catch (OptimisticLockingFailureException | ParcelConflictException e) {
                    return "CONFLICT";
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        List<Future<String>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        List<String> outcomes = new ArrayList<>();
        for (Future<String> future : futures) {
            outcomes.add(future.get());
        }

        Collections.sort(outcomes);
        // Exactly one successful assignment and one conflict
        assertThat(outcomes).containsExactly("CONFLICT", "SUCCESS");
    }

    @Test
    void sequentialReassignmentOnAlreadyAssignedParcelSucceedsAndMarksPreviousReassigned() {
        UUID courier1 = UUID.randomUUID();
        UUID courier2 = UUID.randomUUID();

        parcel.assignPickupCourier(courier1, NOW.minusSeconds(30));
        ParcelAssignmentRecord active = new ParcelAssignmentRecord(
                PARCEL_ID, courier1, ParcelAssignmentType.PICKUP, DISPATCHER_ID, NOW.minusSeconds(30));
        when(assignments.findFirstByParcelIdAndStatus(PARCEL_ID, ParcelAssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        ParcelAssignmentSummary summary = service.assignCourier(
                PARCEL_ID, courier2, DISPATCHER_ID, ParcelAssignmentType.PICKUP);

        assertThat(summary.courierId()).isEqualTo(courier2);
        assertThat(summary.status()).isEqualTo(ParcelAssignmentStatus.ACTIVE);
        assertThat(summary.completedAt()).isNull();

        assertThat(parcel.getAssignedCourierId()).isEqualTo(courier2);
        assertThat(active.getStatus()).isEqualTo(ParcelAssignmentStatus.REASSIGNED);
        assertThat(active.toSummary().completedAt()).isEqualTo(NOW);
    }

    @Test
    void retryingSameAssignmentIsIdempotent() {
        UUID courier = UUID.randomUUID();
        parcel.assignCourier(courier, NOW.minusSeconds(30));
        ParcelAssignmentRecord active = new ParcelAssignmentRecord(
                PARCEL_ID, courier, ParcelAssignmentType.PICKUP, DISPATCHER_ID, NOW.minusSeconds(30));
        when(assignments.findFirstByParcelIdAndStatus(PARCEL_ID, ParcelAssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        ParcelAssignmentSummary summary = service.assignCourier(
                PARCEL_ID, courier, DISPATCHER_ID, ParcelAssignmentType.PICKUP);

        assertThat(summary.courierId()).isEqualTo(courier);
        assertThat(summary.id()).isEqualTo(active.toSummary().id());
        assertThat(active.getStatus()).isEqualTo(ParcelAssignmentStatus.ACTIVE);
    }
}
