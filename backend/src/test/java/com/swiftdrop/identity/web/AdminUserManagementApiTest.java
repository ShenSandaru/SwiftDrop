package com.swiftdrop.identity.web;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AdminUserManagementApiTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder encoder;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private com.swiftdrop.identity.domain.UserAddressRepository addresses;

    @MockitoBean
    private ExternalIdentityRepository identities;

    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;

    @MockitoBean
    private ParcelRepository parcels;

    @MockitoBean
    private ParcelStatusHistoryRepository parcelHistory;

    @MockitoBean
    private DeliveryProofRepository deliveryProofs;

    @MockitoBean
    private OutboxEventRepository outboxEvents;

    @MockitoBean
    private FeeCalculator feeCalculator;

    @MockitoBean
    private ZoneRepository zones;

    @MockitoBean
    private PricingRuleRepository pricingRules;

    private User adminUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminUser = withId(new User("admin@swiftdrop.com", passwordEncoder.encode("admin-pass"), "Admin Dave", Set.of(Role.ADMIN)), ADMIN_ID);
        targetUser = withId(new User("target@swiftdrop.com", passwordEncoder.encode("target-pass"), "Target Alice", Set.of(Role.CUSTOMER)), TARGET_USER_ID);

        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(users.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
        when(users.findByEmailIgnoringCase(any())).thenCallRealMethod();
        when(users.findByEmail("admin@swiftdrop.com")).thenReturn(Optional.of(adminUser));
        when(users.findByEmail("target@swiftdrop.com")).thenReturn(Optional.of(targetUser));
        when(users.saveAndFlush(any())).thenAnswer(call -> {
            User u = call.getArgument(0);
            if (u.getId() == null) {
                ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            }
            return u;
        });
    }

    // 1. List Users
    @Test
    void listUsersUnpagedReturnsArrayForBackwardsCompatibility() throws Exception {
        when(users.findAll()).thenReturn(List.of(adminUser, targetUser));

        mvc.perform(get("/api/v1/users").with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].email").value("admin@swiftdrop.com"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[1].email").value("target@swiftdrop.com"))
                .andExpect(jsonPath("$[*].password").doesNotExist())
                .andExpect(jsonPath("$[*].passwordHash").doesNotExist());
    }

    @Test
    void listUsersPagedReturnsPageResponseShape() throws Exception {
        when(users.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(adminUser, targetUser), org.springframework.data.domain.PageRequest.of(0, 20), 2));

        mvc.perform(get("/api/v1/users?page=0&size=20").with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].email").value("admin@swiftdrop.com"))
                .andExpect(jsonPath("$.items[0].enabled").value(true));
    }

    @Test
    void listUsersFilteredByRoleReturnsMatchingPage() throws Exception {
        when(users.findByRole(eq(Role.COURIER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/users?role=COURIER").with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void listUsersRejectsInvalidRoleFilter() throws Exception {
        mvc.perform(get("/api/v1/users?role=INVALID_ROLE").with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("role")));
    }

    // 2. Staff Creation
    @Test
    void createStaffCourierReturns201Created() throws Exception {
        when(users.findByEmail("newcourier@swiftdrop.com")).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/staff-users")
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Courier Jane",
                                  "email": "newcourier@swiftdrop.com",
                                  "password": "secure-pass-123",
                                  "role": "COURIER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newcourier@swiftdrop.com"))
                .andExpect(jsonPath("$.fullName").value("Courier Jane"))
                .andExpect(jsonPath("$.roles", contains("COURIER")))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void createStaffDispatcherReturns201Created() throws Exception {
        when(users.findByEmail("newdispatcher@swiftdrop.com")).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/staff-users")
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Dispatcher Sarah",
                                  "email": "newdispatcher@swiftdrop.com",
                                  "password": "secure-pass-123",
                                  "role": "DISPATCHER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newdispatcher@swiftdrop.com"))
                .andExpect(jsonPath("$.roles", contains("DISPATCHER")));
    }

    @Test
    void createStaffDispatcherWithHubIdReturns201AndHubId() throws Exception {
        UUID hubId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(users.findByEmail("colombodisp@swiftdrop.com")).thenReturn(Optional.empty());
        when(users.existsByHubIdAndRole(hubId, Role.DISPATCHER)).thenReturn(false);

        mvc.perform(post("/api/v1/staff-users")
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Colombo Dispatcher",
                                  "email": "colombodisp@swiftdrop.com",
                                  "password": "secure-pass-123",
                                  "role": "DISPATCHER",
                                  "hubId": "%s"
                                }
                                """.formatted(hubId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("colombodisp@swiftdrop.com"))
                .andExpect(jsonPath("$.roles", contains("DISPATCHER")))
                .andExpect(jsonPath("$.hubId").value(hubId.toString()));
    }

    @Test
    void createStaffDispatcherRejectsHubWithExistingDispatcher() throws Exception {
        UUID hubId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(users.findByEmail("seconddisp@swiftdrop.com")).thenReturn(Optional.empty());
        when(users.existsByHubIdAndRole(hubId, Role.DISPATCHER)).thenReturn(true);

        mvc.perform(post("/api/v1/staff-users")
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Second Dispatcher",
                                  "email": "seconddisp@swiftdrop.com",
                                  "password": "secure-pass-123",
                                  "role": "DISPATCHER",
                                  "hubId": "%s"
                                }
                                """.formatted(hubId)))
                .andExpect(status().isConflict());
    }

    @Test
    void createStaffRejectsAdminRole() throws Exception {
        mvc.perform(post("/api/v1/staff-users")
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "New Admin",
                                  "email": "newadmin@swiftdrop.com",
                                  "password": "secure-pass-123",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Staff account role must be COURIER or DISPATCHER"));
    }

    @Test
    void createStaffRejectsLegacyDispatcherAdminRole() throws Exception {
        mvc.perform(post("/api/v1/staff-users")
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Legacy Staff",
                                  "email": "legacy@swiftdrop.com",
                                  "password": "secure-pass-123",
                                  "role": "DISPATCHER_ADMIN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Staff account role must be COURIER or DISPATCHER"));
    }

    @Test
    void createStaffRejectsCustomerAndMerchantRoles() throws Exception {
        mvc.perform(post("/api/v1/staff-users")
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Bad Role",
                                  "email": "bad@swiftdrop.com",
                                  "password": "secure-pass-123",
                                  "role": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("role")));

        mvc.perform(post("/api/v1/staff-users")
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Bad Role",
                                  "email": "bad@swiftdrop.com",
                                  "password": "secure-pass-123",
                                  "role": "MERCHANT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("role")));
    }

    @Test
    void createStaffRejectsDuplicateEmailWithConflict() throws Exception {
        when(users.findByEmail("target@swiftdrop.com")).thenReturn(Optional.of(targetUser));

        mvc.perform(post("/api/v1/staff-users")
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Duplicate User",
                                  "email": "target@swiftdrop.com",
                                  "password": "secure-pass-123",
                                  "role": "COURIER"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    // 3. User Enable / Disable
    @Test
    void adminCanDisableAndEnableUser() throws Exception {
        // Disable target user
        mvc.perform(patch("/api/v1/users/" + TARGET_USER_ID)
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TARGET_USER_ID.toString()))
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(targetUser.isEnabled()).isFalse();

        // Enable target user
        mvc.perform(patch("/api/v1/users/" + TARGET_USER_ID)
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TARGET_USER_ID.toString()))
                .andExpect(jsonPath("$.enabled").value(true));

        assertThat(targetUser.isEnabled()).isTrue();
    }

    @Test
    void adminCannotDisableThemself() throws Exception {
        mvc.perform(patch("/api/v1/users/" + ADMIN_ID)
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An administrator cannot disable their own account"));

        assertThat(adminUser.isEnabled()).isTrue();
    }

    @Test
    void patchUserStatusReturns404ForNonExistentUser() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(users.findById(unknownId)).thenReturn(Optional.empty());

        mvc.perform(patch("/api/v1/users/" + unknownId)
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchUserStatusRejectsMissingEnabledField() throws Exception {
        mvc.perform(patch("/api/v1/users/" + TARGET_USER_ID)
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("enabled")));
    }

    // 4. Disabled Account Enforcement
    @Test
    void disabledUserCannotLogin() throws Exception {
        targetUser.setEnabled(false);

        mvc.perform(post("/api/v1/auth/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"target@swiftdrop.com\", \"password\": \"target-pass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUserTokenRejectedOnUsersMe() throws Exception {
        targetUser.setEnabled(false);

        mvc.perform(get("/api/v1/users/me")
                        .with(TestPrincipal.customer(TARGET_USER_ID).postProcessor()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAdminCannotReenableOwnAccount() throws Exception {
        adminUser.setEnabled(false);
        String token = adminBearerToken(ADMIN_ID);

        mvc.perform(patch("/api/v1/users/" + ADMIN_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isUnauthorized());

        assertThat(adminUser.isEnabled()).isFalse();
    }

    @Test
    void disabledAdminCannotDisableAnotherUser() throws Exception {
        adminUser.setEnabled(false);
        String token = adminBearerToken(ADMIN_ID);

        mvc.perform(patch("/api/v1/users/" + TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isUnauthorized());

        assertThat(targetUser.isEnabled()).isTrue();
    }

    @Test
    void disabledAdminCannotCreateStaffAccounts() throws Exception {
        adminUser.setEnabled(false);
        String token = adminBearerToken(ADMIN_ID);

        mvc.perform(post("/api/v1/users/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "New Staff", "email": "newstaff@swiftdrop.com", "password": "staff-password-123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeAdminCanPerformLegitimateOperations() throws Exception {
        adminUser.setEnabled(true);
        String token = adminBearerToken(ADMIN_ID);

        mvc.perform(patch("/api/v1/users/" + TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(targetUser.isEnabled()).isFalse();
    }

    private String adminBearerToken(UUID adminId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("swiftdrop")
                .subject(adminId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .claim("roles", List.of("ADMIN"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static User withId(User user, UUID id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
