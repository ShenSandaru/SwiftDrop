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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
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
class AdminUserManagementApiTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

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

    private User adminUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminUser = withId(new User("admin@swiftdrop.com", passwordEncoder.encode("admin-pass"), "Admin Dave", Set.of(Role.DISPATCHER_ADMIN)), ADMIN_ID);
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
    void createStaffDispatcherAdminReturns201Created() throws Exception {
        when(users.findByEmail("newadmin@swiftdrop.com")).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/staff-users")
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Admin Sarah",
                                  "email": "newadmin@swiftdrop.com",
                                  "password": "secure-pass-123",
                                  "role": "DISPATCHER_ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newadmin@swiftdrop.com"))
                .andExpect(jsonPath("$.roles", contains("DISPATCHER_ADMIN")));
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

    private static User withId(User user, UUID id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
