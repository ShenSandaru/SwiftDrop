package com.swiftdrop.pricing.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.pricing.domain.District;
import com.swiftdrop.pricing.domain.DistrictPostalZone;
import com.swiftdrop.pricing.domain.DistrictRepository;
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
class DistrictApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    private ParcelRepository parcels;
    @MockitoBean
    private ParcelStatusHistoryRepository statusHistory;
    @MockitoBean
    private DeliveryProofRepository proofs;
    @MockitoBean
    private OutboxEventRepository outbox;
    @MockitoBean
    private ZoneRepository zones;
    @MockitoBean
    private PricingRuleRepository rules;
    @MockitoBean
    private DistrictRepository districts;
    @MockitoBean
    private com.swiftdrop.pricing.domain.PricingConfigRepository pricingConfigs;

    @Test
    void listDistricts_returnsAllCatalogDistricts() throws Exception {
        District colombo = new District("CMB", "Colombo", "Western", "00100", "Colombo Branch");
        colombo.getPostalZones(); // access
        DistrictPostalZone maradana = new DistrictPostalZone(colombo, "CMB-MRD", "Colombo 10 - Maradana", "01000");
        org.springframework.test.util.ReflectionTestUtils.setField(colombo, "postalZones", List.of(maradana));

        when(districts.findAllWithPostalZones()).thenReturn(List.of(colombo));

        mvc.perform(get("/api/v1/districts")
                        .with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CMB"))
                .andExpect(jsonPath("$[0].name").value("Colombo"))
                .andExpect(jsonPath("$[0].province").value("Western"))
                .andExpect(jsonPath("$[0].zones[0].code").value("CMB-MRD"))
                .andExpect(jsonPath("$[0].zones[0].name").value("Colombo 10 - Maradana"));
    }

    @Test
    void getDistrict_byCode_returnsDistrict() throws Exception {
        District kandy = new District("KDY", "Kandy", "Central", "20000", "Kandy Branch");
        when(districts.findByCodeIgnoreCase("KDY")).thenReturn(Optional.of(kandy));

        mvc.perform(get("/api/v1/districts/KDY")
                        .with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("KDY"))
                .andExpect(jsonPath("$.name").value("Kandy"));
    }

    @Test
    void getDistrict_notFound_returns404() throws Exception {
        when(districts.findByCodeIgnoreCase("XYZ")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/districts/XYZ")
                        .with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isNotFound());
    }
}
