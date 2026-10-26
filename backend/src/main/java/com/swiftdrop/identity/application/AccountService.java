package com.swiftdrop.identity.application;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;

/** Local email/password accounts: public registration, login, and user directory facade. */
@Service
public class AccountService implements UserDirectory {

    /** BCrypt uses at most 72 bytes, and Spring Security rejects longer input. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    /** Checked when the account does not exist, so both cases take similar time. */
    private final String dummyHash;

    AccountService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Registers a customer. Public registration never grants any other role. */
    public User registerCustomer(String fullName, String email, String rawPassword) {
        return registerUser(fullName, email, rawPassword, EnumSet.of(Role.CUSTOMER));
    }

    @Override
    public UUID createMerchantUser(String fullName, String email, String rawPassword) {
        return registerUser(fullName, email, rawPassword, EnumSet.of(Role.MERCHANT)).getId();
    }

    /** Bootstraps the initial administrator account on startup. */
    public User bootstrapAdmin(String email, String rawPassword) {
        return registerUser("System Administrator", email, rawPassword, EnumSet.of(Role.DISPATCHER_ADMIN));
    }

    private User registerUser(String fullName, String email, String rawPassword, Set<Role> roles) {
        if (tooLong(rawPassword)) {
            throw new InvalidPasswordException("Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
        String normalizedEmail = User.normalizeEmail(email);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword), fullName.trim(), roles);
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // A concurrent registration won; the unique constraint is the final authority.
            throw new EmailAlreadyRegisteredException();
        }
    }

    /** Returns the user if the email and password match an enabled account with a password. */
    public User authenticate(String email, String rawPassword) {
        if (tooLong(rawPassword)) {
            throw new InvalidCredentialsException();
        }
        Optional<User> user = users.findByEmailIgnoringCase(email);
        String hash = user.map(User::getPasswordHash).orElse(null);
        boolean matches = passwordEncoder.matches(rawPassword, hash != null ? hash : dummyHash);
        if (!matches || hash == null || !user.get().isEnabled()) {
            throw new InvalidCredentialsException();
        }
        return user.get();
    }

    public Optional<User> findById(UUID id) {
        return users.findById(id);
    }

    @Override
    public Optional<UserSummary> findUser(UUID id) {
        return users.findById(id).map(AccountService::toSummary);
    }

    private static UserSummary toSummary(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail(), roleNames, user.isEnabled());
    }

    private static boolean tooLong(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
