package com.swiftdrop.identity.web;

import java.util.List;
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
import com.swiftdrop.identity.domain.UserAddress;
import com.swiftdrop.identity.domain.UserAddressRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@MockitoBean(types = com.swiftdrop.delivery.domain.ParcelAssignmentRepository.class)
class UserProfileApiTest {

    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID ADDRESS_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private UserAddressRepository addresses;

    @MockitoBean
    private DeliveryProofRepository deliveryProofs;

    @MockitoBean
    private OutboxEventRepository outboxEvents;

    @MockitoBean
    private ParcelRepository parcels;

    @MockitoBean
    private ParcelStatusHistoryRepository parcelStatusHistory;

    @MockitoBean
    private ExternalIdentityRepository identities;

    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;

    @MockitoBean
    private FeeCalculator feeCalculator;

    @MockitoBean
    private PricingRuleRepository pricingRules;

    @MockitoBean
    private ZoneRepository zones;

    private User courierUser;
    private User adminUser;
    private User customerUser;

    @BeforeEach
    void setUp() {
        courierUser = new User("courier@swiftdrop.lk", passwordEncoder.encode("CurrentPass123!"), "Speedy Courier", Set.of(Role.COURIER));
        courierUser.setPhoneNumber("+94771234567");
        ReflectionTestUtils.setField(courierUser, "id", COURIER_ID);

        adminUser = new User("admin@swiftdrop.lk", passwordEncoder.encode("AdminPass123!"), "Chief Dispatcher", Set.of(Role.DISPATCHER_ADMIN));
        ReflectionTestUtils.setField(adminUser, "id", ADMIN_ID);

        customerUser = new User("customer@swiftdrop.lk", passwordEncoder.encode("CustomerPass123!"), "Alice Customer", Set.of(Role.CUSTOMER));
        ReflectionTestUtils.setField(customerUser, "id", CUSTOMER_ID);
    }

    @Test
    void getMyProfileReturnsUserData() throws Exception {
        when(users.findById(COURIER_ID)).thenReturn(Optional.of(courierUser));

        mvc.perform(get("/api/v1/users/me")
                        .with(TestPrincipal.courier(COURIER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(COURIER_ID.toString())))
                .andExpect(jsonPath("$.email", is("courier@swiftdrop.lk")))
                .andExpect(jsonPath("$.fullName", is("Speedy Courier")))
                .andExpect(jsonPath("$.phoneNumber", is("+94771234567")))
                .andExpect(jsonPath("$.roles[0]", is("COURIER")));
    }

    @Test
    void getMyProfileRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfileSuccessfullyUpdatesNameAndPhone() throws Exception {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customerUser));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mvc.perform(patch("/api/v1/users/me")
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Alice Updated",
                                    "phoneNumber": "+94779876543"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Alice Updated")))
                .andExpect(jsonPath("$.phoneNumber", is("+94779876543")));

        assertThat(customerUser.getFullName()).isEqualTo("Alice Updated");
        assertThat(customerUser.getPhoneNumber()).isEqualTo("+94779876543");
        verify(users).saveAndFlush(customerUser);
    }

    @Test
    void updateProfileRejectsBlankName() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordSuccessfullyUpdatesHash() throws Exception {
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mvc.perform(post("/api/v1/users/me/password")
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currentPassword": "AdminPass123!",
                                    "newPassword": "NewAdminPass456!"
                                }
                                """))
                .andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches("NewAdminPass456!", adminUser.getPasswordHash())).isTrue();
        verify(users).saveAndFlush(adminUser);
    }

    @Test
    void createAndListSavedAddresses() throws Exception {
        when(users.existsById(CUSTOMER_ID)).thenReturn(true);

        UserAddress address = new UserAddress(
                CUSTOMER_ID, "Home", "Alice Recipient", "+94771234567", "123 Galle Rd", "Colombo", "00300", true);
        ReflectionTestUtils.setField(address, "id", ADDRESS_ID);

        when(addresses.findByUserIdOrderByIsDefaultDescCreatedAtDesc(CUSTOMER_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(address));
        when(addresses.saveAndFlush(any(UserAddress.class))).thenReturn(address);

        mvc.perform(post("/api/v1/users/me/addresses")
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "label": "Home",
                                    "recipientName": "Alice Recipient",
                                    "phoneNumber": "+94771234567",
                                    "streetAddress": "123 Galle Rd",
                                    "city": "Colombo",
                                    "postalCode": "00300",
                                    "isDefault": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(ADDRESS_ID.toString())))
                .andExpect(jsonPath("$.label", is("Home")))
                .andExpect(jsonPath("$.city", is("Colombo")))
                .andExpect(jsonPath("$.isDefault", is(true)));

        mvc.perform(get("/api/v1/users/me/addresses")
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label", is("Home")));
    }

    @Test
    void deleteSavedAddress() throws Exception {
        UserAddress address = new UserAddress(
                CUSTOMER_ID, "Office", "Alice Office", "+94771234567", "45 Union Place", "Colombo", "00200", false);
        ReflectionTestUtils.setField(address, "id", ADDRESS_ID);

        when(addresses.findByIdAndUserId(ADDRESS_ID, CUSTOMER_ID)).thenReturn(Optional.of(address));

        mvc.perform(delete("/api/v1/users/me/addresses/" + ADDRESS_ID)
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor()))
                .andExpect(status().isNoContent());

        verify(addresses).delete(address);
    }

    @Test
    void nonCustomerCannotAccessAddresses() throws Exception {
        mvc.perform(get("/api/v1/users/me/addresses")
                        .with(TestPrincipal.courier(COURIER_ID).postProcessor()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/users/me/addresses")
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadAndDownloadAvatar() throws Exception {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customerUser));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[] { 10, 20, 30 });

        mvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAvatar", is(true)))
                .andExpect(jsonPath("$.avatarUpdatedAt").isNotEmpty());

        assertThat(customerUser.hasAvatar()).isTrue();

        // Download does not require authentication
        mvc.perform(get("/api/v1/users/" + CUSTOMER_ID + "/avatar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[] { 10, 20, 30 }));
    }

    @Test
    void deleteAvatarClearsPhoto() throws Exception {
        customerUser.updateAvatar(new byte[] { 1, 2, 3 }, "image/jpeg");
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customerUser));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mvc.perform(delete("/api/v1/users/me/avatar")
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAvatar", is(false)));

        assertThat(customerUser.hasAvatar()).isFalse();
    }

    @Test
    void rejectInvalidAvatarContentType() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[] { 1, 2, 3 });

        mvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(pdfFile)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor()))
                .andExpect(status().isBadRequest());
    }
}
