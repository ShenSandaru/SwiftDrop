package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationConsumerState;
import com.swiftdrop.notification.domain.NotificationConsumerStateRepository;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationPendingEvent;
import com.swiftdrop.notification.domain.NotificationPendingEventRepository;
import com.swiftdrop.notification.domain.NotificationPendingEventStatus;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.notification.domain.NotificationRecordRepository;
import com.swiftdrop.notification.infrastructure.NotificationReconciliationProperties;

class NotificationReconciliationServiceTest {

    private InMemoryPendingEventRepository pendingRepo;
    private InMemoryConsumerStateRepository consumerStateRepo;
    private InMemoryRecordRepository recordRepo;
    private StubParcelEvents parcelEvents;
    private NotificationEventProcessor processor;
    private NotificationReconciliationService service;
    private MutableClock clock;

    private final UUID customerId = UUID.randomUUID();
    private final Instant baseTime = Instant.parse("2026-11-13T10:00:00Z");

    @BeforeEach
    void setUp() {
        pendingRepo = new InMemoryPendingEventRepository();
        consumerStateRepo = new InMemoryConsumerStateRepository();
        recordRepo = new InMemoryRecordRepository();
        parcelEvents = new StubParcelEvents();
        clock = new MutableClock(baseTime);

        NotificationTemplateResolver templateResolver = new NotificationTemplateResolver();
        ParcelStatusEventParser eventParser = new ParcelStatusEventParser();
        UserDirectory userDirectory = new StubUserDirectory(customerId);

        processor = new NotificationEventProcessor(
                consumerStateRepo,
                pendingRepo,
                recordRepo,
                templateResolver,
                eventParser,
                parcelEvents,
                userDirectory);

        NotificationReconciliationProperties properties = new NotificationReconciliationProperties(
                true,
                Duration.ofSeconds(30),
                Duration.ofSeconds(15),
                50,
                5);

        service = new NotificationReconciliationService(pendingRepo, processor, properties, clock);
    }

    @Test
    @DisplayName("Events buffered less than 15 seconds ago are ignored by the reconciliation worker")
    void eventsUnderGracePeriodIgnored() {
        UUID parcelId = UUID.randomUUID();
        // Buffered at baseTime (0 seconds ago)
        createAndBufferPending(parcelId, 2, baseTime);

        int processed = service.reconcile();
        assertThat(processed).isEqualTo(0);

        // Advance 10 seconds (still under 15s grace period)
        clock.set(baseTime.plusSeconds(10));
        processed = service.reconcile();
        assertThat(processed).isEqualTo(0);
    }

    @Test
    @DisplayName("Lock contention causes parcel to be skipped without incrementing retry count")
    void lockContentionSkipsWithoutIncrementingRetryCount() {
        UUID parcelId = UUID.randomUUID();
        NotificationPendingEvent event = createAndBufferPending(parcelId, 2, baseTime.minusSeconds(20));

        // Simulate lock held by another thread
        consumerStateRepo.lockBusy = true;

        int processed = service.reconcile();
        assertThat(processed).isEqualTo(0);

        // Retry count must NOT be incremented
        assertThat(event.getRetryCount()).isEqualTo(0);
        assertThat(event.getStatus()).isEqualTo(NotificationPendingEventStatus.PENDING);
    }

