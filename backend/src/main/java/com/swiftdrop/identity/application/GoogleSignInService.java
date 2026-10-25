package com.swiftdrop.identity.application;

import java.util.EnumSet;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.identity.application.GoogleSignInRejectedException.Reason;
import com.swiftdrop.identity.domain.ExternalIdentity;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.IdentityProvider;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;

/**
 * Resolves a validated Google identity to a SwiftDrop account. The link is found only by the
 * Google subject, never by email: an existing account with the same email is not linked
 * automatically, because that would hand the account to whoever controls the Google account.
 * Linking an existing account needs an authenticated confirmation flow, which does not exist yet.
 */
@Service
public class GoogleSignInService {

    /** Matches {@code users.email}. */
    static final int MAX_EMAIL_LENGTH = 320;
    /** Matches {@code users.full_name}. */
    static final int MAX_NAME_LENGTH = 200;

    private final UserRepository users;
    private final ExternalIdentityRepository identities;

    GoogleSignInService(UserRepository users, ExternalIdentityRepository identities) {
        this.users = users;
        this.identities = identities;
    }

    /**
     * Returns the account linked to the Google subject, or creates a {@code CUSTOMER} account and
     * link for a first-time user. Roles come only from the stored account, never from Google.
     */
    @Transactional
    public User signIn(GoogleIdentity identity) {
        String subject = identity.subject();
        if (subject == null || subject.isBlank() || subject.length() > ExternalIdentity.MAX_SUBJECT_LENGTH) {
            throw new GoogleSignInRejectedException(Reason.INVALID_IDENTITY);
        }

        Optional<ExternalIdentity> link = identities.findByProviderAndSubject(IdentityProvider.GOOGLE, subject);
        if (link.isPresent()) {
            return users.findById(link.get().getUserId())
                    .filter(User::isEnabled)
                    .orElseThrow(() -> new GoogleSignInRejectedException(Reason.ACCOUNT_UNAVAILABLE));
        }

        if (!identity.emailVerified()) {
            throw new GoogleSignInRejectedException(Reason.EMAIL_NOT_VERIFIED);
        }
        if (identity.email() == null || identity.email().isBlank()) {
            throw new GoogleSignInRejectedException(Reason.INVALID_IDENTITY);
        }
        String email = User.normalizeEmail(identity.email());
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new GoogleSignInRejectedException(Reason.INVALID_IDENTITY);
        }
        if (users.findByEmail(email).isPresent()) {
            throw new GoogleSignInRejectedException(Reason.EMAIL_ALREADY_REGISTERED);
        }

        try {
            User user = users.saveAndFlush(new User(email, null, fullName(identity.name(), email),
                    EnumSet.of(Role.CUSTOMER)));
            identities.saveAndFlush(new ExternalIdentity(IdentityProvider.GOOGLE, subject, user.getId()));
            return user;
        } catch (DataIntegrityViolationException e) {
            // A concurrent sign-in or registration won; the unique constraints are the final
            // authority. Throwing rolls back the partly created account.
            throw new GoogleSignInRejectedException(Reason.CONFLICT);
        }
    }

    private static String fullName(String name, String email) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            trimmed = email;
        }
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
