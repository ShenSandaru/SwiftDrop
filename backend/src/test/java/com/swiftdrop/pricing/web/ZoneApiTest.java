package com.swiftdrop.pricing.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** The zone list through HTTP; the repository is mocked. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@MockitoBean(types = {
        com.swiftdrop.pricing.domain.DistrictRepository.class,
        com.swiftdrop.pricing.domain.PricingConfigRepository.class,
        com.swiftdrop.identity.domain.UserAddressRepository.class,
        com.swiftdrop.delivery.domain.ParcelAssignmentRepository.class,
        com.swiftdrop.delivery.domain.HubRepository.class,
        com.swiftdrop.merchant.domain.MerchantRepository.class,
        com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository.class,
        com.swiftdrop.merchant.domain.MerchantApiKeyRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventStateRepository.class,
        com.swiftdrop.merchant.domain.MerchantProcessedEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantPendingEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventAnomalyRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository.class,
        com.swiftdrop.notification.domain.NotificationRecordRepository.class
})
class ZoneApiTest {

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

    @Test
    void loggedInUsersGetActiveZonesSortedByName() throws Exception {
        when(zones.findByActiveTrue()).thenReturn(List.of(zone("KDY", "Kandy"), zone("CMB", "Colombo")));

        mvc.perform(get("/api/v1/zones").with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Colombo"))
                .andExpect(jsonPath("$[0].code").value("CMB"))
                .andExpect(jsonPath("$[1].name").value("Kandy"))
                .andExpect(jsonPath("$[0].length()").value(3));
    }

    @Test
    void anonymousCallerIsRejected() throws Exception {
        mvc.perform(get("/api/v1/zones")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanListAllZonesIncludingInactive() throws Exception {
        Zone activeZone = zone("CMB", "Colombo");
        Zone inactiveZone = zone("JFN", "Jaffna");
        inactiveZone.setActive(false);
        when(zones.findAllByOrderByNameAsc()).thenReturn(List.of(activeZone, inactiveZone));

        mvc.perform(get("/api/v1/zones?all=true").with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CMB"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].code").value("JFN"))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void nonAdminCannotListAllZones() throws Exception {
        mvc.perform(get("/api/v1/zones?all=true").with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateZone() throws Exception {
        when(zones.existsByCodeIgnoreCase("GAL")).thenReturn(false);
        when(zones.existsByNameIgnoreCase("Galle")).thenReturn(false);
        when(zones.saveAndFlush(any(Zone.class))).thenAnswer(inv -> {
            Zone z = inv.getArgument(0);
            ReflectionTestUtils.setField(z, "id", UUID.randomUUID());
            return z;
        });

        String payload = """
                {
                    "code": "GAL",
                    "name": "Galle"
                }
                """;

        mvc.perform(post("/api/v1/zones")
                        .with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("GAL"))
                .andExpect(jsonPath("$.name").value("Galle"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void duplicateCodeReturns409() throws Exception {
        when(zones.existsByCodeIgnoreCase("CMB")).thenReturn(true);

        String payload = """
                {
                    "code": "CMB",
                    "name": "Another Colombo"
                }
                """;

        mvc.perform(post("/api/v1/zones")
                        .with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A zone with code 'CMB' already exists"));
    }

    @Test
    void duplicateNameReturns409() throws Exception {
        when(zones.existsByCodeIgnoreCase("CMB2")).thenReturn(false);
        when(zones.existsByNameIgnoreCase("Colombo")).thenReturn(true);

        String payload = """
                {
                    "code": "CMB2",
                    "name": "Colombo"
                }
                """;

        mvc.perform(post("/api/v1/zones")
                        .with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A zone with name 'Colombo' already exists"));
    }

    @Test
    void adminCanUpdateZone() throws Exception {
        UUID id = UUID.randomUUID();
        Zone existing = zone("CMB", "Colombo");
        ReflectionTestUtils.setField(existing, "id", id);
        ReflectionTestUtils.setField(existing, "version", 0L);

        when(zones.findById(id)).thenReturn(Optional.of(existing));
        when(zones.saveAndFlush(any(Zone.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {
                    "code": "CMB",
                    "name": "Greater Colombo",
                    "active": false,
                    "version": 0
                }
                """;

        mvc.perform(put("/api/v1/zones/" + id)
                        .with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Greater Colombo"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void versionConflictReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        Zone existing = zone("CMB", "Colombo");
        ReflectionTestUtils.setField(existing, "id", id);
        ReflectionTestUtils.setField(existing, "version", 2L);

        when(zones.findById(id)).thenReturn(Optional.of(existing));

        String payload = """
                {
                    "code": "CMB",
                    "name": "Greater Colombo",
                    "active": true,
                    "version": 1
                }
                """;

        mvc.perform(put("/api/v1/zones/" + id)
                        .with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Zone has been modified by another transaction. Please reload."));
    }

    @Test
    void nonAdminCannotCreateOrUpdate() throws Exception {
        String payload = """
                {
                    "code": "GAL",
                    "name": "Galle"
                }
                """;

        mvc.perform(post("/api/v1/zones")
                        .with(TestPrincipal.customer().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/zones/" + UUID.randomUUID())
                        .with(TestPrincipal.courier().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteZone() throws Exception {
        UUID id = UUID.randomUUID();
        Zone existing = zone("CMB", "Colombo");
        ReflectionTestUtils.setField(existing, "id", id);
        when(zones.findById(id)).thenReturn(Optional.of(existing));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/zones/" + id)
                        .with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isNoContent());
    }

    @Test
    void nonAdminCannotDeleteZone() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/zones/" + UUID.randomUUID())
                        .with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isForbidden());
    }

    private static Zone zone(String code, String name) {
        Zone zone = new Zone(code, name);
        ReflectionTestUtils.setField(zone, "id", UUID.randomUUID());
        return zone;
    }
}
