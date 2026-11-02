package com.swiftdrop.delivery.domain;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

/**
 * Tracking numbers {@code SD} + 10 random Crockford base32 characters (D-05), for example
 * {@code SD7K3M9Q2XWB}. The alphabet leaves out I, L, O and U, which are easy to misread. 50 random
 * bits make numbers hard to guess; the unique constraint catches the rare collision, and the caller
 * retries with a new number.
 */
@Component
public class TrackingNumberGenerator {

    public static final String PREFIX = "SD";
    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    static final int RANDOM_LENGTH = 10;

    private final RandomGenerator random;

    public TrackingNumberGenerator() {
        this(new SecureRandom());
    }

    TrackingNumberGenerator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public String next() {
        StringBuilder number = new StringBuilder(PREFIX.length() + RANDOM_LENGTH).append(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            number.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return number.toString();
    }

    /** Trims and upper-cases user input so lookups are case-insensitive. */
    public static String normalize(String trackingNumber) {
        return Objects.requireNonNull(trackingNumber, "trackingNumber").trim().toUpperCase(Locale.ROOT);
    }

    /** Whether a normalized value has the tracking-number format. */
    public static boolean isWellFormed(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.length() != PREFIX.length() + RANDOM_LENGTH
                || !trackingNumber.startsWith(PREFIX)) {
            return false;
        }
        for (int i = PREFIX.length(); i < trackingNumber.length(); i++) {
            if (ALPHABET.indexOf(trackingNumber.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
