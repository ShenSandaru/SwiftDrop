package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Checks the module rules of ADR 0003 by scanning the main source files, without extra
 * dependencies: a module references another module only through its {@code api} package and only
 * along the allowed graph; no module references {@code config}; {@code shared} references no
 * module. Every {@code com.swiftdrop.*} reference outside comments counts, including fully
 * qualified names and static imports. The check is textual, so it cannot see reflection.
 */
class ModuleBoundaryTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");

    /** The allowed module dependencies, exactly as in ADR 0003. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "identity", Set.of(),
            "pricing", Set.of(),
            "delivery", Set.of("identity", "pricing"),
            "dispatch", Set.of("identity", "delivery"),
            "merchant", Set.of("identity", "delivery"),
            "notification", Set.of("identity", "delivery"),
            "tracking", Set.of("delivery"));

    private static final String CONFIG = "config";
    private static final String SHARED = "shared";
    /** Classes directly in {@code com.swiftdrop}, such as the application class. */
    private static final String ROOT = "";

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;",
            Pattern.MULTILINE);
    private static final Pattern REFERENCE = Pattern.compile("\\bcom\\.swiftdrop((?:\\.\\w+)*)");
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    @Test
    void mainSourcesRespectModuleBoundaries() throws IOException {
        List<String> violations = new ArrayList<>();
        int files = 0;
        try (Stream<Path> paths = Files.walk(MAIN_SOURCES)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                files++;
                String source = Files.readString(file);
                violations.addAll(violations(file.toString(), source));
            }
        }
        assertThat(files).as("Java files found under %s", MAIN_SOURCES).isGreaterThan(0);
        assertThat(violations).isEmpty();
    }

    @Test
    void everyDecidedModuleExists() {
        for (String module : ALLOWED.keySet()) {
            assertThat(MAIN_SOURCES.resolve("com/swiftdrop").resolve(module).resolve("api"))
                    .as("api package of module %s", module)
                    .isDirectory();
        }
    }

    // The checker itself is tested so that an empty result is not a false pass.

    @Test
    void allowsApiOfPermittedModuleAndShared() {
        assertThat(violations("A.java", """
                package com.swiftdrop.delivery.application;
                import com.swiftdrop.identity.api.UserLookup;
                import com.swiftdrop.pricing.api.FeeQuote;
                import com.swiftdrop.delivery.domain.Parcel;
                import com.swiftdrop.shared.ErrorResponse;
                """)).isEmpty();
    }

    @Test
    void rejectsInternalPackageOfAnotherModule() {
        assertThat(violations("A.java", """
                package com.swiftdrop.dispatch.application;
                import com.swiftdrop.delivery.domain.ParcelRepository;
                """)).hasSize(1);
    }

    @Test
    void rejectsDependencyOutsideTheGraph() {
        assertThat(violations("A.java", """
                package com.swiftdrop.delivery.application;
                import static com.swiftdrop.dispatch.api.CourierLookup.find;
                """)).hasSize(1);
    }

    @Test
    void rejectsFullyQualifiedReferenceButIgnoresComments() {
        assertThat(violations("A.java", """
                package com.swiftdrop.tracking.web;
                /** See {@link com.swiftdrop.delivery.domain.Parcel}. */
                class A {
                    // com.swiftdrop.merchant.domain.Merchant
                    com.swiftdrop.delivery.domain.Parcel parcel;
                }
                """)).hasSize(1);
    }

    @Test
    void rejectsModuleUsingConfigAndSharedUsingModule() {
        assertThat(violations("A.java", """
                package com.swiftdrop.identity.web;
                import com.swiftdrop.config.SecurityConfig;
                """)).hasSize(1);
        assertThat(violations("B.java", """
                package com.swiftdrop.shared;
                import com.swiftdrop.delivery.api.ParcelStatus;
                """)).hasSize(1);
    }

    @Test
    void allowsConfigToUseModuleApisOnly() {
        assertThat(violations("A.java", """
                package com.swiftdrop.config;
                import com.swiftdrop.identity.api.UserLookup;
                import com.swiftdrop.merchant.api.MerchantAuthentication;
                """)).isEmpty();
        assertThat(violations("B.java", """
                package com.swiftdrop.config;
                import com.swiftdrop.identity.domain.UserRepository;
                """)).hasSize(1);
    }

    @Test
    void rejectsUnknownTopLevelPackage() {
        assertThat(violations("A.java", "package com.swiftdrop.reporting;\n")).hasSize(1);
    }

    private static List<String> violations(String file, String source) {
        String code = COMMENTS.matcher(source).replaceAll(" ");
        Matcher packageMatcher = PACKAGE.matcher(code);
        if (!packageMatcher.find()) {
            return List.of(file + ": no package declaration");
        }
        String[] sourcePackage = segments(packageMatcher.group(1));
        if (sourcePackage == null) {
            return List.of(file + ": outside com.swiftdrop");
        }
        String from = topLevel(sourcePackage);
        if (!isKnown(from)) {
            return List.of(file + ": unknown top-level package com.swiftdrop." + from);
        }

        List<String> result = new ArrayList<>();
        Matcher reference = REFERENCE.matcher(code.substring(packageMatcher.end()));
        while (reference.find()) {
            String[] target = segments("com.swiftdrop" + reference.group(1));
            String to = topLevel(target);
            String sub = target.length > 1 ? target[1] : "";
            if (!isAllowed(from, to, sub)) {
                result.add(file + ": " + packageMatcher.group(1) + " must not reference "
                        + reference.group());
            }
        }
        return result;
    }

    private static boolean isAllowed(String from, String to, String sub) {
        if (from.equals(to) || to.equals(SHARED)) {
            return true;
        }
        if (from.equals(SHARED)) {
            return false;
        }
        boolean moduleApi = ALLOWED.containsKey(to) && sub.equals("api");
        if (from.equals(CONFIG) || from.equals(ROOT)) {
            return moduleApi || to.equals(CONFIG) || to.equals(ROOT);
        }
        return moduleApi && ALLOWED.get(from).contains(to);
    }

    private static boolean isKnown(String topLevel) {
        return ALLOWED.containsKey(topLevel) || Set.of(CONFIG, SHARED, ROOT).contains(topLevel);
    }

    /** The segments after {@code com.swiftdrop}, or {@code null} if outside it. */
    private static String[] segments(String name) {
        if (name.equals("com.swiftdrop")) {
            return new String[0];
        }
        if (!name.startsWith("com.swiftdrop.")) {
            return null;
        }
        return name.substring("com.swiftdrop.".length()).split("\\.");
    }

    /** The first segment, or {@link #ROOT} for {@code com.swiftdrop} and its classes. */
    private static String topLevel(String[] segments) {
        if (segments.length == 0 || Character.isUpperCase(segments[0].charAt(0))) {
            return ROOT;
        }
        return segments[0];
    }
}
