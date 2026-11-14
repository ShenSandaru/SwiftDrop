package com.swiftdrop.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.pricing.api.FeeCalculator;

@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class NotificationAdvisoryLockIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private NotificationConsumerStateRepository consumerStateRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private TransactionTemplate tx;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("Advisory lock can be acquired and works when no consumer_state row exists")
    void acquireLockWithoutConsumerStateRow() {
        UUID parcelId = UUID.randomUUID();

        // Confirm row does not exist
        assertThat(consumerStateRepository.findById(parcelId)).isEmpty();

        tx.executeWithoutResult(status -> {
            consumerStateRepository.acquireParcelLock(parcelId);
            boolean acquiredAgainInSameTx = consumerStateRepository.tryAcquireParcelLock(parcelId);
            assertThat(acquiredAgainInSameTx).isTrue();
        });

        // Still no row created, but lock executed cleanly
        assertThat(consumerStateRepository.findById(parcelId)).isEmpty();
    }

    @Test
    @DisplayName("Different parcels acquire advisory locks independently without blocking")
    void differentParcelsLockIndependently() throws Exception {
        UUID parcel1 = UUID.randomUUID();
        UUID parcel2 = UUID.randomUUID();

        CountDownLatch t1LockAcquired = new CountDownLatch(1);
        CountDownLatch t2Done = new CountDownLatch(1);

        Future<Boolean> f1 = executor.submit(() -> tx.execute(status -> {
            consumerStateRepository.acquireParcelLock(parcel1);
            t1LockAcquired.countDown();
            try {
                // Hold lock until t2 finishes
                assertThat(t2Done.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            return true;
        }));

        assertThat(t1LockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        // Transaction 2 locks a different parcel concurrently - must not block
        Future<Boolean> f2 = executor.submit(() -> tx.execute(status -> {
            boolean acquired = consumerStateRepository.tryAcquireParcelLock(parcel2);
            t2Done.countDown();
            return acquired;
        }));

        assertThat(f2.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(f1.get(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("Same parcel lock serializes: second transaction waits until first commits")
    void sameParcelSerializesUntilCommit() throws Exception {
        UUID parcelId = UUID.randomUUID();

        CountDownLatch t1Locked = new CountDownLatch(1);
        CountDownLatch t1Release = new CountDownLatch(1);
        CountDownLatch t2Started = new CountDownLatch(1);
        AtomicBoolean t2AcquiredAfterT1 = new AtomicBoolean(false);

        // Transaction 1 acquires lock and holds it
        Future<Void> f1 = executor.submit(() -> {
            tx.executeWithoutResult(status -> {
                consumerStateRepository.acquireParcelLock(parcelId);
                t1Locked.countDown();
                try {
                    assertThat(t1Release.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return null;
        });

        assertThat(t1Locked.await(5, TimeUnit.SECONDS)).isTrue();

        // Transaction 2 tries to acquire same lock
        Future<Void> f2 = executor.submit(() -> {
            t2Started.countDown();
            tx.executeWithoutResult(status -> {
                consumerStateRepository.acquireParcelLock(parcelId);
                t2AcquiredAfterT1.set(true);
            });
            return null;
        });

        assertThat(t2Started.await(5, TimeUnit.SECONDS)).isTrue();

        // Wait brief period: T2 must NOT have acquired yet while T1 holds it
        Thread.sleep(200);
        assertThat(t2AcquiredAfterT1.get()).isFalse();

        // Release T1 commit
        t1Release.countDown();
        f1.get(5, TimeUnit.SECONDS);

        // Now T2 completes acquisition
        f2.get(5, TimeUnit.SECONDS);
        assertThat(t2AcquiredAfterT1.get()).isTrue();
    }

    @Test
    @DisplayName("Lock is automatically released after transaction rollback")
    void lockReleasedOnRollback() throws Exception {
        UUID parcelId = UUID.randomUUID();

        CountDownLatch t1Locked = new CountDownLatch(1);
        CountDownLatch t1Rollback = new CountDownLatch(1);

        Future<Void> f1 = executor.submit(() -> {
            try {
                tx.executeWithoutResult(status -> {
                    consumerStateRepository.acquireParcelLock(parcelId);
                    t1Locked.countDown();
                    try {
                        t1Rollback.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    status.setRollbackOnly();
                });
            } catch (Exception ignored) {
            }
            return null;
        });

        assertThat(t1Locked.await(5, TimeUnit.SECONDS)).isTrue();

        // While T1 is active, tryLock in another tx must return false
        tx.executeWithoutResult(status -> {
            boolean acquired = consumerStateRepository.tryAcquireParcelLock(parcelId);
            assertThat(acquired).isFalse();
        });

        // Trigger rollback in T1
        t1Rollback.countDown();
        f1.get(5, TimeUnit.SECONDS);

        // Now T1 is rolled back; lock must be freely acquirable in a new transaction
        tx.executeWithoutResult(status -> {
            boolean acquired = consumerStateRepository.tryAcquireParcelLock(parcelId);
            assertThat(acquired).isTrue();
        });
    }
}

