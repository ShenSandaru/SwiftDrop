package com.swiftdrop.identity.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.shared.web.PageLimit;

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

    public static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;
    public static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public record AvatarData(byte[] bytes, String contentType, Instant updatedAt) {}

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
        return registerUser("System Administrator", email, rawPassword, EnumSet.of(Role.ADMIN));
    }

    /** Registers a courier staff account. */
    public User registerCourier(String fullName, String email, String rawPassword) {
        return registerUser(fullName, email, rawPassword, EnumSet.of(Role.COURIER));
    }

    /** Registers a staff account (COURIER or DISPATCHER). Rejects ADMIN. */
    public User registerStaff(String fullName, String email, String rawPassword, Role role) {
        if (role != Role.COURIER && role != Role.DISPATCHER) {
            throw new InvalidRoleException("Staff account role must be COURIER or DISPATCHER");
        }
        return registerUser(fullName, email, rawPassword, EnumSet.of(role));
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

    /** Returns all users for administrative management. */
    public List<User> findAllUsers() {
        return users.findAll();
    }

    /** Returns a paged list of users, optionally filtered by role. */
    public Page<User> findUsers(int page, int size, Role roleFilter) {
        int clampedSize = PageLimit.clampSize(size);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (roleFilter != null) {
            return users.findByRole(roleFilter, pageable);
        }
        return users.findAll(pageable);
    }

    /** Enables or disables a user account. The system Administrator account cannot be disabled. */
    public User updateUserStatus(UUID targetUserId, boolean enabled, UUID actingAdminId) {
        if (targetUserId.equals(actingAdminId) && !enabled) {
            throw new SelfDisablingException("An administrator cannot disable their own account");
        }
        User user = users.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getRoles().contains(Role.ADMIN) && !enabled) {
            throw new SelfDisablingException("The system Administrator account cannot be disabled");
        }
        user.setEnabled(enabled);
        return users.saveAndFlush(user);
    }

    /** Updates the personal profile (full name) of a user. */
    public User updateProfile(UUID userId, String fullName) {
        return updateProfile(userId, fullName, null);
    }

    /** Updates the personal profile (full name and phone number) of a user. */
    public User updateProfile(UUID userId, String fullName, String phoneNumber) {
        if (fullName == null || fullName.trim().isBlank()) {
            throw new IllegalArgumentException("Full name cannot be blank");
        }
        String trimmed = fullName.trim();
        if (trimmed.length() > 200) {
            throw new IllegalArgumentException("Full name must be at most 200 characters");
        }
        if (phoneNumber != null && phoneNumber.trim().length() > 30) {
            throw new IllegalArgumentException("Phone number must be at most 30 characters");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }
        user.setFullName(trimmed);
        user.setPhoneNumber(phoneNumber);
        return users.saveAndFlush(user);
    }

    /** Changes the password for an existing account. */
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        if (tooLong(newPassword)) {
            throw new InvalidPasswordException("Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidPasswordException("New password must be at least 8 characters");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }
        if (user.getPasswordHash() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account uses external Google authentication and does not have a local password");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password does not match");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.saveAndFlush(user);
    }

    /** Updates the user's avatar image. */
    public User updateAvatar(UUID userId, byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar file cannot be empty");
        }
        if (bytes.length > MAX_AVATAR_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar file must be at most 2 MB");
        }
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType.trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar must be a JPEG, PNG, or WebP image");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }
        user.updateAvatar(bytes, contentType.trim().toLowerCase(Locale.ROOT));
        return users.saveAndFlush(user);
    }

    /** Retrieves the user's avatar image data, if present. */
    public Optional<AvatarData> getAvatar(UUID userId) {
        return users.findById(userId)
                .filter(User::isEnabled)
                .filter(User::hasAvatar)
                .map(user -> new AvatarData(user.getAvatarPhoto(), user.getAvatarContentType(), user.getAvatarUpdatedAt()));
    }

    /** Removes the user's avatar. */
    public User removeAvatar(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }
        user.removeAvatar();
        return users.saveAndFlush(user);
    }

    @Override
    public Optional<UserSummary> findUser(UUID id) {
        return users.findById(id).map(AccountService::toSummary);
    }

    @Override
    public List<UserSummary> findEnabledCouriers() {
        return users.findByRoleAndEnabledTrue(Role.COURIER).stream()
                .map(AccountService::toSummary)
                .toList();
    }

    @Override
    public List<UserSummary> findEnabledAdmins() {
        return users.findByRoleAndEnabledTrue(Role.ADMIN).stream()
                .map(AccountService::toSummary)
                .toList();
    }

    private static UserSummary toSummary(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
        return new UserSummary(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                roleNames,
                user.isEnabled(),
                user.isProfileCompleted());
    }

    private static boolean tooLong(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