    @Test
    @DisplayName("Incomplete history increments retry count and marks UNRESOLVED on 5th attempt")
    void incompleteHistoryIncrementsAndQuarantinesOnFifthAttempt() {
        UUID parcelId = UUID.randomUUID();
        NotificationPendingEvent event = createAndBufferPending(parcelId, 2, baseTime.minusSeconds(20));

        // parcelEvents returns empty list (gap sequence 1 not yet in delivery outbox)
        parcelEvents.returnedEvents.clear();

        // Attempts 1 to 4: retry count increments, status remains PENDING
        for (int i = 1; i <= 4; i++) {
            clock.set(baseTime.plusSeconds(i * 30));
            service.reconcile();
            assertThat(event.getRetryCount()).isEqualTo(i);
            assertThat(event.getStatus()).isEqualTo(NotificationPendingEventStatus.PENDING);
            assertThat(event.getLastReconciledAt()).isEqualTo(clock.instant());
        }

        // Attempt 5: retry count reaches 5, marked UNRESOLVED
        clock.set(baseTime.plusSeconds(150));
        service.reconcile();
        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(NotificationPendingEventStatus.UNRESOLVED);

        // Subsequent reconciliation runs ignore UNRESOLVED events (dropped from PENDING index query)
        clock.set(baseTime.plusSeconds(180));
        int processedAfterQuarantine = service.reconcile();
        assertThat(processedAfterQuarantine).isEqualTo(0);
        assertThat(event.getRetryCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("Successful history recovery applies gap events, updates watermark, and drains pending")
    void successfulRecoveryAppliesGapAndDrainsPending() {
        UUID parcelId = UUID.randomUUID();
        // Buffer sequence 2 and sequence 3
        createAndBufferPending(parcelId, 2, baseTime.minusSeconds(20));
        createAndBufferPending(parcelId, 3, baseTime.minusSeconds(20));

        // Missing sequence 1 is provided by ParcelEvents
        ParcelStatusChanged seq1 = createEvent(parcelId, 1, ParcelStatus.CREATED);
        parcelEvents.returnedEvents.add(seq1);

        int processed = service.reconcile();
        assertThat(processed).isEqualTo(1);

        // Consumer state watermark advanced to 3
        NotificationConsumerState state = consumerStateRepo.findById(parcelId).orElseThrow();
        assertThat(state.getLastAppliedSequence()).isEqualTo(3);

        // Both pending events were drained and deleted
        assertThat(pendingRepo.findByParcelIdOrderBySequenceAsc(parcelId)).isEmpty();

        // 3 NotificationRecords created (seq 1, 2, 3)
        assertThat(recordRepo.records).hasSize(3);
    }

    private NotificationPendingEvent createAndBufferPending(UUID parcelId, int sequence, Instant receivedAt) {
        ParcelStatusChanged event = createEvent(parcelId, sequence, ParcelStatus.OUT_FOR_DELIVERY);
        String payload = createPayload(event);
        NotificationPendingEvent pending = new NotificationPendingEvent(
                event.eventId(),
                parcelId,
                sequence,
                payload,
                receivedAt);
        return pendingRepo.save(pending);
    }

    private ParcelStatusChanged createEvent(UUID parcelId, int sequence, ParcelStatus status) {
        return ParcelStatusChanged.of(
                UUID.randomUUID(),
                Instant.now(),
                new ParcelStatusChangedData(
                        parcelId,
                        "SD1234567890",
                        ParcelStatus.CREATED,
                        status,
                        sequence,
                        ParcelSource.CUSTOMER,
                        customerId,
                        null,
                        null,
                        null,
                        "HUB-01",
                        1));
    }

    private String createPayload(ParcelStatusChanged event) {
        return "{"
                + "\"eventId\":\"" + event.eventId() + "\","
                + "\"eventType\":\"" + event.eventType() + "\","
                + "\"schemaVersion\":" + event.schemaVersion() + ","
                + "\"occurredAt\":\"" + event.occurredAt() + "\","
                + "\"producer\":\"" + event.producer() + "\","
                + "\"data\":{"
                + "\"parcelId\":\"" + event.data().parcelId() + "\","
                + "\"trackingNumber\":\"" + event.data().trackingNumber() + "\","
                + "\"newStatus\":\"" + event.data().newStatus() + "\","
                + "\"sequence\":" + event.data().sequence() + ","
                + "\"source\":\"" + event.data().source() + "\","
                + "\"ownerCustomerId\":\"" + event.data().ownerCustomerId() + "\""
                + "}"
                + "}";
    }

    // --- In-Memory Test Stubs ---

    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static class StubParcelEvents implements ParcelEvents {
        final List<ParcelStatusChanged> returnedEvents = new ArrayList<>();

        @Override
        public List<ParcelStatusChanged> eventsBetween(UUID parcelId, int fromSequence, int toSequence) {
            return new ArrayList<>(returnedEvents);
        }
    }

    static class StubUserDirectory implements UserDirectory {
        private final UUID customerId;

        StubUserDirectory(UUID customerId) {
            this.customerId = customerId;
        }

        @Override
        public Optional<UserSummary> findUser(UUID id) {
            if (id.equals(customerId)) {
                return Optional.of(new UserSummary(id, "Test Cust", "cust@example.com", java.util.Set.of("CUSTOMER"), true));
            }
            return Optional.empty();
        }

        @Override
        public List<UserSummary> findEnabledCouriers() {
            return List.of();
        }

        @Override
        public UUID createMerchantUser(String fullName, String email, String rawPassword) {
            return UUID.randomUUID();
        }
    }

    static class InMemoryPendingEventRepository implements NotificationPendingEventRepository {
        final List<NotificationPendingEvent> storage = new ArrayList<>();

        @Override
        public NotificationPendingEvent save(NotificationPendingEvent entity) {
            storage.removeIf(e -> e.getId().equals(entity.getId()));
            storage.add(entity);
            return entity;
        }

        @Override
        public Optional<NotificationPendingEvent> findByParcelIdAndSequence(UUID parcelId, int sequence) {
            return storage.stream()
                    .filter(e -> e.getParcelId().equals(parcelId) && e.getSequence() == sequence)
                    .findFirst();
        }

        @Override
        public List<NotificationPendingEvent> findByParcelIdOrderBySequenceAsc(UUID parcelId) {
            return storage.stream()
                    .filter(e -> e.getParcelId().equals(parcelId))
                    .sorted(java.util.Comparator.comparingInt(NotificationPendingEvent::getSequence))
                    .toList();
        }

        @Override
        public List<NotificationPendingEvent> findByStatusAndReceivedAtBeforeOrderByReceivedAtAsc(
                NotificationPendingEventStatus status, Instant receivedBeforeCutoff, Limit limit) {
            return storage.stream()
                    .filter(e -> e.getStatus() == status && !e.getReceivedAt().isAfter(receivedBeforeCutoff))
                    .sorted(java.util.Comparator.comparing(NotificationPendingEvent::getReceivedAt))
                    .limit(limit.max())
                    .toList();
        }

        @Override
        public long countByStatus(NotificationPendingEventStatus status) {
            return storage.stream().filter(e -> e.getStatus() == status).count();
        }

        @Override
        public void delete(NotificationPendingEvent entity) {
            storage.removeIf(e -> e.getId().equals(entity.getId()));
        }

        // Unused JPA methods
        @Override public void flush() {}
        @Override public <S extends NotificationPendingEvent> S saveAndFlush(S entity) { return null; }
        @Override public <S extends NotificationPendingEvent> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<NotificationPendingEvent> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public NotificationPendingEvent getOne(UUID id) { return null; }
        @Override public NotificationPendingEvent getById(UUID id) { return null; }
        @Override public NotificationPendingEvent getReferenceById(UUID id) { return null; }
        @Override public <S extends NotificationPendingEvent> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends NotificationPendingEvent> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends NotificationPendingEvent> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public <S extends NotificationPendingEvent> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends NotificationPendingEvent> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends NotificationPendingEvent> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends NotificationPendingEvent, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends NotificationPendingEvent> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public Optional<NotificationPendingEvent> findById(UUID id) { return storage.stream().filter(e -> e.getId().equals(id)).findFirst(); }
        @Override public boolean existsById(UUID id) { return findById(id).isPresent(); }
        @Override public List<NotificationPendingEvent> findAll() { return new ArrayList<>(storage); }
        @Override public List<NotificationPendingEvent> findAllById(Iterable<UUID> ids) { return List.of(); }
        @Override public long count() { return storage.size(); }
        @Override public void deleteById(UUID id) { storage.removeIf(e -> e.getId().equals(id)); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) {}
        @Override public void deleteAll(Iterable<? extends NotificationPendingEvent> entities) {}
        @Override public void deleteAll() { storage.clear(); }
        @Override public List<NotificationPendingEvent> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public org.springframework.data.domain.Page<NotificationPendingEvent> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
    }

    static class InMemoryConsumerStateRepository implements NotificationConsumerStateRepository {
        final List<NotificationConsumerState> states = new ArrayList<>();
        boolean lockBusy = false;

        @Override
        public void acquireParcelLock(UUID parcelId) {}

        @Override
        public boolean tryAcquireParcelLock(UUID parcelId) {
            return !lockBusy;
        }

        @Override
        public Optional<NotificationConsumerState> findById(UUID parcelId) {
            return states.stream().filter(s -> s.getParcelId().equals(parcelId)).findFirst();
        }

        @Override
        public NotificationConsumerState save(NotificationConsumerState entity) {
            states.removeIf(s -> s.getParcelId().equals(entity.getParcelId()));
            states.add(entity);
            return entity;
        }

        // Unused JPA methods
        @Override public void flush() {}
        @Override public <S extends NotificationConsumerState> S saveAndFlush(S entity) { return null; }
        @Override public <S extends NotificationConsumerState> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<NotificationConsumerState> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public NotificationConsumerState getOne(UUID id) { return null; }
        @Override public NotificationConsumerState getById(UUID id) { return null; }
        @Override public NotificationConsumerState getReferenceById(UUID id) { return null; }
        @Override public <S extends NotificationConsumerState> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends NotificationConsumerState> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends NotificationConsumerState> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public <S extends NotificationConsumerState> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends NotificationConsumerState> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends NotificationConsumerState> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends NotificationConsumerState, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends NotificationConsumerState> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public boolean existsById(UUID id) { return findById(id).isPresent(); }
        @Override public List<NotificationConsumerState> findAll() { return new ArrayList<>(states); }
        @Override public List<NotificationConsumerState> findAllById(Iterable<UUID> ids) { return List.of(); }
        @Override public long count() { return states.size(); }
        @Override public void deleteById(UUID id) { states.removeIf(s -> s.getParcelId().equals(id)); }
        @Override public void delete(NotificationConsumerState entity) { states.removeIf(s -> s.getParcelId().equals(entity.getParcelId())); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) {}
        @Override public void deleteAll(Iterable<? extends NotificationConsumerState> entities) {}
        @Override public void deleteAll() { states.clear(); }
        @Override public List<NotificationConsumerState> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public org.springframework.data.domain.Page<NotificationConsumerState> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
    }

    static class InMemoryRecordRepository implements NotificationRecordRepository {
        final List<NotificationRecord> records = new ArrayList<>();

        @Override
        public boolean existsByEventIdAndChannelAndRecipientUserId(UUID eventId, NotificationChannel channel, UUID recipientUserId) {
            return records.stream().anyMatch(r -> r.getEventId().equals(eventId)
                    && r.getChannel() == channel
                    && r.getRecipientUserId().equals(recipientUserId));
        }

        @Override
        public Optional<NotificationRecord> findByEventIdAndChannelAndRecipientUserId(UUID eventId, NotificationChannel channel, UUID recipientUserId) {
            return records.stream().filter(r -> r.getEventId().equals(eventId)
                    && r.getChannel() == channel
                    && r.getRecipientUserId().equals(recipientUserId)).findFirst();
        }

        @Override
        public NotificationRecord save(NotificationRecord entity) {
            records.add(entity);
            return entity;
        }

        @Override
        public Optional<NotificationRecord> findByIdAndRecipientUserIdAndChannel(UUID id, UUID recipientUserId, NotificationChannel channel) {
            return records.stream().filter(r -> r.getId().equals(id)
                    && r.getRecipientUserId().equals(recipientUserId)
                    && r.getChannel() == channel).findFirst();
        }

        @Override
        public org.springframework.data.domain.Page<NotificationRecord> findByRecipientUserIdAndChannel(UUID recipientUserId, NotificationChannel channel, org.springframework.data.domain.Pageable pageable) {
            List<NotificationRecord> matches = records.stream()
                    .filter(r -> r.getRecipientUserId().equals(recipientUserId) && r.getChannel() == channel)
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(matches, pageable, matches.size());
        }

        @Override
        public int markAllAsReadForRecipient(UUID recipientUserId, java.time.Instant readAt) {
            int updated = 0;
            for (NotificationRecord r : records) {
                if (r.getRecipientUserId().equals(recipientUserId) && !r.isRead() && r.getChannel() == NotificationChannel.IN_APP) {
                    r.markAsRead(readAt);
                    updated++;
                }
            }
            return updated;
        }

        @Override
        public org.springframework.data.domain.Page<NotificationRecord> findByRecipientUserId(UUID recipientUserId, org.springframework.data.domain.Pageable pageable) {
            List<NotificationRecord> matches = records.stream().filter(r -> r.getRecipientUserId().equals(recipientUserId)).toList();
            return new org.springframework.data.domain.PageImpl<>(matches, pageable, matches.size());
        }

        @Override
        public org.springframework.data.domain.Page<NotificationRecord> findByRecipientUserIdAndChannelAndIsReadFalse(UUID recipientUserId, NotificationChannel channel, org.springframework.data.domain.Pageable pageable) {
            return null;
        }

        @Override
        public long countByRecipientUserIdAndChannelAndIsReadFalse(UUID recipientUserId, NotificationChannel channel) {
            return 0;
        }

        @Override
        public org.springframework.data.domain.Page<NotificationRecord> findByParcelId(UUID parcelId, org.springframework.data.domain.Pageable pageable) {
            return null;
        }

        // Unused JPA methods
        @Override public void flush() {}
        @Override public <S extends NotificationRecord> S saveAndFlush(S entity) { return null; }
        @Override public <S extends NotificationRecord> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<NotificationRecord> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public NotificationRecord getOne(UUID id) { return null; }
        @Override public NotificationRecord getById(UUID id) { return null; }
        @Override public NotificationRecord getReferenceById(UUID id) { return null; }
        @Override public <S extends NotificationRecord> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends NotificationRecord> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends NotificationRecord> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public <S extends NotificationRecord> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends NotificationRecord> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends NotificationRecord> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends NotificationRecord, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends NotificationRecord> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public Optional<NotificationRecord> findById(UUID id) { return records.stream().filter(r -> r.getId().equals(id)).findFirst(); }
        @Override public boolean existsById(UUID id) { return findById(id).isPresent(); }
        @Override public List<NotificationRecord> findAll() { return new ArrayList<>(records); }
        @Override public List<NotificationRecord> findAllById(Iterable<UUID> ids) { return List.of(); }
        @Override public long count() { return records.size(); }
        @Override public void deleteById(UUID id) { records.removeIf(r -> r.getId().equals(id)); }
        @Override public void delete(NotificationRecord entity) { records.removeIf(r -> r.getId().equals(entity.getId())); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) {}
        @Override public void deleteAll(Iterable<? extends NotificationRecord> entities) {}
        @Override public void deleteAll() { records.clear(); }
        @Override public List<NotificationRecord> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public org.springframework.data.domain.Page<NotificationRecord> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
    }
}

