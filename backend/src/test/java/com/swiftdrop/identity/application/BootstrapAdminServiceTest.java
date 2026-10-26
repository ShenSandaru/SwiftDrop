package com.swiftdrop.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.UserRepository;

class BootstrapAdminServiceTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private UserRepository users;
    private AccountService accounts;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        accounts = mock(AccountService.class);
    }

    @Test
    void bootstrapsAdminWhenConfiguredAndNoAdminExists() {
        BootstrapAdminProperties properties = new BootstrapAdminProperties("admin@swiftdrop.com", "secure-pass-123");
        when(users.existsByRoles(Role.DISPATCHER_ADMIN)).thenReturn(false);

        BootstrapAdminService service = new BootstrapAdminService(users, accounts, properties);
        service.run(new DefaultApplicationArguments());

        verify(users).existsByRoles(Role.DISPATCHER_ADMIN);
        verify(accounts).bootstrapAdmin("admin@swiftdrop.com", "secure-pass-123");
    }

    @Test
    void skipsBootstrapWhenAdminAlreadyExists() {
        BootstrapAdminProperties properties = new BootstrapAdminProperties("admin@swiftdrop.com", "secure-pass-123");
        when(users.existsByRoles(Role.DISPATCHER_ADMIN)).thenReturn(true);

        BootstrapAdminService service = new BootstrapAdminService(users, accounts, properties);
        service.bootstrap();

        verify(users).existsByRoles(Role.DISPATCHER_ADMIN);
        verify(accounts, never()).bootstrapAdmin(any(), any());
    }

    @Test
    void skipsBootstrapWhenBothValuesAbsentOrBlank() {
        BootstrapAdminProperties empty = new BootstrapAdminProperties("", "");
        BootstrapAdminProperties spaces = new BootstrapAdminProperties("   ", "   ");
        BootstrapAdminProperties nulls = new BootstrapAdminProperties(null, null);

        assertThat(empty.isConfigured()).isFalse();
        assertThat(spaces.isConfigured()).isFalse();
        assertThat(nulls.isConfigured()).isFalse();

        BootstrapAdminService service = new BootstrapAdminService(users, accounts, empty);
        service.bootstrap();

        verify(users, never()).existsByRoles(any());
        verify(accounts, never()).bootstrapAdmin(any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "admin@swiftdrop.com, ''",
            "admin@swiftdrop.com, '   '",
            "'', secure-pass",
            "'   ', secure-pass"
    })
    void failsStartupOnPartialConfiguration(String email, String password) {
        assertThatThrownBy(() -> new BootstrapAdminProperties(email, password))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Partial admin bootstrap configuration")
                .hasMessageNotContaining(email.isBlank() ? "non-blank" : email)
                .hasMessageNotContaining(password.isBlank() ? "non-blank" : password);
    }

    @Test
    void failsStartupOnNullEmailWithConfiguredPassword() {
        assertThatThrownBy(() -> new BootstrapAdminProperties(null, "secure-pass"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Partial admin bootstrap configuration")
                .hasMessageNotContaining("secure-pass");
    }

    @Test
    void failsStartupOnNullPasswordWithConfiguredEmail() {
        assertThatThrownBy(() -> new BootstrapAdminProperties("admin@swiftdrop.com", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Partial admin bootstrap configuration")
                .hasMessageNotContaining("admin@swiftdrop.com");
    }

    @Test
    void rejectsPasswordExceeding72Bytes() {
        String longPassword = "p".repeat(73);
        BootstrapAdminProperties properties = new BootstrapAdminProperties("admin@swiftdrop.com", longPassword);
        AccountService realAccounts = new AccountService(users, encoder);
        when(users.existsByRoles(Role.DISPATCHER_ADMIN)).thenReturn(false);

        BootstrapAdminService service = new BootstrapAdminService(users, realAccounts, properties);

        assertThatThrownBy(service::bootstrap)
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("72 bytes");

        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void toStringNeverExposesPassword() {
        BootstrapAdminProperties properties = new BootstrapAdminProperties("admin@swiftdrop.com", "super-secret-password");

        assertThat(properties.toString())
                .doesNotContain("super-secret-password")
                .contains("admin@swiftdrop.com")
                .contains("configured=true");
    }
}
