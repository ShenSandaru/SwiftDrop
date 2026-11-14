package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.application.MerchantAdminService;
import com.swiftdrop.merchant.application.MerchantApprovalService;
import com.swiftdrop.merchant.application.MerchantRegistrationService;
import com.swiftdrop.merchant.application.MerchantWebhookService;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.domain.WebhookEndpointLimitExceededException;
import com.swiftdrop.merchant.domain.WebhookEndpointNotFoundException;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@MockitoBean(types = {com.swiftdrop.identity.domain.UserAddressRepository.class,
        com.swiftdrop.delivery.domain.ParcelAssignmentRepository.class,
        com.swiftdrop.delivery.domain.HubRepository.class,
        com.swiftdrop.merchant.domain.MerchantApiKeyRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventStateRepository.class,
        com.swiftdrop.merchant.domain.MerchantProcessedEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantPendingEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventAnomalyRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository.class,
        com.swiftdrop.notification.domain.NotificationRecordRepository.class})
class MerchantWebhookEndpointControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MerchantWebhookService webhookService;

    @MockitoBean
    private MerchantRegistrationService registrationService;

    @MockitoBean
    private MerchantApprovalService approvalService;

    @MockitoBean
    private MerchantAdminService adminService;

    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    private ZoneRepository zones;
    @MockitoBean
    private PricingRuleRepository pricingRules;
    @MockitoBean
    private ParcelRepository parcels;
    @MockitoBean
    private ParcelStatusHistoryRepository parcelHistory;
    @MockitoBean
    private DeliveryProofRepository deliveryProofs;
    @MockitoBean
    private OutboxEventRepository outboxEvents;
    @MockitoBean
    private MerchantRepository merchants;
    @MockitoBean
    private MerchantStatusHistoryRepository merchantStatusHistory;
    @MockitoBean
    private MerchantWebhookEndpointRepository merchantWebhookEndpoints;

    private final UUID userId = UUID.randomUUID();
    private final UUID endpointId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private Merchant approvedMerchant;

    @BeforeEach
    void setUp() {
        approvedMerchant = new Merchant(
                userId, "Apex Store", "Retail", "BR-1001",
                "+94771234567", "No 10, Main St", "Apex Retailers Ltd", "https://apex.com");
        approvedMerchant.approve(UUID.randomUUID());
        when(merchants.findByUserId(userId)).thenReturn(Optional.of(approvedMerchant));
    }

    @Test
    @DisplayName("POST /api/v1/merchants/me/webhook-endpoints creates endpoint and returns raw secret")
    void createEndpointSuccess() throws Exception {
        CreateWebhookEndpointResponse response = new CreateWebhookEndpointResponse(
                endpointId,
                merchantId,
                "https://api.apex.com/hook",
                "Order status webhook",
                true,
                Set.of(ParcelStatus.CREATED, ParcelStatus.DELIVERED),
                "whsec_testsecret1234567890abcdef",
                "cdef",
                Instant.now(),
                Instant.now()
        );

        when(webhookService.createEndpoint(eq(userId), any(CreateWebhookEndpointRequest.class)))
                .thenReturn(response);

        String body = """
                {
                    "url": "https://api.apex.com/hook",
                    "description": "Order status webhook",
                    "events": ["CREATED", "DELIVERED"]
                }
                """;

        mvc.perform(post("/api/v1/merchants/me/webhook-endpoints")
                        .with(TestPrincipal.merchant(userId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(endpointId.toString()))
                .andExpect(jsonPath("$.url").value("https://api.apex.com/hook"))
                .andExpect(jsonPath("$.secret").value("whsec_testsecret1234567890abcdef"))
                .andExpect(jsonPath("$.secretHint").value("cdef"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/merchants/me/webhook-endpoints fails with 409 when limit exceeded")
    void createEndpointLimitExceeded() throws Exception {
        when(webhookService.createEndpoint(eq(userId), any(CreateWebhookEndpointRequest.class)))
                .thenThrow(new WebhookEndpointLimitExceededException(5));

        String body = """
                {
                    "url": "https://api.apex.com/hook",
                    "events": ["CREATED"]
                }
                """;

        mvc.perform(post("/api/v1/merchants/me/webhook-endpoints")
                        .with(TestPrincipal.merchant(userId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.type").value("https://swiftdrop.com/problems/webhook-limit-exceeded"))
                .andExpect(jsonPath("$.maxLimit").value(5));
    }

    @Test
    @DisplayName("Non-approved merchant is forbidden from creating webhook endpoints (403)")
    void nonApprovedMerchantForbidden() throws Exception {
        Merchant pendingMerchant = new Merchant(
                userId, "Pending Store", "Retail", "BR-1002",
                "+94771234568", "No 11, Main St", "Pending Ltd", null);
        when(merchants.findByUserId(userId)).thenReturn(Optional.of(pendingMerchant));

        String body = """
                {
                    "url": "https://api.pending.com/hook",
                    "events": ["CREATED"]
                }
                """;

        mvc.perform(post("/api/v1/merchants/me/webhook-endpoints")
                        .with(TestPrincipal.merchant(userId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/merchants/me/webhook-endpoints lists endpoints without secrets")
    void listEndpointsSuccess() throws Exception {
        WebhookEndpointResponse item = new WebhookEndpointResponse(
                endpointId,
                merchantId,
                "https://api.apex.com/hook",
                "Order status webhook",
                true,
                Set.of(ParcelStatus.CREATED),
                "cdef",
                Instant.now(),
                Instant.now()
        );

        when(webhookService.listEndpoints(userId)).thenReturn(List.of(item));

        mvc.perform(get("/api/v1/merchants/me/webhook-endpoints")
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(endpointId.toString()))
                .andExpect(jsonPath("$[0].url").value("https://api.apex.com/hook"))
                .andExpect(jsonPath("$[0].secretHint").value("cdef"))
                .andExpect(jsonPath("$[0].secret").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/merchants/me/webhook-endpoints/{id} returns 404 if not found")
    void getEndpointNotFound() throws Exception {
        when(webhookService.getEndpoint(userId, endpointId))
                .thenThrow(new WebhookEndpointNotFoundException(endpointId));

        mvc.perform(get("/api/v1/merchants/me/webhook-endpoints/" + endpointId)
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://swiftdrop.com/problems/webhook-endpoint-not-found"));
    }

    @Test
    @DisplayName("PATCH /api/v1/merchants/me/webhook-endpoints/{id} updates endpoint")
    void updateEndpointSuccess() throws Exception {
        WebhookEndpointResponse response = new WebhookEndpointResponse(
                endpointId,
                merchantId,
                "https://api.apex.com/new-hook",
                "Updated",
                false,
                Set.of(ParcelStatus.DELIVERED),
                "cdef",
                Instant.now(),
                Instant.now()
        );

        when(webhookService.updateEndpoint(eq(userId), eq(endpointId), any(UpdateWebhookEndpointRequest.class)))
                .thenReturn(response);

        String body = """
                {
                    "url": "https://api.apex.com/new-hook",
                    "description": "Updated",
                    "enabled": false,
                    "events": ["DELIVERED"]
                }
                """;

        mvc.perform(patch("/api/v1/merchants/me/webhook-endpoints/" + endpointId)
                        .with(TestPrincipal.merchant(userId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://api.apex.com/new-hook"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/merchants/me/webhook-endpoints/{id} returns 204 No Content")
    void deleteEndpointSuccess() throws Exception {
        doNothing().when(webhookService).deleteEndpoint(userId, endpointId);

        mvc.perform(delete("/api/v1/merchants/me/webhook-endpoints/" + endpointId)
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/merchants/me/webhook-endpoints/{id}/secret-rotations returns new secret once")
    void rotateSecretSuccess() throws Exception {
        RotateSecretResponse response = new RotateSecretResponse(
                endpointId,
                "whsec_new_rotated_secret_987654321",
                "4321"
        );

        when(webhookService.rotateSecret(userId, endpointId)).thenReturn(response);

        mvc.perform(post("/api/v1/merchants/me/webhook-endpoints/" + endpointId + "/secret-rotations")
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpointId").value(endpointId.toString()))
                .andExpect(jsonPath("$.secret").value("whsec_new_rotated_secret_987654321"))
                .andExpect(jsonPath("$.secretHint").value("4321"));
    }
}

