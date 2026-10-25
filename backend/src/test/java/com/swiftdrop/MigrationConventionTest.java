package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Checks the migration convention without a database: each migration is in its owning module's
 * folder {@code db/migration/<module>/}, is named
 * {@code V<yyyyMMddHHmm>__<module>_<description>.sql}, and no two migrations share a version.
 * It does not run Flyway; that needs PostgreSQL.
 */
class MigrationConventionTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
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
}
