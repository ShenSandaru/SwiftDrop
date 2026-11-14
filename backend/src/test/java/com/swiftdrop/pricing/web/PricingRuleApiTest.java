package com.swiftdrop.pricing.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.Zone;
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
@MockitoBean(types = {
        com.swiftdrop.merchant.domain.MerchantApiKeyRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventStateRepository.class,
        com.swiftdrop.merchant.domain.MerchantProcessedEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantPendingEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventAnomalyRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository.class,
        com.swiftdrop.notification.domain.NotificationRecordRepository.class,
        com.swiftdrop.identity.domain.UserAddressRepository.class,
        com.swiftdrop.delivery.domain.ParcelAssignmentRepository.class,
        com.swiftdrop.delivery.domain.HubRepository.class,
        com.swiftdrop.merchant.domain.MerchantRepository.class,
        com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository.class
})
class PricingRuleApiTest {

    private static final UUID ORIGIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mvc;

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
    private com.swiftdrop.pricing.domain.DistrictRepository districts;
    @MockitoBean
    private com.swiftdrop.pricing.domain.PricingConfigRepository pricingConfigs;

    @Test
    void adminCanListPricingRules() throws Exception {
        Zone origin = new Zone("CMB", "Colombo");
        ReflectionTestUtils.setField(origin, "id", ORIGIN_ID);
        Zone dest = new Zone("KDY", "Kandy");
        ReflectionTestUtils.setField(dest, "id", DEST_ID);

        PricingRule rule = new PricingRule(ORIGIN_ID, DEST_ID, new BigDecimal("400.00"), new BigDecimal("100.00"));
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());

        when(zones.findAll()).thenReturn(List.of(origin, dest));
        when(pricingRules.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(rule));

        mvc.perform(get("/api/v1/pricing-rules").with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originZoneCode").value("CMB"))
                .andExpect(jsonPath("$[0].destinationZoneCode").value("KDY"))
                .andExpect(jsonPath("$[0].baseFee").value(400.00));
    }

    @Test
    void nonAdminCannotListPricingRules() throws Exception {
        mvc.perform(get("/api/v1/pricing-rules").with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreatePricingRule() throws Exception {
        Zone origin = new Zone("CMB", "Colombo");
        ReflectionTestUtils.setField(origin, "id", ORIGIN_ID);
        Zone dest = new Zone("KDY", "Kandy");
        ReflectionTestUtils.setField(dest, "id", DEST_ID);

        when(zones.findById(ORIGIN_ID)).thenReturn(Optional.of(origin));
        when(zones.findById(DEST_ID)).thenReturn(Optional.of(dest));
        when(pricingRules.existsByOriginZoneIdAndDestinationZoneId(ORIGIN_ID, DEST_ID)).thenReturn(false);
        when(pricingRules.saveAndFlush(any(PricingRule.class))).thenAnswer(inv -> {
            PricingRule r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
            return r;
        });

        String payload = """
                {
                    "originZoneId": "%s",
                    "destinationZoneId": "%s",
                    "baseFee": 450.00,
                    "perKgFee": 120.00,
                    "smallSizeSurcharge": 10.00,
                    "mediumSizeSurcharge": 20.00,
                    "largeSizeSurcharge": 30.00,
                    "currency": "LKR",
                    "active": true
                }
                """.formatted(ORIGIN_ID, DEST_ID);

        mvc.perform(post("/api/v1/pricing-rules")
                        .with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originZoneCode").value("CMB"))
                .andExpect(jsonPath("$.destinationZoneCode").value("KDY"))
                .andExpect(jsonPath("$.baseFee").value(450.00));
    }

    @Test
    void duplicateZonePairReturns409() throws Exception {
        Zone origin = new Zone("CMB", "Colombo");
        ReflectionTestUtils.setField(origin, "id", ORIGIN_ID);
        Zone dest = new Zone("KDY", "Kandy");
        ReflectionTestUtils.setField(dest, "id", DEST_ID);

        when(zones.findById(ORIGIN_ID)).thenReturn(Optional.of(origin));
        when(zones.findById(DEST_ID)).thenReturn(Optional.of(dest));
        when(pricingRules.existsByOriginZoneIdAndDestinationZoneId(ORIGIN_ID, DEST_ID)).thenReturn(true);

        String payload = """
                {
                    "originZoneId": "%s",
                    "destinationZoneId": "%s",
                    "baseFee": 400.00,
                    "perKgFee": 100.00
                }
                """.formatted(ORIGIN_ID, DEST_ID);

        mvc.perform(post("/api/v1/pricing-rules")
                        .with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A pricing rule for this origin and destination zone pair already exists"));
    }

    @Test
    void adminCanUpdatePricingRule() throws Exception {
        UUID ruleId = UUID.randomUUID();
        Zone origin = new Zone("CMB", "Colombo");
        ReflectionTestUtils.setField(origin, "id", ORIGIN_ID);
        Zone dest = new Zone("KDY", "Kandy");
        ReflectionTestUtils.setField(dest, "id", DEST_ID);

        PricingRule rule = new PricingRule(ORIGIN_ID, DEST_ID, new BigDecimal("400.00"), new BigDecimal("100.00"));
        ReflectionTestUtils.setField(rule, "id", ruleId);
        ReflectionTestUtils.setField(rule, "version", 0L);

        when(pricingRules.findById(ruleId)).thenReturn(Optional.of(rule));
        when(zones.findById(ORIGIN_ID)).thenReturn(Optional.of(origin));
        when(zones.findById(DEST_ID)).thenReturn(Optional.of(dest));
        when(pricingRules.saveAndFlush(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {
                    "originZoneId": "%s",
                    "destinationZoneId": "%s",
                    "baseFee": 550.00,
                    "perKgFee": 150.00,
                    "active": true,
                    "version": 0
                }
                """.formatted(ORIGIN_ID, DEST_ID);

        mvc.perform(put("/api/v1/pricing-rules/" + ruleId)
                        .with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseFee").value(550.00))
                .andExpect(jsonPath("$.perKgFee").value(150.00));
    }

    @Test
    void versionConflictReturns409() throws Exception {
        UUID ruleId = UUID.randomUUID();
        PricingRule rule = new PricingRule(ORIGIN_ID, DEST_ID, new BigDecimal("400.00"), new BigDecimal("100.00"));
        ReflectionTestUtils.setField(rule, "id", ruleId);
        ReflectionTestUtils.setField(rule, "version", 3L);

        when(pricingRules.findById(ruleId)).thenReturn(Optional.of(rule));

        String payload = """
                {
                    "originZoneId": "%s",
                    "destinationZoneId": "%s",
                    "baseFee": 550.00,
                    "perKgFee": 150.00,
                    "version": 1
                }
                """.formatted(ORIGIN_ID, DEST_ID);

        mvc.perform(put("/api/v1/pricing-rules/" + ruleId)
                        .with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Pricing rule has been modified by another transaction. Please reload."));
    }
}

