package com.swiftdrop.merchant.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantEventAnomaly;
import com.swiftdrop.merchant.domain.MerchantEventAnomalyRepository;
import com.swiftdrop.merchant.domain.MerchantEventState;
import com.swiftdrop.merchant.domain.MerchantEventStateRepository;
import com.swiftdrop.merchant.domain.MerchantPendingEvent;
import com.swiftdrop.merchant.domain.MerchantPendingEventRepository;
import com.swiftdrop.merchant.domain.MerchantProcessedEvent;
import com.swiftdrop.merchant.domain.MerchantProcessedEventRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Mocked repositories backed by in-memory maps, so processor and reconciler tests run the real ordering logic
 * for one merchant parcel. One state row, as the database lock would give the processor.
 */
class MerchantEventFixture {

    static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    static final UUID MERCHANT_ID = UUID.fromString("77aa0000-0000-0000-0000-000000000001");
    static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    static final String REFERENCE = "ORD-10045";
    /** Status reached at each sequence, in lifecycle order (1 = CREATED). */
    static final List<ParcelStatus> PATH = List.of(ParcelStatus.CREATED, ParcelStatus.PICKED_UP, ParcelStatus.AT_HUB,
            ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED);

    final MerchantEventStateRepository states = mock(MerchantEventStateRepository.class);
    final MerchantProcessedEventRepository processedRepository = mock(MerchantProcessedEventRepository.class);
    final MerchantPendingEventRepository pendingRepository = mock(MerchantPendingEventRepository.class);
    final MerchantEventAnomalyRepository anomalyRepository = mock(MerchantEventAnomalyRepository.class);
    final MerchantWebhookDeliveryRepository deliveries = mock(MerchantWebhookDeliveryRepository.class);
    final MerchantWebhookEndpointRepository endpoints = mock(MerchantWebhookEndpointRepository.class);
    final MerchantRepository merchants = mock(MerchantRepository.class);
    final ParcelQueries parcels = mock(ParcelQueries.class);
    final ParcelEvents history = mock(ParcelEvents.class);
    final MerchantEventJson json = new MerchantEventJson();
    final MerchantEventProperties properties = new MerchantEventProperties(null, null, null, null, null, null, null,
            null, null, null);
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final MerchantEventMetrics metrics = new MerchantEventMetrics(meters);
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    final Map<UUID, MerchantProcessedEvent> processed = new LinkedHashMap<>();
    final Map<UUID, MerchantPendingEvent> pending = new LinkedHashMap<>();
    final List<MerchantEventAnomaly> anomalies = new ArrayList<>();
    /** One entry per created delivery record: {endpointId, eventId, status name, payload}. */
    final List<Object[]> records = new ArrayList<>();
    final Merchant merchant;
    MerchantEventState state;

