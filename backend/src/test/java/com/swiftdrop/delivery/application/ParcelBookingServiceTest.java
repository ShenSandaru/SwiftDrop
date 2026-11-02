package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.delivery.domain.TrackingNumberGenerator;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;

/** Booking with the real recorder; pricing, repositories and the generator are mocked. */
class ParcelBookingServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID COLOMBO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID KANDY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RULE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");

    private ParcelRepository parcels;
    private ParcelStatusHistoryRepository history;
    private OutboxEventRepository outbox;
    private FeeCalculator feeCalculator;
    private TrackingNumberGenerator trackingNumbers;
    private ParcelBookingService service;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelRepository.class);
        history = mock(ParcelStatusHistoryRepository.class);
        outbox = mock(OutboxEventRepository.class);
        feeCalculator = mock(FeeCalculator.class);
        trackingNumbers = mock(TrackingNumberGenerator.class);
        when(trackingNumbers.next()).thenReturn("SD7K3M9Q2XWB");
        when(parcels.saveAndFlush(any())).thenAnswer(call -> {
            Parcel parcel = call.getArgument(0);
            ReflectionTestUtils.setField(parcel, "id", UUID.randomUUID());
            return parcel;
        });
        when(history.save(any())).thenAnswer(call -> call.getArgument(0));
        when(outbox.save(any())).thenAnswer(call -> call.getArgument(0));
        when(feeCalculator.calculateFee(COLOMBO, KANDY, ParcelSize.MEDIUM, 2000)).thenReturn(new FeeQuote(
                new BigDecimal("850.00"), "LKR", new BigDecimal("550.00"), new BigDecimal("200.00"),
                new BigDecimal("100.00"), RULE_ID));
        ParcelEventRecorder recorder = new ParcelEventRecorder(history, outbox, new ParcelEventSerializer(),
                mock(ApplicationEventPublisher.class));
        service = new ParcelBookingService(parcels, feeCalculator, trackingNumbers, recorder,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void customerBookingTakesFeeFromPricingAndRecordsCreation() {
        Parcel parcel = service.bookForCustomer(CUSTOMER_ID, booking());

        assertThat(parcel.getSource()).isEqualTo(ParcelSource.CUSTOMER);
        assertThat(parcel.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(parcel.getTrackingNumber()).isEqualTo("SD7K3M9Q2XWB");
        assertThat(parcel.getFeeAmount()).isEqualByComparingTo("850.00");
        assertThat(parcel.getPricingRuleId()).isEqualTo(RULE_ID);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(parcel.getCreatedAt()).isEqualTo(NOW);

        ArgumentCaptor<ParcelStatusHistoryEntry> entry = ArgumentCaptor.forClass(ParcelStatusHistoryEntry.class);
        InOrder order = inOrder(parcels, history, outbox);
        order.verify(parcels).saveAndFlush(parcel);
        order.verify(history).save(entry.capture());
        order.verify(outbox).save(any(OutboxEvent.class));
        assertThat(entry.getValue().getSequence()).isEqualTo(1);
        assertThat(entry.getValue().getFromStatus()).isNull();
        assertThat(entry.getValue().getActorRole()).isEqualTo(ActorRole.CUSTOMER);
        assertThat(entry.getValue().getActorUserId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void merchantBookingHasMerchantOwnerAndNoActorUser() {
        Parcel parcel = service.bookForMerchant(new NewMerchantParcel(MERCHANT_ID, "ORD-1", contact(COLOMBO),
                contact(KANDY), ParcelSize.MEDIUM, 2000, null));

        assertThat(parcel.getSource()).isEqualTo(ParcelSource.MERCHANT);
        assertThat(parcel.getMerchantId()).isEqualTo(MERCHANT_ID);
        ArgumentCaptor<ParcelStatusHistoryEntry> entry = ArgumentCaptor.forClass(ParcelStatusHistoryEntry.class);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().getActorRole()).isEqualTo(ActorRole.MERCHANT);
        assertThat(entry.getValue().getActorUserId()).isNull();
    }

    @Test
    void takenTrackingNumbersAreSkipped() {
        when(trackingNumbers.next()).thenReturn("SD0000000001", "SD0000000002", "SD0000000003");
        when(parcels.existsByTrackingNumber("SD0000000001")).thenReturn(true);
        when(parcels.existsByTrackingNumber("SD0000000002")).thenReturn(true);

        assertThat(service.bookForCustomer(CUSTOMER_ID, booking()).getTrackingNumber()).isEqualTo("SD0000000003");
    }

    @Test
    void bookingFailsAfterFiveTakenTrackingNumbers() {
        when(parcels.existsByTrackingNumber(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.bookForCustomer(CUSTOMER_ID, booking()))
                .isInstanceOf(IllegalStateException.class);
        verify(trackingNumbers, times(ParcelBookingService.TRACKING_NUMBER_ATTEMPTS)).next();
        verify(parcels, never()).saveAndFlush(any());
    }

    @Test
    void invalidBookingNeverReachesPricingOrTheDatabase() {
        DeliveryContact badPhone = new DeliveryContact("Nimal", "0771234567", "12 Temple Rd", null, "Colombo",
                COLOMBO, null);

        assertThatThrownBy(() -> service.bookForCustomer(CUSTOMER_ID,
                new Booking(badPhone, contact(KANDY), ParcelSize.MEDIUM, 2000, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.bookForCustomer(CUSTOMER_ID,
                new Booking(contact(COLOMBO), null, ParcelSize.MEDIUM, 2000, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.bookForCustomer(CUSTOMER_ID,
                new Booking(contact(COLOMBO), contact(KANDY), ParcelSize.MEDIUM, 30_001, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.bookForCustomer(CUSTOMER_ID,
                new Booking(contact(COLOMBO), contact(KANDY), null, 2000, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(feeCalculator);
        verify(parcels, never()).saveAndFlush(any());
    }

    @Test
    void pricingFailurePropagatesAndNothingIsSaved() {
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenThrow(new IllegalStateException("no rule"));

        assertThatThrownBy(() -> service.bookForCustomer(CUSTOMER_ID, booking())).isInstanceOf(IllegalStateException.class);
        verify(parcels, never()).saveAndFlush(any());
        verifyNoInteractions(history, outbox);
    }

    private static Booking booking() {
        return new Booking(contact(COLOMBO), contact(KANDY), ParcelSize.MEDIUM, 2000, "Books");
    }

    private static DeliveryContact contact(UUID zoneId) {
        return new DeliveryContact("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo", zoneId, null);
    }
}
