package com.swiftdrop.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

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
        when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
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
    void authenticatesWithCorrectPasswordInAnyEmailCase() {
        User alice = existing(encoder.encode("correct horse"));
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        assertThat(service.authenticate(" ALICE@example.com", "correct horse")).isSameAs(alice);
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

    private static User existing(String passwordHash) {
        return new User("alice@example.com", passwordHash, "Alice", Set.of(Role.CUSTOMER));
    }
}
