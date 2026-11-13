package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.notification.application.NotificationTemplateResolver.NotificationContent;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationConsumerState;
import com.swiftdrop.notification.domain.NotificationConsumerStateRepository;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationPendingEvent;
import com.swiftdrop.notification.domain.NotificationPendingEventRepository;
import com.swiftdrop.notification.domain.NotificationPendingEventStatus;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

@ExtendWith(MockitoExtension.class)
class NotificationEventProcessorTest {

    @Mock
    private NotificationConsumerStateRepository consumerStateRepository;

    @Mock
    private NotificationPendingEventRepository pendingEventRepository;

    @Mock
    private NotificationRecordRepository recordRepository;

    @Mock
    private NotificationTemplateResolver templateResolver;

    @Mock
    private ParcelStatusEventParser eventParser;

    @Mock
    private ParcelEvents parcelEvents;

    @Mock
    private UserDirectory userDirectory;

    private NotificationEventProcessor processor;

    private final UUID parcelId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final String trackingNumber = "SD1234567890";
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        processor = new NotificationEventProcessor(
                consumerStateRepository,
                pendingEventRepository,
                recordRepository,
                templateResolver,
                eventParser,
                parcelEvents,
                userDirectory);
    }

    private ParcelStatusChanged createCustomerEvent(int sequence, ParcelStatus newStatus) {
        return ParcelStatusChanged.of(
                UUID.randomUUID(),
                now,
                new ParcelStatusChangedData(
                        parcelId,
                        trackingNumber,
                        ParcelStatus.CREATED,
                        newStatus,
                        sequence,
                        ParcelSource.CUSTOMER,
                        customerId,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    private ParcelStatusChanged createMerchantEvent(int sequence, ParcelStatus newStatus) {
        return ParcelStatusChanged.of(
                UUID.randomUUID(),
                now,
                new ParcelStatusChangedData(
                        parcelId,
                        trackingNumber,
                        ParcelStatus.CREATED,
                        newStatus,
                        sequence,
                        ParcelSource.MERCHANT,
                        null,
                        UUID.randomUUID(),
                        null,
                        null,
                        null,
                        null));
    }

    @Test
    @DisplayName("Initial event (sequence 1) with no prior consumer state acquires lock, creates state with watermark 1, and creates notification")
    void initialEventProcessing() {
        ParcelStatusChanged event = createCustomerEvent(1, ParcelStatus.CREATED);
        String rawJson = "{\"eventId\":\"" + event.eventId() + "\"}";

        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.empty());
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 1)).thenReturn(Optional.empty());
        when(userDirectory.findUser(customerId)).thenReturn(Optional.of(new UserSummary(customerId, "Test", "test@example.com", Set.of("CUSTOMER"), true)));
        when(templateResolver.resolve(event)).thenReturn(Optional.of(new NotificationContent("Parcel Booked", "Your parcel SD1234567890 has been booked.")));
        when(recordRepository.existsByEventIdAndChannelAndRecipientUserId(event.eventId(), NotificationChannel.IN_APP, customerId)).thenReturn(false);

        NotificationConsumerState savedState = new NotificationConsumerState(parcelId, 1, now);
        when(consumerStateRepository.save(any(NotificationConsumerState.class))).thenReturn(savedState);
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 2)).thenReturn(Optional.empty());

        processor.process(event, rawJson);

        verify(consumerStateRepository).acquireParcelLock(parcelId);
        ArgumentCaptor<NotificationConsumerState> stateCaptor = ArgumentCaptor.forClass(NotificationConsumerState.class);
        verify(consumerStateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getParcelId()).isEqualTo(parcelId);
        assertThat(stateCaptor.getValue().getLastAppliedSequence()).isEqualTo(1);

        ArgumentCaptor<NotificationRecord> recordCaptor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(recordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getRecipientUserId()).isEqualTo(customerId);
        assertThat(recordCaptor.getValue().getParcelId()).isEqualTo(parcelId);
        assertThat(recordCaptor.getValue().getTitle()).isEqualTo("Parcel Booked");
        assertThat(recordCaptor.getValue().getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(recordCaptor.getValue().getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("In-order event (sequence == lastApplied + 1) parses and applies canonical stored pending payload if present")
    void inOrderEventUsesCanonicalPendingPayload() {
        NotificationConsumerState existingState = new NotificationConsumerState(parcelId, 1, now.minusSeconds(60));
        ParcelStatusChanged incomingEvent = createCustomerEvent(2, ParcelStatus.PICKED_UP);
        String incomingRawJson = "{\"sequence\":2,\"incoming\":true}";

        String canonicalRawJson = "{\"sequence\":2,\"canonical\":true}";
        ParcelStatusChanged canonicalEvent = createCustomerEvent(2, ParcelStatus.PICKED_UP);
        NotificationPendingEvent existingPending = new NotificationPendingEvent(incomingEvent.eventId(), parcelId, 2, canonicalRawJson, now);

        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.of(existingState));
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 2)).thenReturn(Optional.of(existingPending));
        when(eventParser.parse(canonicalRawJson)).thenReturn(canonicalEvent);
        when(userDirectory.findUser(customerId)).thenReturn(Optional.of(new UserSummary(customerId, "Test", "test@example.com", Set.of("CUSTOMER"), true)));
        when(templateResolver.resolve(canonicalEvent)).thenReturn(Optional.of(new NotificationContent("Parcel Picked Up", "Picked up")));
        when(consumerStateRepository.save(existingState)).thenReturn(existingState);
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 3)).thenReturn(Optional.empty());

        processor.process(incomingEvent, incomingRawJson);

        verify(consumerStateRepository).acquireParcelLock(parcelId);
        verify(eventParser).parse(canonicalRawJson);
        verify(pendingEventRepository).delete(existingPending);
        verify(consumerStateRepository).save(existingState);
        assertThat(existingState.getLastAppliedSequence()).isEqualTo(2);
        verify(recordRepository).save(any(NotificationRecord.class));
    }

    @Test
    @DisplayName("In-order event with corrupted canonical pending payload marks entity UNRESOLVED and halts without advancing watermark")
    void inOrderEventCorruptedPendingPayloadHalts() {
        NotificationConsumerState existingState = new NotificationConsumerState(parcelId, 1, now.minusSeconds(60));
        ParcelStatusChanged incomingEvent = createCustomerEvent(2, ParcelStatus.PICKED_UP);
        String incomingRawJson = "{\"sequence\":2}";

        String corruptedRawJson = "{\"corrupted\":true}";
        NotificationPendingEvent corruptedPending = new NotificationPendingEvent(incomingEvent.eventId(), parcelId, 2, corruptedRawJson, now);

        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.of(existingState));
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 2)).thenReturn(Optional.of(corruptedPending));
        when(eventParser.parse(corruptedRawJson)).thenThrow(new EventParsingException("Malformed canonical JSON"));

        processor.process(incomingEvent, incomingRawJson);

        assertThat(corruptedPending.getStatus()).isEqualTo(NotificationPendingEventStatus.UNRESOLVED);
        verify(pendingEventRepository).save(corruptedPending);
        verify(pendingEventRepository, never()).delete(corruptedPending);
        verify(consumerStateRepository, never()).save(any());
        verifyNoInteractions(templateResolver);
        verifyNoInteractions(recordRepository);
    }

    @Test
    @DisplayName("In-order event with existing UNRESOLVED pending event halts immediately without re-parsing")
    void inOrderEventWithUnresolvedPendingHaltsImmediately() {
        NotificationConsumerState existingState = new NotificationConsumerState(parcelId, 1, now.minusSeconds(60));
        ParcelStatusChanged incomingEvent = createCustomerEvent(2, ParcelStatus.PICKED_UP);
        String incomingRawJson = "{\"sequence\":2}";

        NotificationPendingEvent unresolvedPending = new NotificationPendingEvent(incomingEvent.eventId(), parcelId, 2, "raw", now);
        unresolvedPending.markUnresolved();

        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.of(existingState));
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 2)).thenReturn(Optional.of(unresolvedPending));

        processor.process(incomingEvent, incomingRawJson);

        verifyNoInteractions(eventParser);
        verify(pendingEventRepository, never()).delete(any());
        verify(consumerStateRepository, never()).save(any());
        verifyNoInteractions(templateResolver);
        verifyNoInteractions(recordRepository);
    }

    @Test
    @DisplayName("Duplicate event (sequence <= lastApplied) is ignored and cleans any lingering pending record")
    void duplicateEventProcessing() {
        NotificationConsumerState existingState = new NotificationConsumerState(parcelId, 3, now);
        ParcelStatusChanged duplicateEvent = createCustomerEvent(2, ParcelStatus.PICKED_UP);
        String rawJson = "{\"sequence\":2}";

        NotificationPendingEvent lingering = new NotificationPendingEvent(duplicateEvent.eventId(), parcelId, 2, rawJson, now);
        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.of(existingState));
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 2)).thenReturn(Optional.of(lingering));

        processor.process(duplicateEvent, rawJson);

        verify(consumerStateRepository).acquireParcelLock(parcelId);
        verify(pendingEventRepository).delete(lingering);
        verifyNoInteractions(templateResolver);
        verifyNoInteractions(recordRepository);
        verify(consumerStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("Out-of-order event with incomplete gap recovery buffers event and does not advance watermark")
    void outOfOrderIncompleteRecovery() {
        NotificationConsumerState existingState = new NotificationConsumerState(parcelId, 1, now);
        ParcelStatusChanged event4 = createCustomerEvent(4, ParcelStatus.OUT_FOR_DELIVERY);
        String rawJson = "{\"sequence\":4}";

        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.of(existingState));
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 4)).thenReturn(Optional.empty());
        // Delivery returns incomplete history (only sequence 2, missing 3)
        ParcelStatusChanged event2 = createCustomerEvent(2, ParcelStatus.PICKED_UP);
        when(parcelEvents.eventsBetween(parcelId, 2, 3)).thenReturn(List.of(event2));

        processor.process(event4, rawJson);

        verify(consumerStateRepository).acquireParcelLock(parcelId);
        ArgumentCaptor<NotificationPendingEvent> pendingCaptor = ArgumentCaptor.forClass(NotificationPendingEvent.class);
        verify(pendingEventRepository).save(pendingCaptor.capture());
        assertThat(pendingCaptor.getValue().getParcelId()).isEqualTo(parcelId);
        assertThat(pendingCaptor.getValue().getSequence()).isEqualTo(4);

        // Watermark must not advance, no notifications created
        verify(consumerStateRepository, never()).save(any());
        verifyNoInteractions(recordRepository);
    }

    @Test
    @DisplayName("Out-of-order event with full gap recovery applies history in order, applies current event, and drains pending buffer")
    void outOfOrderFullRecoveryAndDraining() {
        NotificationConsumerState existingState = new NotificationConsumerState(parcelId, 1, now);
        ParcelStatusChanged event3 = createCustomerEvent(3, ParcelStatus.IN_TRANSIT);
        String rawJson3 = "{\"sequence\":3}";

        ParcelStatusChanged event2 = createCustomerEvent(2, ParcelStatus.PICKED_UP);
        ParcelStatusChanged event4 = createCustomerEvent(4, ParcelStatus.DELIVERED);
        String rawJson4 = "{\"sequence\":4}";
        NotificationPendingEvent pending4 = new NotificationPendingEvent(event4.eventId(), parcelId, 4, rawJson4, now);

        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.of(existingState));
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 3)).thenReturn(Optional.empty());
        when(parcelEvents.eventsBetween(parcelId, 2, 2)).thenReturn(List.of(event2));
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 2)).thenReturn(Optional.empty());
        when(userDirectory.findUser(customerId)).thenReturn(Optional.of(new UserSummary(customerId, "Test", "test@example.com", Set.of("CUSTOMER"), true)));
        when(templateResolver.resolve(any())).thenReturn(Optional.of(new NotificationContent("Title", "Message")));
        when(consumerStateRepository.save(any(NotificationConsumerState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When draining contiguous pending events:
        // after applying 3, watermark is 3 -> look for 4 -> found pending4 -> parse -> apply -> look for 5 -> empty
        doReturn(Optional.of(pending4)).when(pendingEventRepository).findByParcelIdAndSequence(parcelId, 4);
        when(eventParser.parse(rawJson4)).thenReturn(event4);
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 5)).thenReturn(Optional.empty());

        processor.process(event3, rawJson3);

        // Sequence 2, 3, and 4 should all have been applied
        verify(recordRepository, times(3)).save(any(NotificationRecord.class));
        verify(pendingEventRepository).delete(pending4);
        assertThat(existingState.getLastAppliedSequence()).isEqualTo(4);
    }

    @Test
    @DisplayName("CUSTOMER event with null ownerCustomerId throws EventParsingException")
    void customerEventNullOwnerThrows() {
        ParcelStatusChanged event = ParcelStatusChanged.of(
                UUID.randomUUID(),
                now,
                new ParcelStatusChangedData(
                        parcelId,
                        trackingNumber,
                        ParcelStatus.CREATED,
                        ParcelStatus.CREATED,
                        1,
                        ParcelSource.CUSTOMER,
                        null, // missing customer ID!
                        null,
                        null,
                        null,
                        null,
                        null));
        String rawJson = "{}";

        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(event, rawJson))
                .isInstanceOf(EventParsingException.class)
                .hasMessageContaining("must have non-null ownerCustomerId");

        verify(recordRepository, never()).save(any());
        verify(consumerStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("CUSTOMER event with disabled or missing customer advances watermark without creating notification")
    void customerDisabledOrMissingAdvancesWatermarkWithoutNotification() {
        ParcelStatusChanged event = createCustomerEvent(1, ParcelStatus.CREATED);
        String rawJson = "{\"sequence\":1}";

        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.empty());
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 1)).thenReturn(Optional.empty());
        when(userDirectory.findUser(customerId)).thenReturn(Optional.of(new UserSummary(customerId, "Test", "test@example.com", Set.of("CUSTOMER"), false))); // disabled
        when(consumerStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 2)).thenReturn(Optional.empty());

        processor.process(event, rawJson);

        verify(consumerStateRepository).save(any());
        verifyNoInteractions(templateResolver);
        verifyNoInteractions(recordRepository);
    }

    @Test
    @DisplayName("MERCHANT event advances watermark without querying UserDirectory or creating customer notification")
    void merchantEventAdvancesWatermarkWithoutNotification() {
        ParcelStatusChanged event = createMerchantEvent(1, ParcelStatus.CREATED);
        String rawJson = "{\"sequence\":1}";

        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.empty());
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 1)).thenReturn(Optional.empty());
        when(consumerStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 2)).thenReturn(Optional.empty());

        processor.process(event, rawJson);

        verifyNoInteractions(userDirectory);
        verifyNoInteractions(templateResolver);
        verifyNoInteractions(recordRepository);
        verify(consumerStateRepository).save(any());
    }

    @Test
    @DisplayName("Idempotent pending event buffering does not overwrite existing pending record")
    void idempotentPendingBuffering() {
        NotificationConsumerState existingState = new NotificationConsumerState(parcelId, 1, now);
        ParcelStatusChanged event = createCustomerEvent(3, ParcelStatus.IN_TRANSIT);
        String rawJson = "{\"sequence\":3}";

        NotificationPendingEvent existingPending = new NotificationPendingEvent(UUID.randomUUID(), parcelId, 3, "existing-raw", now);
        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.of(existingState));
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 3)).thenReturn(Optional.of(existingPending));
        when(parcelEvents.eventsBetween(parcelId, 2, 2)).thenReturn(Collections.emptyList());

        processor.process(event, rawJson);

        verify(pendingEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Corrupted pending event payload during draining marks entity UNRESOLVED and halts draining")
    void corruptedPayloadMarksUnresolvedAndHaltsDraining() {
        NotificationConsumerState existingState = new NotificationConsumerState(parcelId, 1, now);
        ParcelStatusChanged event2 = createCustomerEvent(2, ParcelStatus.PICKED_UP);
        String rawJson2 = "{\"sequence\":2}";

        // Buffered pending event has mismatched sequence in payload
        String corruptedRawJson = "{\"corrupted\":true}";
        NotificationPendingEvent corruptedPending = new NotificationPendingEvent(UUID.randomUUID(), parcelId, 3, corruptedRawJson, now);

        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.of(existingState));
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 2)).thenReturn(Optional.empty());
        when(userDirectory.findUser(customerId)).thenReturn(Optional.of(new UserSummary(customerId, "Test", "test@example.com", Set.of("CUSTOMER"), true)));
        when(templateResolver.resolve(event2)).thenReturn(Optional.of(new NotificationContent("Title", "Message")));
        when(consumerStateRepository.save(existingState)).thenReturn(existingState);

        // Sequence 3 is found in buffer
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 3)).thenReturn(Optional.of(corruptedPending));
        when(eventParser.parse(corruptedRawJson)).thenThrow(new EventParsingException("Malformed JSON"));

        processor.process(event2, rawJson2);

        // Watermark stopped at 2
        assertThat(existingState.getLastAppliedSequence()).isEqualTo(2);
        // Pending event marked UNRESOLVED and saved
        assertThat(corruptedPending.getStatus()).isEqualTo(NotificationPendingEventStatus.UNRESOLVED);
        verify(pendingEventRepository).save(corruptedPending);
        // Sequence 4 should never be queried
        verify(pendingEventRepository, never()).findByParcelIdAndSequence(parcelId, 4);
    }

    @Test
    @DisplayName("Already UNRESOLVED pending event encountered during draining halts draining immediately without re-parsing")
    void alreadyUnresolvedPendingHaltsDrainingWithoutReparsing() {
        NotificationConsumerState existingState = new NotificationConsumerState(parcelId, 1, now);
        ParcelStatusChanged event2 = createCustomerEvent(2, ParcelStatus.PICKED_UP);
        String rawJson2 = "{\"sequence\":2}";

        NotificationPendingEvent unresolvedPending = new NotificationPendingEvent(UUID.randomUUID(), parcelId, 3, "raw", now);
        unresolvedPending.markUnresolved();

        when(consumerStateRepository.findById(parcelId)).thenReturn(Optional.of(existingState));
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 2)).thenReturn(Optional.empty());
        when(userDirectory.findUser(customerId)).thenReturn(Optional.of(new UserSummary(customerId, "Test", "test@example.com", Set.of("CUSTOMER"), true)));
        when(templateResolver.resolve(event2)).thenReturn(Optional.of(new NotificationContent("Title", "Message")));
        when(consumerStateRepository.save(existingState)).thenReturn(existingState);

        // Sequence 3 is already UNRESOLVED in buffer
        when(pendingEventRepository.findByParcelIdAndSequence(parcelId, 3)).thenReturn(Optional.of(unresolvedPending));

        processor.process(event2, rawJson2);

        // Watermark stopped at 2
        assertThat(existingState.getLastAppliedSequence()).isEqualTo(2);
        // Parser must not be invoked for unresolved event
        verifyNoInteractions(eventParser);
        // Pending event must not be deleted or modified
        verify(pendingEventRepository, never()).delete(unresolvedPending);
        // Sequence 4 should never be queried
        verify(pendingEventRepository, never()).findByParcelIdAndSequence(parcelId, 4);
    }
}