    MerchantEventFixture(UUID... subscribedEndpointIds) {
        merchant = new Merchant(UUID.randomUUID(), "Apex", "Retail", "REG-1", "+94771234567", "1 Main St", null, null);
        ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
        merchant.approve(UUID.randomUUID());
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
        when(parcels.findByMerchantReference(MERCHANT_ID, REFERENCE)).thenReturn(Optional.of(new ParcelSummary(
                PARCEL_ID, "SD7K3M9Q2XWB", ParcelStatus.CREATED, ParcelSize.SMALL, 800, ParcelSource.MERCHANT, null,
                MERCHANT_ID, REFERENCE, null, NOW)));

        List<MerchantWebhookEndpoint> endpointList = new ArrayList<>();
        for (UUID id : subscribedEndpointIds) {
            MerchantWebhookEndpoint endpoint = mock(MerchantWebhookEndpoint.class);
            when(endpoint.getId()).thenReturn(id);
            endpointList.add(endpoint);
        }
        when(endpoints.findActiveForMerchantAndEvent(eq(MERCHANT_ID), any())).thenReturn(endpointList);

        when(states.existsById(any())).thenAnswer(call -> state != null && state.getParcelId().equals(call.getArgument(0)));
        when(states.insertIfAbsent(any(), any(), any())).thenAnswer(call -> {
            if (state == null) {
                state = new MerchantEventState(call.getArgument(0), call.getArgument(1), call.getArgument(2));
                return 1;
            }
            return 0;
        });
        when(states.lockByParcelId(any())).thenAnswer(call -> Optional.ofNullable(state));
        when(states.tryLockByParcelId(any())).thenAnswer(call -> Optional.ofNullable(state));

        when(processedRepository.findById(any())).thenAnswer(call -> Optional.ofNullable(processed.get(call.getArgument(0))));
        when(processedRepository.findByParcelIdAndSequence(any(), anyInt())).thenAnswer(call -> processed.values().stream()
                .filter(e -> e.getParcelId().equals(call.getArgument(0)) && e.getSequence() == (int) call.getArgument(1))
                .findFirst());
        when(processedRepository.save(any())).thenAnswer(call -> {
            MerchantProcessedEvent event = call.getArgument(0);
            processed.put(event.getEventId(), event);
            return event;
        });

        when(pendingRepository.findById(any())).thenAnswer(call -> Optional.ofNullable(pending.get(call.getArgument(0))));
        when(pendingRepository.findByParcelIdAndSequence(any(), anyInt())).thenAnswer(call -> pending.values().stream()
                .filter(e -> e.getParcelId().equals(call.getArgument(0)) && e.getSequence() == (int) call.getArgument(1))
                .findFirst());
        when(pendingRepository.findByParcelIdOrderBySequenceAsc(any())).thenAnswer(call -> pending.values().stream()
                .filter(e -> e.getParcelId().equals(call.getArgument(0)))
                .sorted(Comparator.comparingInt(MerchantPendingEvent::getSequence))
                .toList());
        when(pendingRepository.save(any())).thenAnswer(call -> {
            MerchantPendingEvent event = call.getArgument(0);
            pending.put(event.getEventId(), event);
            return event;
        });
        doAnswer(call -> pending.remove(((MerchantPendingEvent) call.getArgument(0)).getEventId()))
                .when(pendingRepository).delete(any());

        when(anomalyRepository.findByEventId(any())).thenAnswer(call -> anomalies.stream()
                .filter(a -> a.getEventId().equals(call.getArgument(0))).toList());
        when(anomalyRepository.save(any())).thenAnswer(call -> {
            anomalies.add(call.getArgument(0));
            return call.getArgument(0);
        });

        when(deliveries.insertPending(any(), any(), any(), any(), any(), anyInt(), anyString(), anyString(), any()))
                .thenAnswer(call -> {
                    UUID endpointId = call.getArgument(1);
                    UUID eventId = call.getArgument(3);
                    boolean exists = records.stream().anyMatch(r -> r[0].equals(endpointId) && r[1].equals(eventId));
                    if (exists) {
                        return 0;
                    }
                    records.add(new Object[] {endpointId, eventId, call.getArgument(6), call.getArgument(7)});
                    return 1;
                });
    }

    MerchantEventProcessor processor() {
        return new MerchantEventProcessor(states, processedRepository, pendingRepository, anomalyRepository,
                deliveries, endpoints, merchants, parcels, json, metrics, properties, clock);
    }

    /** The parcel's event with this sequence and a fresh event ID. */
    static ParcelStatusChanged event(int sequence) {
        return event(UUID.randomUUID(), sequence);
    }

    static ParcelStatusChanged event(UUID eventId, int sequence) {
        return ParcelStatusChanged.of(eventId, NOW.minusSeconds(60L * (10 - sequence)),
                new ParcelStatusChangedData(PARCEL_ID, "SD7K3M9Q2XWB", sequence == 1 ? null : PATH.get(sequence - 2),
                        PATH.get(sequence - 1), sequence, ParcelSource.MERCHANT, null, MERCHANT_ID, REFERENCE, null));
    }

    double counter(String name) {
        return meters.find(name).counters().stream().mapToDouble(c -> c.count()).sum();
    }
}
