package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resolver.sql.SqlMigrationResolver;
import org.flywaydb.core.internal.scanner.Scanner;
import org.flywaydb.core.internal.sqlscript.SqlScript;
import org.junit.jupiter.api.Test;

/**
 * Checks the migration convention and Flyway discovery without a database: each migration is in its
 * owning module's folder {@code db/migration/<module>/}, is named
 * {@code V<yyyyMMddHHmm>__<module>_<description>.sql}, no two migrations share a version, and
 * Flyway resolves all module migrations from the configured {@code spring.flyway.locations}.
 */
class MigrationConventionTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Path APPLICATION_PROPERTIES = Path.of("src/main/resources/application.properties");
    private static final List<String> MODULES = List.of(
            "identity", "pricing", "delivery", "dispatch", "merchant", "notification", "tracking");
    private static final Pattern NAME =
            Pattern.compile("V(\\d{12})__([a-z]+)_[a-z0-9_]+\\.sql");

    @Test
    void migrationsFollowTheConvention() throws IOException {
        Map<String, Path> versions = new HashMap<>();
        List<Path> files;
        try (Stream<Path> paths = Files.walk(MIGRATIONS)) {
            files = paths.filter(Files::isRegularFile).toList();
        }
        assertThat(files).isNotEmpty();

        for (Path file : files) {
            Path relative = MIGRATIONS.relativize(file);
            assertThat(relative.getNameCount()).as("%s is directly in a module folder", relative)
                    .isEqualTo(2);
            String folder = relative.getName(0).toString();
            assertThat(MODULES).as("module folder of %s", relative).contains(folder);

            Matcher name = NAME.matcher(relative.getFileName().toString());
            assertThat(name.matches()).as("%s matches %s", relative, NAME).isTrue();
            assertThat(name.group(2)).as("module prefix of %s", relative).isEqualTo(folder);

            Path previous = versions.put(name.group(1), relative);
            assertThat(previous).as("version %s used twice", name.group(1)).isNull();
        }
    }

    @Test
    void flywayDiscoversAllModuleMigrationsFromConfiguredLocations() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(APPLICATION_PROPERTIES)) {
            properties.load(in);
        }
        String locationsProperty = properties.getProperty("spring.flyway.locations");
        assertThat(locationsProperty).isEqualTo("classpath:db/migration");

        String[] locations = Stream.of(locationsProperty.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        Configuration configuration = Flyway.configure()
                .locations(locations)
                .failOnMissingLocations(true);

        Scanner<Object> scanner = new Scanner<>(
                Object.class, configuration, configuration.getLocations());
        SqlMigrationResolver resolver = new SqlMigrationResolver(
                scanner,
                null,
                (resource, mixed, resourceProvider) -> mock(SqlScript.class),
                configuration,
                new ParsingContext());

        Collection<ResolvedMigration> resolved = resolver.resolveMigrations(null);
        assertThat(resolved)
                .extracting(
                        m -> m.getVersion().getVersion(),
                        ResolvedMigration::getDescription,
                        ResolvedMigration::getScript)
                .containsExactly(
                        tuple(
                                "202610251600",
                                "identity create users and roles",
                                "identity/V202610251600__identity_create_users_and_roles.sql"),
                        tuple(
                                "202610251700",
                                "identity create external identities",
                                "identity/V202610251700__identity_create_external_identities.sql"),
                        tuple(
                                "202610261800",
                                "identity create google login codes",
                                "identity/V202610261800__identity_create_google_login_codes.sql"));
    }
}
