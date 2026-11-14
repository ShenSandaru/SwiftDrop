package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookDelivery;
import com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookRedelivery;
import com.swiftdrop.merchant.domain.MerchantWebhookRedelivery.RequestedBy;
import com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookDeliveryStatus;

/** Redelivery rules of ADR 0004 §7.5 and the series limits of SD-412. */
class WebhookRedeliveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
    private static final UUID DELIVERY_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID ENDPOINT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private final MerchantWebhookDeliveryRepository deliveries = mock(MerchantWebhookDeliveryRepository.class);
    private final MerchantWebhookRedeliveryRepository redeliveries = mock(MerchantWebhookRedeliveryRepository.class);
    private final MerchantWebhookEndpointRepository endpoints = mock(MerchantWebhookEndpointRepository.class);
    private final MerchantRepository merchants = mock(MerchantRepository.class);
    private final WebhookRedeliveryService service = new WebhookRedeliveryService(deliveries, redeliveries, endpoints,
            merchants, Clock.fixed(NOW, ZoneOffset.UTC));

    private MerchantWebhookDelivery delivery;
    private final MerchantWebhookEndpoint endpoint = mock(MerchantWebhookEndpoint.class);
    private Merchant merchant;

    /** A real record in the given state; the entity has no public constructor (created by SQL in production). */
    private static MerchantWebhookDelivery record(WebhookDeliveryStatus status, int attempts, int redeliveries) {
        MerchantWebhookDelivery delivery = org.springframework.beans.BeanUtils.instantiateClass(
                MerchantWebhookDelivery.class);
        ReflectionTestUtils.setField(delivery, "id", DELIVERY_ID);
        ReflectionTestUtils.setField(delivery, "merchantId", MERCHANT_ID);
        ReflectionTestUtils.setField(delivery, "endpointId", ENDPOINT_ID);
        ReflectionTestUtils.setField(delivery, "eventId", UUID.randomUUID());
        ReflectionTestUtils.setField(delivery, "status", status);
        ReflectionTestUtils.setField(delivery, "attempts", attempts);
        ReflectionTestUtils.setField(delivery, "manualRedeliveries", redeliveries);
        ReflectionTestUtils.setField(delivery, "completedAt", NOW.minusSeconds(60));
        ReflectionTestUtils.setField(delivery, "leaseToken", UUID.randomUUID());
        ReflectionTestUtils.setField(delivery, "leaseExpiresAt", NOW.minusSeconds(30));
        return delivery;
    }

    @BeforeEach
    void setUp() {
        delivery = record(WebhookDeliveryStatus.FAILED, 8, 0);
        when(deliveries.lockById(DELIVERY_ID)).thenAnswer(call -> Optional.of(delivery));
        when(endpoint.getMerchantId()).thenReturn(MERCHANT_ID);
        when(endpoint.isEnabled()).thenReturn(true);
        when(endpoints.findById(ENDPOINT_ID)).thenReturn(Optional.of(endpoint));
        merchant = new Merchant(UUID.randomUUID(), "Apex", "Retail", "REG-1", "+94771234567", "1 Main St", null, null);
        ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
        merchant.approve(UUID.randomUUID());
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
    }

    @Test
    @DisplayName("A failed record starts series 1: due now, lease cleared, series counted from here, audited")
    void redeliversFailedRecord() {
        int series = service.redeliver(DELIVERY_ID, MERCHANT_ID, USER_ID, RequestedBy.MERCHANT);

        assertThat(series).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(delivery.getCompletedAt()).isNull();
        assertThat(delivery.getLeaseToken()).isNull();
        assertThat(delivery.getLeaseExpiresAt()).isNull();
        assertThat(delivery.getManualRedeliveries()).isEqualTo(1);
        assertThat(delivery.getSeriesStartAttempts()).isEqualTo(8);
        assertThat(delivery.getSeriesTriggeredBy()).isEqualTo(USER_ID);

        ArgumentCaptor<MerchantWebhookRedelivery> audit = ArgumentCaptor.forClass(MerchantWebhookRedelivery.class);
        verify(redeliveries).save(audit.capture());
        assertThat(audit.getValue().getSeries()).isEqualTo(1);
        assertThat(audit.getValue().getRequestedByUserId()).isEqualTo(USER_ID);
        assertThat(audit.getValue().getRequestedByRole()).isEqualTo(RequestedBy.MERCHANT);
        assertThat(audit.getValue().getPreviousStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(audit.getValue().getRequestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("A parked SECRET_UNAVAILABLE record can be redelivered (recovery after a key fix)")
    void redeliversParkedRecord() {
        delivery = record(WebhookDeliveryStatus.SECRET_UNAVAILABLE, 2, 0);

        assertThat(service.redeliver(DELIVERY_ID, MERCHANT_ID, USER_ID, RequestedBy.MERCHANT)).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
    }

    @Test
    @DisplayName("Three redeliveries are accepted, each a new series; the fourth is refused")
    void threeThenRefused() {
        for (int expected = 1; expected <= 3; expected++) {
            assertThat(service.redeliver(DELIVERY_ID, MERCHANT_ID, USER_ID, RequestedBy.MERCHANT)).isEqualTo(expected);
            // The series failed again
            ReflectionTestUtils.setField(delivery, "status", WebhookDeliveryStatus.FAILED);
        }

        assertThatThrownBy(() -> service.redeliver(DELIVERY_ID, MERCHANT_ID, USER_ID, RequestedBy.MERCHANT))
                .isInstanceOf(RedeliveryRefusedException.class).hasMessageContaining("limit");
        assertThat(delivery.getManualRedeliveries()).isEqualTo(3);
    }

    @Test
    @DisplayName("Another merchant's record, or a missing one, is not found")
    void otherMerchant() {
        assertThatThrownBy(() -> service.redeliver(DELIVERY_ID, UUID.randomUUID(), USER_ID, RequestedBy.MERCHANT))
                .isInstanceOf(WebhookDeliveryNotFoundException.class);
        assertThatThrownBy(() -> service.redeliver(UUID.randomUUID(), MERCHANT_ID, USER_ID, RequestedBy.MERCHANT))
                .isInstanceOf(WebhookDeliveryNotFoundException.class);
        verify(redeliveries, never()).save(any());
    }

    @Test
    @DisplayName("An admin may redeliver any merchant's record")
    void admin() {
        assertThat(service.redeliver(DELIVERY_ID, null, USER_ID, RequestedBy.ADMIN)).isEqualTo(1);
    }

    @Test
    @DisplayName("A running series or a delivered record is refused")
    void wrongStatus() {
        for (WebhookDeliveryStatus status : new WebhookDeliveryStatus[] {WebhookDeliveryStatus.PENDING,
                WebhookDeliveryStatus.IN_PROGRESS, WebhookDeliveryStatus.SUCCEEDED}) {
            delivery = record(status, 1, 0);
            assertThatThrownBy(() -> service.redeliver(DELIVERY_ID, MERCHANT_ID, USER_ID, RequestedBy.MERCHANT))
                    .as(status.name()).isInstanceOf(RedeliveryRefusedException.class);
        }
        verify(redeliveries, never()).save(any());
    }

    @Test
    @DisplayName("A disabled, deleted or foreign endpoint, or a merchant that is not approved, is refused")
    void inactiveEndpointOrMerchant() {
        when(endpoint.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.redeliver(DELIVERY_ID, MERCHANT_ID, USER_ID, RequestedBy.MERCHANT))
                .isInstanceOf(RedeliveryRefusedException.class);

        when(endpoint.isEnabled()).thenReturn(true);
        when(endpoint.isDeleted()).thenReturn(true);
        assertThatThrownBy(() -> service.redeliver(DELIVERY_ID, MERCHANT_ID, USER_ID, RequestedBy.MERCHANT))
                .isInstanceOf(RedeliveryRefusedException.class);

        when(endpoint.isDeleted()).thenReturn(false);
        when(endpoint.getMerchantId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.redeliver(DELIVERY_ID, MERCHANT_ID, USER_ID, RequestedBy.MERCHANT))
                .isInstanceOf(RedeliveryRefusedException.class);

        when(endpoint.getMerchantId()).thenReturn(MERCHANT_ID);
        merchant.suspend(UUID.randomUUID(), "Review");
        assertThatThrownBy(() -> service.redeliver(DELIVERY_ID, MERCHANT_ID, USER_ID, RequestedBy.MERCHANT))
                .isInstanceOf(RedeliveryRefusedException.class).hasMessageContaining("not approved");

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        verify(redeliveries, never()).save(any());
    }
}
