package com.swiftdrop.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.identity.domain.GoogleLoginCode;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.identity.security.AccessTokenIssuer;
import com.swiftdrop.identity.web.AccessTokenResponse;

/**
 * Handles creation, atomic single-use redemption, and bounded opportunistic cleanup of Google
 * OAuth exchange codes (ADR 0005).
 */
@Service
public class GoogleExchangeService {

    private static final Logger log = LoggerFactory.getLogger(GoogleExchangeService.class);

    public static final Duration CODE_TTL = Duration.ofSeconds(60);
    public static final Duration PURGE_EXPIRED_THRESHOLD = Duration.ofMinutes(5);
    public static final Duration PURGE_USED_THRESHOLD = Duration.ofHours(1);
    public static final int PURGE_BATCH_LIMIT = 50;
    private static final int CODE_BYTE_LENGTH = 32;

    private final GoogleLoginCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final AccessTokenIssuer tokenIssuer;
    private final SecureRandom secureRandom;

    @Autowired
    public GoogleExchangeService(
            @Autowired(required = false) GoogleLoginCodeRepository codeRepository,
            UserRepository userRepository,
            AccessTokenIssuer tokenIssuer) {
        this(codeRepository, userRepository, tokenIssuer, new SecureRandom());
    }

    GoogleExchangeService(
            GoogleLoginCodeRepository codeRepository,
            UserRepository userRepository,
            AccessTokenIssuer tokenIssuer,
            SecureRandom secureRandom) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.tokenIssuer = tokenIssuer;
        this.secureRandom = secureRandom;
    }

    /**
     * Generates a 32-byte cryptographically secure exchange code, stores its SHA-256 digest with
     * a 60-second expiry, executes opportunistic cleanup, and returns the raw URL-safe code.
     */
    @Transactional
    public String createExchangeCode(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        byte[] bytes = new byte[CODE_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        String rawCode = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String codeHash = hash(rawCode);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(CODE_TTL);

        requireCodeRepository().save(new GoogleLoginCode(codeHash, userId, now, expiresAt, null));
        purgeExpiredCodes(now);

        return rawCode;
    }

    /**
     * Atomically redeems a one-time exchange code within a single transaction.
     * Enforces single-use redemption, expiry (60s), and user enablement checks. If an error
     * occurs before commit, the transaction rolls back so the code is not consumed.
     */
    @Transactional
    public AccessTokenResponse exchange(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new InvalidExchangeCodeException();
        }

        String codeHash = hash(rawCode.trim());
        Instant now = Instant.now();

        int updated = requireCodeRepository().markUsedIfValid(codeHash, now);
        if (updated == 0) {
            log.info("Exchange code rejected: code not found, expired, or already used");
            throw new InvalidExchangeCodeException();
        }

        GoogleLoginCode code = requireCodeRepository().findById(codeHash)
                .orElseThrow(InvalidExchangeCodeException::new);

        User user = userRepository.findById(code.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(() -> {
                    log.info("Exchange code rejected: user account not found or disabled");
                    return new InvalidExchangeCodeException();
                });

        purgeExpiredCodes(now);

        AccessTokenIssuer.AccessToken token = tokenIssuer.issue(user);
        return new AccessTokenResponse(token.value(), "Bearer", token.lifetime().toSeconds());
    }

    private void purgeExpiredCodes(Instant now) {
        if (codeRepository == null) {
            return;
        }
        try {
            codeRepository.deleteExpiredOrUsedBefore(
                    now.minus(PURGE_EXPIRED_THRESHOLD),
                    now.minus(PURGE_USED_THRESHOLD),
                    PURGE_BATCH_LIMIT);
        } catch (Exception e) {
            log.debug("Opportunistic cleanup of exchange codes skipped: {}", e.getMessage());
        }
    }

    private GoogleLoginCodeRepository requireCodeRepository() {
        if (codeRepository == null) {
            throw new IllegalStateException("GoogleLoginCodeRepository is not available in the current context");
        }
        return codeRepository;
    }

    public static String hash(String rawCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawCode.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
