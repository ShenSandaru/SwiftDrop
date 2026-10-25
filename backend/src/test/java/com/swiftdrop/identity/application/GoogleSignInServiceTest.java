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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.identity.application.GoogleSignInRejectedException.Reason;
import com.swiftdrop.identity.domain.ExternalIdentity;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.IdentityProvider;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;

class GoogleSignInServiceTest {

    private static final String SUBJECT = "110169484474386276334";

    private UserRepository users;
    private ExternalIdentityRepository identities;
    private GoogleSignInService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        identities = mock(ExternalIdentityRepository.class);
        when(users.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        service = new GoogleSignInService(users, identities);
    }

    @Test
    void firstSignInCreatesPasswordlessCustomerLinkedBySubject() {
        User user = service.signIn(new GoogleIdentity(SUBJECT, " Alice@Example.com", true, " Alice "));

        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getFullName()).isEqualTo("Alice");
        assertThat(user.getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(user.getPasswordHash()).isNull();
        ArgumentCaptor<ExternalIdentity> link = ArgumentCaptor.forClass(ExternalIdentity.class);
        verify(identities).saveAndFlush(link.capture());
        assertThat(link.getValue().getProvider()).isEqualTo(IdentityProvider.GOOGLE);
        assertThat(link.getValue().getSubject()).isEqualTo(SUBJECT);
        assertThat(link.getValue().getUserId()).isEqualTo(user.getId());
    }

    @Test
    void linkedSubjectResolvesToTheSameAccountEvenWhenTheGoogleEmailChanged() {
        User alice = linkedUser(Role.CUSTOMER);

        User user = service.signIn(new GoogleIdentity(SUBJECT, "renamed@example.com", false, "Other"));

        assertThat(user).isSameAs(alice);
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        verify(users, never()).saveAndFlush(any());
        verify(users, never()).findByEmail(any());
    }

    @Test
    void neverLinksAnExistingAccountByMatchingEmail() {
        when(users.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(new User("alice@example.com", "{bcrypt}x", "Alice", Set.of(Role.CUSTOMER))));

        assertRejected(new GoogleIdentity(SUBJECT, "ALICE@example.com", true, "Alice"),
                Reason.EMAIL_ALREADY_REGISTERED);
        verify(users, never()).saveAndFlush(any());
        verify(identities, never()).saveAndFlush(any());
    }

    @Test
    void newAccountNeedsAVerifiedEmail() {
        assertRejected(new GoogleIdentity(SUBJECT, "alice@example.com", false, "Alice"), Reason.EMAIL_NOT_VERIFIED);
        assertRejected(new GoogleIdentity(SUBJECT, null, true, "Alice"), Reason.INVALID_IDENTITY);
        assertRejected(new GoogleIdentity(SUBJECT, "a".repeat(310) + "@example.com", true, "A"),
                Reason.INVALID_IDENTITY);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMissingOrOversizedSubject() {
        assertRejected(new GoogleIdentity(null, "alice@example.com", true, "Alice"), Reason.INVALID_IDENTITY);
        assertRejected(new GoogleIdentity(" ", "alice@example.com", true, "Alice"), Reason.INVALID_IDENTITY);
        assertRejected(new GoogleIdentity("1".repeat(256), "alice@example.com", true, "Alice"),
                Reason.INVALID_IDENTITY);
    }

    @Test
    void disabledOrMissingLinkedAccountIsRejected() {
        User alice = linkedUser(Role.CUSTOMER);
        ReflectionTestUtils.setField(alice, "enabled", false);
        assertRejected(new GoogleIdentity(SUBJECT, "alice@example.com", true, "Alice"), Reason.ACCOUNT_UNAVAILABLE);

        when(users.findById(alice.getId())).thenReturn(Optional.empty());
        assertRejected(new GoogleIdentity(SUBJECT, "alice@example.com", true, "Alice"), Reason.ACCOUNT_UNAVAILABLE);
    }

    @Test
    void concurrentCreationIsRejectedNotDuplicated() {
        when(identities.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));

        assertRejected(new GoogleIdentity(SUBJECT, "alice@example.com", true, "Alice"), Reason.CONFLICT);
    }

    @Test
    void blankNameFallsBackToEmailAndLongNameIsTruncated() {
        assertThat(service.signIn(new GoogleIdentity(SUBJECT, "alice@example.com", true, null)).getFullName())
                .isEqualTo("alice@example.com");
        assertThat(service.signIn(new GoogleIdentity("other", "bob@example.com", true, "B".repeat(250)))
                .getFullName()).hasSize(GoogleSignInService.MAX_NAME_LENGTH);
    }

    private void assertRejected(GoogleIdentity identity, Reason reason) {
        assertThatThrownBy(() -> service.signIn(identity))
                .isInstanceOfSatisfying(GoogleSignInRejectedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason));
    }

    private User linkedUser(Role role) {
        User user = withId(new User("alice@example.com", null, "Alice", Set.of(role)));
        when(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.of(new ExternalIdentity(IdentityProvider.GOOGLE, SUBJECT, user.getId())));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    private static User withId(User user) {
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
