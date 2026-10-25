package com.swiftdrop.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void newUserIsEnabledWithNormalizedEmail() {
        User user = new User("  Alice@Example.COM ", "hash", "Alice", Set.of(Role.CUSTOMER));

        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(user.getId()).as("generated when persisted").isNull();
    }

    @Test
    void passwordHashIsOptionalForExternalSignIn() {
        User user = new User("bob@example.com", null, "Bob", Set.of(Role.CUSTOMER));

        assertThat(user.getPasswordHash()).isNull();
    }

    @Test
    void requiresAtLeastOneRole() {
        assertThatThrownBy(() -> new User("carol@example.com", "hash", "Carol", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rolesCannotBeChangedThroughTheGetter() {
        User user = new User("dan@example.com", "hash", "Dan", Set.of(Role.COURIER));

        assertThatThrownBy(() -> user.getRoles().add(Role.DISPATCHER_ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
