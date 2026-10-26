package com.swiftdrop.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;

class AccountServiceTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private UserRepository users;
    private AccountService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        when(users.findByEmailIgnoringCase(any())).thenCallRealMethod();
        when(users.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        service = new AccountService(users, encoder);
    }

    @Test
    void registersCustomerWithHashedPasswordAndNormalizedEmail() {
        User user = service.registerCustomer(" Alice ", "Alice@Example.COM", "correct horse");

        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getFullName()).isEqualTo("Alice");
        assertThat(user.getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(user.getPasswordHash()).doesNotContain("correct horse").startsWith("{bcrypt}");
        assertThat(encoder.matches("correct horse", user.getPasswordHash())).isTrue();
        verify(users).findByEmail("alice@example.com");
    }

    @Test
    void bootstrapAdminCreatesDispatcherAdminUser() {
        User user = service.bootstrapAdmin(" Admin@Example.COM ", "secret-admin-pass");

        assertThat(user.getEmail()).isEqualTo("admin@example.com");
        assertThat(user.getFullName()).isEqualTo("System Administrator");
        assertThat(user.getRoles()).containsExactly(Role.DISPATCHER_ADMIN);
        assertThat(user.getPasswordHash()).doesNotContain("secret-admin-pass").startsWith("{bcrypt}");
        assertThat(encoder.matches("secret-admin-pass", user.getPasswordHash())).isTrue();
        verify(users).findByEmail("admin@example.com");
    }

    @Test
    void registerCourierCreatesCourierUser() {
        User user = service.registerCourier(" Bob Courier ", "Bob@Example.COM", "secure courier pass");

        assertThat(user.getEmail()).isEqualTo("bob@example.com");
        assertThat(user.getFullName()).isEqualTo("Bob Courier");
        assertThat(user.getRoles()).containsExactly(Role.COURIER);
        assertThat(user.getPasswordHash()).doesNotContain("secure courier pass").startsWith("{bcrypt}");
        assertThat(encoder.matches("secure courier pass", user.getPasswordHash())).isTrue();
        verify(users).findByEmail("bob@example.com");
    }

    @Test
    void rejectsAlreadyRegisteredEmailInAnyCase() {
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(existing("x")));

        assertThatThrownBy(() -> service.registerCustomer("Alice", "ALICE@example.com", "correct horse"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void uniqueConstraintIsTheFinalAuthorityForDuplicates() {
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThatThrownBy(() -> service.registerCustomer("Alice", "alice@example.com", "correct horse"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void rejectsPasswordLongerThanBcryptAllows() {
        String tooLong = "é".repeat(37); // 74 bytes in UTF-8, though only 37 characters

        assertThatThrownBy(() -> service.registerCustomer("Alice", "alice@example.com", tooLong))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void createsMerchantUserWithMerchantRoleAndReturnsId() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        UUID userId = service.createMerchantUser(" Bob Merchant ", "Bob@Merchant.COM", "secret123");

        verify(users).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("bob@merchant.com");
        assertThat(saved.getFullName()).isEqualTo("Bob Merchant");
        assertThat(saved.getRoles()).containsExactly(Role.MERCHANT);
        assertThat(saved.getPasswordHash()).doesNotContain("secret123").startsWith("{bcrypt}");
        assertThat(encoder.matches("secret123", saved.getPasswordHash())).isTrue();
        assertThat(userId).isNotNull().isEqualTo(saved.getId());
    }

    @Test
    void createMerchantUserRejectsTooLongPassword() {
        String tooLong = "é".repeat(37);

        assertThatThrownBy(() -> service.createMerchantUser("Bob", "bob@merchant.com", tooLong))
                .isInstanceOf(InvalidPasswordException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void createMerchantUserRejectsDuplicateEmail() {
        when(users.findByEmail("bob@merchant.com")).thenReturn(Optional.of(existing("x")));

        assertThatThrownBy(() -> service.createMerchantUser("Bob", "BOB@merchant.com", "secret123"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void createMerchantUserHandlesUniqueConstraintViolation() {
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThatThrownBy(() -> service.createMerchantUser("Bob", "bob@merchant.com", "secret123"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void findsExistingUserAsUserSummaryWithoutPasswordHash() {
        UUID id = UUID.randomUUID();
        User user = withId(new User("carol@example.com", encoder.encode("password"), "Carol",
                Set.of(Role.COURIER, Role.CUSTOMER)));
        ReflectionTestUtils.setField(user, "id", id);
        when(users.findById(id)).thenReturn(Optional.of(user));

        Optional<UserSummary> summaryOpt = service.findUser(id);

        assertThat(summaryOpt).isPresent();
        UserSummary summary = summaryOpt.get();
        assertThat(summary.id()).isEqualTo(id);
        assertThat(summary.email()).isEqualTo("carol@example.com");
        assertThat(summary.fullName()).isEqualTo("Carol");
        assertThat(summary.roles()).containsExactlyInAnyOrder("COURIER", "CUSTOMER");
        assertThat(summary.enabled()).isTrue();
        assertThat(summary.hasRole("COURIER")).isTrue();
        assertThat(summary.hasRole("MERCHANT")).isFalse();
    }

    @Test
    void findUserReturnsEmptyWhenUserNotFound() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());

        assertThat(service.findUser(id)).isEmpty();
    }

    @Test
    void userSummaryEnforcesNullChecksAndImmutableRoles() {
        UUID id = UUID.randomUUID();
        UserSummary summary = new UserSummary(id, "Dan", "dan@example.com", Set.of("CUSTOMER"), true);

        assertThat(summary.id()).isEqualTo(id);
        assertThat(summary.fullName()).isEqualTo("Dan");
        assertThat(summary.email()).isEqualTo("dan@example.com");
        assertThat(summary.roles()).containsExactly("CUSTOMER");
        assertThat(summary.enabled()).isTrue();

        assertThatThrownBy(() -> summary.roles().add("OTHER"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> new UserSummary(null, "Dan", "dan@example.com", Set.of(), true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserSummary(id, null, "dan@example.com", Set.of(), true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserSummary(id, "Dan", null, Set.of(), true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void authenticatesWithCorrectPasswordInAnyEmailCase() {
        User alice = existing(encoder.encode("correct horse"));
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        assertThat(service.authenticate(" ALICE@example.com", "correct horse")).isSameAs(alice);
    }

    @Test
    void findAllUsersReturnsAllUsersFromRepository() {
        User alice = existing("h1");
        User bob = withId(new User("bob@example.com", "h2", "Bob", Set.of(Role.COURIER)));
        when(users.findAll()).thenReturn(List.of(alice, bob));

        List<User> result = service.findAllUsers();

        assertThat(result).containsExactly(alice, bob);
        verify(users).findAll();
    }

    @Test
    void wrongPasswordUnknownEmailAndPasswordlessAccountFailTheSameWay() {
        when(users.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(existing(encoder.encode("correct horse"))));
        when(users.findByEmail("google@example.com")).thenReturn(Optional.of(existing(null)));

        assertThatThrownBy(() -> service.authenticate("alice@example.com", "wrong password"))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");
        assertThatThrownBy(() -> service.authenticate("nobody@example.com", "correct horse"))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");
        assertThatThrownBy(() -> service.authenticate("google@example.com", "correct horse"))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");
    }

    @Test
    void registerStaffAllowsCourierAndDispatcherAdmin() {
        User courier = service.registerStaff("Courier Bob", "courier@example.com", "password123", Role.COURIER);
        assertThat(courier.getRoles()).containsExactly(Role.COURIER);

        User admin = service.registerStaff("Admin Dave", "admin2@example.com", "password123", Role.DISPATCHER_ADMIN);
        assertThat(admin.getRoles()).containsExactly(Role.DISPATCHER_ADMIN);
    }

    @Test
    void registerStaffRejectsCustomerAndMerchantRoles() {
        assertThatThrownBy(() -> service.registerStaff("Cust", "cust@example.com", "password123", Role.CUSTOMER))
                .isInstanceOf(InvalidRoleException.class)
                .hasMessage("Staff account role must be COURIER or DISPATCHER_ADMIN");

        assertThatThrownBy(() -> service.registerStaff("Merch", "merch@example.com", "password123", Role.MERCHANT))
                .isInstanceOf(InvalidRoleException.class)
                .hasMessage("Staff account role must be COURIER or DISPATCHER_ADMIN");
    }

    @Test
    void updateUserStatusEnablesAndDisablesUser() {
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User targetUser = withId(new User("user@example.com", "hash", "Target User", Set.of(Role.CUSTOMER)));
        ReflectionTestUtils.setField(targetUser, "id", targetId);
        when(users.findById(targetId)).thenReturn(Optional.of(targetUser));

        User disabled = service.updateUserStatus(targetId, false, adminId);
        assertThat(disabled.isEnabled()).isFalse();

        User enabled = service.updateUserStatus(targetId, true, adminId);
        assertThat(enabled.isEnabled()).isTrue();
    }

    @Test
    void updateUserStatusRejectsAdminDisablingThemself() {
        UUID adminId = UUID.randomUUID();
        User adminUser = withId(new User("admin@example.com", "hash", "Admin", Set.of(Role.DISPATCHER_ADMIN)));
        ReflectionTestUtils.setField(adminUser, "id", adminId);

        assertThatThrownBy(() -> service.updateUserStatus(adminId, false, adminId))
                .isInstanceOf(SelfDisablingException.class)
                .hasMessage("An administrator cannot disable their own account");

        // Enabling oneself or keeping enabled is permitted
        when(users.findById(adminId)).thenReturn(Optional.of(adminUser));
        User result = service.updateUserStatus(adminId, true, adminId);
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    void updateUserStatusThrowsNotFoundForNonExistentUser() {
        UUID adminId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        when(users.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUserStatus(unknownId, false, adminId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void findUsersPaginatesAndFiltersByRole() {
        org.springframework.data.domain.Page<User> pageMock = new org.springframework.data.domain.PageImpl<>(List.of(existing("h1")));
        when(users.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(pageMock);
        when(users.findByRole(any(Role.class), any(org.springframework.data.domain.Pageable.class))).thenReturn(pageMock);

        org.springframework.data.domain.Page<User> allPage = service.findUsers(0, 20, null);
        assertThat(allPage.getContent()).hasSize(1);
        verify(users).findAll(any(org.springframework.data.domain.Pageable.class));

        org.springframework.data.domain.Page<User> courierPage = service.findUsers(0, 20, Role.COURIER);
        assertThat(courierPage.getContent()).hasSize(1);
        verify(users).findByRole(any(Role.class), any(org.springframework.data.domain.Pageable.class));
    }

    private static User existing(String passwordHash) {
        return withId(new User("alice@example.com", passwordHash, "Alice", Set.of(Role.CUSTOMER)));
    }

    private static User withId(User user) {
        if (user != null && user.getId() == null) {
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        }
        return user;
    }
}
