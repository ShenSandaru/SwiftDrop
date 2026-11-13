package com.swiftdrop.merchant.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.HubRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.api.WebhookDeliveryView;
import com.swiftdrop.merchant.application.RedeliveryRefusedException;
import com.swiftdrop.merchant.application.WebhookDeliveryDetail;
import com.swiftdrop.merchant.application.WebhookDeliveryHistoryService;
import com.swiftdrop.merchant.application.WebhookDeliveryNotFoundException;
import com.swiftdrop.merchant.application.WebhookRedeliveryService;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookRedelivery.RequestedBy;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.AuthorizationMatrixExecutor;
import com.swiftdrop.test.security.AuthorizationMatrixRow;
import com.swiftdrop.test.security.TestPrincipal;

/** Delivery management endpoints (SD-412): scoping, status codes, response shape and the authorization matrix. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@MockitoBean(types = {UserRepository.class, ExternalIdentityRepository.class, GoogleLoginCodeRepository.class,
        ZoneRepository.class, PricingRuleRepository.class, ParcelRepository.class, ParcelStatusHistoryRepository.class,
        DeliveryProofRepository.class, OutboxEventRepository.class, ParcelAssignmentRepository.class,
        HubRepository.class, MerchantStatusHistoryRepository.class, MerchantWebhookEndpointRepository.class,
        com.swiftdrop.merchant.domain.MerchantApiKeyRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventStateRepository.class,
        com.swiftdrop.merchant.domain.MerchantProcessedEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantPendingEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventAnomalyRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository.class})
class WebhookDeliveryApiTest {

    private static final String MINE = "/api/v1/merchants/me/webhook-deliveries";
    private static final String ALL = "/api/v1/webhook-deliveries";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private MerchantRepository merchants;
    @MockitoBean
    private WebhookDeliveryHistoryService history;
    @MockitoBean
    private WebhookRedeliveryService redelivery;

    @BeforeEach
    void setUp() {
        Merchant merchant = new Merchant(USER_ID, "Apex", "Retail", "REG-1", "+94771234567", "1 Main St", null, null);
        ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
        merchant.approve(UUID.randomUUID());
        when(merchants.findByUserId(USER_ID)).thenReturn(Optional.of(merchant));

        WebhookDeliveryView view = new WebhookDeliveryView(DELIVERY_ID, UUID.randomUUID(), "https://hooks.example.com",
                ParcelStatus.CREATED, 500, "FAILED", 8, null, Instant.now(), Instant.now());
        when(history.history(any(), any(), any(), any())).thenReturn(new PageResult<>(List.of(view), 0, 20, 1, 1));
        when(history.detail(eq(DELIVERY_ID), any())).thenReturn(new WebhookDeliveryDetail(DELIVERY_ID, MERCHANT_ID,
                UUID.randomUUID(), "https://hooks.example.com", UUID.randomUUID(), UUID.randomUUID(), 1,
                ParcelStatus.CREATED, "FAILED", 8, 500, "HTTP_ERROR", null, Instant.now(), Instant.now(), 0, 3, true,
                List.of(new WebhookDeliveryDetail.Series(0, null, null, null, null, List.of(
                        new WebhookDeliveryDetail.Attempt(1, Instant.now(), "HTTP_ERROR", 500, 12))))));
        when(redelivery.redeliver(eq(DELIVERY_ID), any(), any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("A merchant lists only its own deliveries; filters are passed on")
    void merchantList() throws Exception {
        UUID endpointId = UUID.randomUUID();
        mvc.perform(get(MINE).param("endpointId", endpointId.toString()).param("status", "FAILED")
                        .with(TestPrincipal.merchant(USER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(DELIVERY_ID.toString()))
                .andExpect(jsonPath("$.items[0].deliveryStatus").value("FAILED"));

        verify(history).history(eq(MERCHANT_ID), eq(endpointId),
                eq(com.swiftdrop.merchant.domain.WebhookDeliveryStatus.FAILED), any());
    }

    @Test
    @DisplayName("Detail shows series and attempts, never the payload, signature or secret")
    void merchantDetail() throws Exception {
        String body = mvc.perform(get(MINE + "/" + DELIVERY_ID).with(TestPrincipal.merchant(USER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series[0].number").value(0))
                .andExpect(jsonPath("$.series[0].attempts[0].httpStatus").value(500))
                .andExpect(jsonPath("$.redeliveriesRemaining").value(3))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("payload", "signature", "secret", "leaseToken");
        verify(history).detail(DELIVERY_ID, MERCHANT_ID);
    }

    @Test
    @DisplayName("Redelivery answers 202 with the series; the merchant and user come from the token")
    void merchantRedelivery() throws Exception {
        mvc.perform(post(MINE + "/" + DELIVERY_ID + "/redeliveries").with(TestPrincipal.merchant(USER_ID).postProcessor()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.series").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(redelivery).redeliver(DELIVERY_ID, MERCHANT_ID, USER_ID, RequestedBy.MERCHANT);
    }

    @Test
    @DisplayName("Not found answers 404 and a refusal 409")
    void errors() throws Exception {
        UUID other = UUID.randomUUID();
        when(history.detail(eq(other), any())).thenThrow(new WebhookDeliveryNotFoundException());
        when(redelivery.redeliver(eq(other), any(), any(), any())).thenThrow(new WebhookDeliveryNotFoundException());
        mvc.perform(get(MINE + "/" + other).with(TestPrincipal.merchant(USER_ID).postProcessor()))
                .andExpect(status().isNotFound());
        mvc.perform(post(MINE + "/" + other + "/redeliveries").with(TestPrincipal.merchant(USER_ID).postProcessor()))
                .andExpect(status().isNotFound());

        when(redelivery.redeliver(eq(DELIVERY_ID), any(), any(), any()))
                .thenThrow(new RedeliveryRefusedException("The redelivery limit of 3 is used up"));
        mvc.perform(post(MINE + "/" + DELIVERY_ID + "/redeliveries").with(TestPrincipal.merchant(USER_ID).postProcessor()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The redelivery limit of 3 is used up"));
    }

    @Test
    @DisplayName("An admin sees all merchants and redelivers as ADMIN")
    void admin() throws Exception {
        mvc.perform(get(ALL).with(TestPrincipal.admin(ADMIN_ID).postProcessor())).andExpect(status().isOk());
        verify(history).history(isNull(), isNull(), isNull(), any());

        mvc.perform(get(ALL + "/" + DELIVERY_ID).with(TestPrincipal.admin(ADMIN_ID).postProcessor()))
                .andExpect(status().isOk());
        verify(history).detail(DELIVERY_ID, null);

        mvc.perform(post(ALL + "/" + DELIVERY_ID + "/redeliveries").with(TestPrincipal.admin(ADMIN_ID).postProcessor()))
                .andExpect(status().isAccepted());
        verify(redelivery).redeliver(DELIVERY_ID, null, ADMIN_ID, RequestedBy.ADMIN);
    }

    @TestFactory
    @DisplayName("Authorization matrix")
    Stream<DynamicTest> matrix() {
        UUID other = UUID.randomUUID();
        String apiKey = "sdk_abcdefgh1234_" + "S".repeat(43);
        return AuthorizationMatrixExecutor.toDynamicTests(mvc, List.of(
                AuthorizationMatrixRow.row("merchant list: anonymous 401", HttpMethod.GET, MINE,
                        TestPrincipal.anonymous(), HttpStatus.UNAUTHORIZED),
                AuthorizationMatrixRow.row("merchant list: approved merchant 200", HttpMethod.GET, MINE,
                        TestPrincipal.merchant(USER_ID), HttpStatus.OK),
                AuthorizationMatrixRow.row("merchant list: merchant role without profile 403", HttpMethod.GET, MINE,
                        TestPrincipal.merchant(other), HttpStatus.FORBIDDEN),
                AuthorizationMatrixRow.row("merchant list: customer 403", HttpMethod.GET, MINE,
                        TestPrincipal.customer(other), HttpStatus.FORBIDDEN),
                AuthorizationMatrixRow.row("merchant list: courier 403", HttpMethod.GET, MINE,
                        TestPrincipal.courier(other), HttpStatus.FORBIDDEN),
                AuthorizationMatrixRow.row("merchant list: API key 401", HttpMethod.GET, MINE,
                        TestPrincipal.apiKey(apiKey), HttpStatus.UNAUTHORIZED),
                AuthorizationMatrixRow.row("merchant redelivery: anonymous 401", HttpMethod.POST,
                        MINE + "/" + DELIVERY_ID + "/redeliveries", TestPrincipal.anonymous(), HttpStatus.UNAUTHORIZED),
                AuthorizationMatrixRow.row("admin list: admin 200", HttpMethod.GET, ALL,
                        TestPrincipal.admin(ADMIN_ID), HttpStatus.OK),
                AuthorizationMatrixRow.row("admin list: dispatcher 403", HttpMethod.GET, ALL,
                        TestPrincipal.dispatcher(other), HttpStatus.FORBIDDEN),
                AuthorizationMatrixRow.row("admin list: merchant 403", HttpMethod.GET, ALL,
                        TestPrincipal.merchant(USER_ID), HttpStatus.FORBIDDEN),
                AuthorizationMatrixRow.row("admin list: anonymous 401", HttpMethod.GET, ALL,
                        TestPrincipal.anonymous(), HttpStatus.UNAUTHORIZED),
                AuthorizationMatrixRow.row("admin redelivery: merchant 403", HttpMethod.POST,
                        ALL + "/" + DELIVERY_ID + "/redeliveries", TestPrincipal.merchant(USER_ID), HttpStatus.FORBIDDEN),
                AuthorizationMatrixRow.row("admin redelivery: dispatcher 403", HttpMethod.POST,
                        ALL + "/" + DELIVERY_ID + "/redeliveries", TestPrincipal.dispatcher(other),
                        HttpStatus.FORBIDDEN)));
    }
}
