package com.swiftdrop.identity.application;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;

/** Local email/password accounts: public registration and login. */
@Service
public class AccountService {

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
        if (tooLong(rawPassword)) {
            throw new InvalidPasswordException("Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
        String normalizedEmail = User.normalizeEmail(email);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword), fullName.trim(),
                EnumSet.of(Role.CUSTOMER));
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

    private static boolean tooLong(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
