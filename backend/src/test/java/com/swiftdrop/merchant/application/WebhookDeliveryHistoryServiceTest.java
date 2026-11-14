package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.merchant.api.WebhookDeliveryView;
import com.swiftdrop.merchant.domain.MerchantWebhookAttempt;
import com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookDelivery;
import com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookRedelivery;
import com.swiftdrop.merchant.domain.MerchantWebhookRedelivery.RequestedBy;
import com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookAttemptOutcome;
import com.swiftdrop.merchant.domain.WebhookDeliveryStatus;

class WebhookDeliveryHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID ENDPOINT_ID = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private final MerchantWebhookDeliveryRepository deliveries = mock(MerchantWebhookDeliveryRepository.class);
    private final MerchantWebhookAttemptRepository attempts = mock(MerchantWebhookAttemptRepository.class);
    private final MerchantWebhookRedeliveryRepository redeliveries = mock(MerchantWebhookRedeliveryRepository.class);
    private final MerchantWebhookEndpointRepository endpoints = mock(MerchantWebhookEndpointRepository.class);
    private final WebhookDeliveryHistoryService service = new WebhookDeliveryHistoryService(deliveries, attempts,
            redeliveries, endpoints);

    private MerchantWebhookDelivery delivery;

    @BeforeEach
    void setUp() {
        delivery = BeanUtils.instantiateClass(MerchantWebhookDelivery.class);
        ReflectionTestUtils.setField(delivery, "id", DELIVERY_ID);
        ReflectionTestUtils.setField(delivery, "merchantId", MERCHANT_ID);
        ReflectionTestUtils.setField(delivery, "endpointId", ENDPOINT_ID);
        ReflectionTestUtils.setField(delivery, "eventId", UUID.randomUUID());
        ReflectionTestUtils.setField(delivery, "parcelId", UUID.randomUUID());
        ReflectionTestUtils.setField(delivery, "sequence", 2);
        ReflectionTestUtils.setField(delivery, "eventType", ParcelStatus.PICKED_UP);
        ReflectionTestUtils.setField(delivery, "payload", "{\"secretish\":\"never shown\"}");
        ReflectionTestUtils.setField(delivery, "status", WebhookDeliveryStatus.SUCCEEDED);
        ReflectionTestUtils.setField(delivery, "attempts", 4);
        ReflectionTestUtils.setField(delivery, "manualRedeliveries", 1);
        ReflectionTestUtils.setField(delivery, "lastHttpStatus", 200);
        ReflectionTestUtils.setField(delivery, "lastOutcome", WebhookAttemptOutcome.SUCCEEDED);
        ReflectionTestUtils.setField(delivery, "nextAttemptAt", NOW);
        ReflectionTestUtils.setField(delivery, "createdAt", NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(delivery, "completedAt", NOW);

        MerchantWebhookEndpoint endpoint = mock(MerchantWebhookEndpoint.class);
        when(endpoint.getId()).thenReturn(ENDPOINT_ID);
        when(endpoint.getUrl()).thenReturn("https://hooks.example.com/swiftdrop");
        when(endpoints.findAllById(any())).thenReturn(List.of(endpoint));
    }

    @Test
    @DisplayName("Merchant history is scoped to the merchant and mapped to the view; nextRetryAt only while pending")
    void history() {
        when(deliveries.findHistory(eq(MERCHANT_ID), isNull(), isNull(), any())).thenReturn(new PageImpl<>(
                List.of(delivery), org.springframework.data.domain.PageRequest.of(0, 20), 1));

        var page = service.findDeliveriesByMerchant(MERCHANT_ID, null, new PageRequest(0, 20));

        assertThat(page.totalItems()).isEqualTo(1);
        WebhookDeliveryView view = page.items().get(0);
        assertThat(view.endpointUrl()).isEqualTo("https://hooks.example.com/swiftdrop");
        assertThat(view.eventType()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(view.responseStatus()).isEqualTo(200);
        assertThat(view.deliveryStatus()).isEqualTo("SUCCEEDED");
        assertThat(view.attemptCount()).isEqualTo(4);
        assertThat(view.nextRetryAt()).isNull();
        verify(deliveries).findHistory(eq(MERCHANT_ID), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("The merchant query requires a merchant ID, so it can never list everyone")
    void merchantRequired() {
        assertThatThrownBy(() -> service.findDeliveriesByMerchant(null, null, new PageRequest(0, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Detail groups attempts into series 0 and the manual series with who requested them")
    void detailSeries() {
        when(deliveries.findByIdAndMerchantId(DELIVERY_ID, MERCHANT_ID)).thenReturn(Optional.of(delivery));
        when(attempts.findByDeliveryIdOrderByAttemptNumberAsc(DELIVERY_ID)).thenReturn(List.of(
                new MerchantWebhookAttempt(DELIVERY_ID, 1, 0, NOW, WebhookAttemptOutcome.HTTP_ERROR, 500, 10, null),
                new MerchantWebhookAttempt(DELIVERY_ID, 2, 0, NOW, WebhookAttemptOutcome.HTTP_ERROR, 500, 10, null),
                new MerchantWebhookAttempt(DELIVERY_ID, 3, 0, NOW, WebhookAttemptOutcome.TIMEOUT, null, 10, null),
                new MerchantWebhookAttempt(DELIVERY_ID, 4, 1, NOW, WebhookAttemptOutcome.SUCCEEDED, 200, 10, ADMIN_ID)));
        when(redeliveries.findByDeliveryIdOrderBySeriesAsc(DELIVERY_ID)).thenReturn(List.of(
                new MerchantWebhookRedelivery(DELIVERY_ID, 1, ADMIN_ID, RequestedBy.ADMIN, WebhookDeliveryStatus.FAILED,
                        NOW.minusSeconds(60))));

        WebhookDeliveryDetail detail = service.detail(DELIVERY_ID, MERCHANT_ID);

        assertThat(detail.series()).hasSize(2);
        assertThat(detail.series().get(0).number()).isZero();
        assertThat(detail.series().get(0).requestedBy()).isNull();
        assertThat(detail.series().get(0).attempts()).extracting(WebhookDeliveryDetail.Attempt::number)
                .containsExactly(1, 2, 3);
        assertThat(detail.series().get(1).number()).isEqualTo(1);
        assertThat(detail.series().get(1).requestedBy()).isEqualTo(ADMIN_ID);
        assertThat(detail.series().get(1).requestedByRole()).isEqualTo("ADMIN");
        assertThat(detail.series().get(1).previousStatus()).isEqualTo("FAILED");
        assertThat(detail.series().get(1).attempts()).extracting(WebhookDeliveryDetail.Attempt::outcome)
                .containsExactly("SUCCEEDED");
        assertThat(detail.redeliveriesUsed()).isEqualTo(1);
        assertThat(detail.redeliveriesRemaining()).isEqualTo(2);
        assertThat(detail.redeliverable()).isFalse();
    }

    @Test
    @DisplayName("Another merchant's record is not found; an admin sees any record")
    void scoping() {
        when(deliveries.findByIdAndMerchantId(DELIVERY_ID, MERCHANT_ID)).thenReturn(Optional.of(delivery));
        when(deliveries.findById(DELIVERY_ID)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> service.detail(DELIVERY_ID, UUID.randomUUID()))
                .isInstanceOf(WebhookDeliveryNotFoundException.class);
        assertThat(service.detail(DELIVERY_ID, null).merchantId()).isEqualTo(MERCHANT_ID);
    }
}
