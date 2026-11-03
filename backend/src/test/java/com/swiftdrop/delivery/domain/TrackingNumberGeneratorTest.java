package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrackingNumberGeneratorTest {

    private final TrackingNumberGenerator generator = new TrackingNumberGenerator();

    @Test
    void numbersHavePrefixLengthAndOnlyUnambiguousCharacters() {
        for (int i = 0; i < 1_000; i++) {
            String number = generator.next();

            assertThat(number).hasSize(12).startsWith("SD").matches("SD[0-9A-HJKMNP-TV-Z]{10}");
            assertThat(number.substring(2)).doesNotContain("I", "L", "O", "U");
            assertThat(TrackingNumberGenerator.isWellFormed(number)).isTrue();
        }
    }

    @Test
    void numbersAreRandom() {
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            numbers.add(generator.next());
        }

        assertThat(numbers).hasSize(10_000);
    }

    @Test
    void everyAlphabetCharacterCanAppear() {
        // A generator that walks through the alphabet in order
        RandomGenerator sequential = new RandomGenerator() {
            private int next;

            @Override
            public long nextLong() {
                return next++;
            }

            @Override
            public int nextInt(int bound) {
                return next++ % bound;
            }
        };
        TrackingNumberGenerator walking = new TrackingNumberGenerator(sequential);
        StringBuilder seen = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            seen.append(walking.next().substring(2));
        }

        assertThat(seen.toString()).startsWith(TrackingNumberGenerator.ALPHABET);
    }

    @Test
    void normalizeTrimsAndUpperCases() {
        assertThat(TrackingNumberGenerator.normalize("  sd7k3m9q2xwb ")).isEqualTo("SD7K3M9Q2XWB");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "SD7K3M9Q2XW", "SD7K3M9Q2XWBB", "XX7K3M9Q2XWB", "SD7K3M9Q2XWI", "SD7K3M9Q2XWU",
        "sd7k3m9q2xwb"})
    void malformedNumbersAreRecognized(String number) {
        assertThat(TrackingNumberGenerator.isWellFormed(number)).isFalse();
    }
}
