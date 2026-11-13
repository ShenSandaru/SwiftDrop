package com.swiftdrop.identity.web;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        com.swiftdrop.delivery.domain.ParcelAssignmentRepository.class,
        com.swiftdrop.delivery.domain.HubRepository.class,
        com.swiftdrop.merchant.domain.MerchantRepository.class,
        com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository.class,
        com.swiftdrop.notification.domain.NotificationRecordRepository.class
})
class UserProfileApiTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository users;

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

    private User customerUser;

    @BeforeEach
    void setUp() {
        customerUser = new User("customer@swiftdrop.com", passwordEncoder.encode("password123"), "Jane Doe",
                Set.of(Role.CUSTOMER));
        ReflectionTestUtils.setField(customerUser, "id", CUSTOMER_ID);

        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customerUser));
        when(users.findByEmail("customer@swiftdrop.com")).thenReturn(Optional.of(customerUser));
        when(users.saveAndFlush(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(users.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getMeReflectsInitialIncompleteProfileStatus() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(TestPrincipal.customer(CUSTOMER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.email").value("customer@swiftdrop.com"))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.profileCompleted").value(false));
    }

    @Test
    void updateProfileWithValidNameAndPhoneNumberSucceedsAndMarksCompleted() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Jane Perera\",\"phoneNumber\":\"+94771234567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Perera"))
                .andExpect(jsonPath("$.phoneNumber").value("+94771234567"))
                .andExpect(jsonPath("$.profileCompleted").value(true));

        assertThat(customerUser.getFullName()).isEqualTo("Jane Perera");
        assertThat(customerUser.getPhoneNumber()).isEqualTo("+94771234567");
        assertThat(customerUser.isProfileCompleted()).isTrue();
    }

    @Test
    void updateProfileWithInvalidPhoneNumberFailsValidation() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Jane Perera\",\"phoneNumber\":\"0771234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("phoneNumber")))
                .andExpect(jsonPath("$.detail").value("Invalid request"));
    }

    @Test
    void updateProfileWithBlankNameFailsValidation() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"   \",\"phoneNumber\":\"+94771234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("fullName")));
    }

    @Test
    void updateProfileWithoutAuthenticationIsRejected() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Jane Perera\",\"phoneNumber\":\"+94771234567\"}"))
                .andExpect(status().isUnauthorized());
    }
}
