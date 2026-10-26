package com.swiftdrop.test.security;

import java.util.Objects;
import java.util.function.Consumer;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Declares one cell / test case in the table-driven authorization matrix (TST-2).
 *
 * @param description        human-readable scenario description
 * @param method             HTTP method under test
 * @param path               endpoint URI under test
 * @param principal          caller identity (anonymous, JWT with roles, or API key)
 * @param expectedStatus     expected HTTP response status
 * @param requestCustomizer  optional builder customizer (e.g. JSON body, headers, params)
 */
public record AuthorizationMatrixRow(
        String description,
        HttpMethod method,
        String path,
        TestPrincipal principal,
        HttpStatus expectedStatus,
        Consumer<MockHttpServletRequestBuilder> requestCustomizer
) {
    public AuthorizationMatrixRow {
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(expectedStatus, "expectedStatus must not be null");
    }

    public static Builder row(String description) {
        return new Builder(description);
    }

    public static AuthorizationMatrixRow row(
            String description,
            HttpMethod method,
            String path,
            TestPrincipal principal,
            HttpStatus expectedStatus
    ) {
        return new AuthorizationMatrixRow(description, method, path, principal, expectedStatus, null);
    }

    public static AuthorizationMatrixRow row(
            String description,
            HttpMethod method,
            String path,
            TestPrincipal principal,
            HttpStatus expectedStatus,
            Consumer<MockHttpServletRequestBuilder> customizer
    ) {
        return new AuthorizationMatrixRow(description, method, path, principal, expectedStatus, customizer);
    }

    public static class Builder {
        private final String description;
        private HttpMethod method = HttpMethod.GET;
        private String path;
        private TestPrincipal principal = TestPrincipal.anonymous();
        private HttpStatus expectedStatus = HttpStatus.OK;
        private Consumer<MockHttpServletRequestBuilder> requestCustomizer;

        public Builder(String description) {
            this.description = description;
        }

        public Builder get(String path) {
            this.method = HttpMethod.GET;
            this.path = path;
            return this;
        }

        public Builder post(String path) {
            this.method = HttpMethod.POST;
            this.path = path;
            return this;
        }

        public Builder put(String path) {
            this.method = HttpMethod.PUT;
            this.path = path;
            return this;
        }

        public Builder patch(String path) {
            this.method = HttpMethod.PATCH;
            this.path = path;
            return this;
        }

        public Builder delete(String path) {
            this.method = HttpMethod.DELETE;
            this.path = path;
            return this;
        }

        public Builder principal(TestPrincipal principal) {
            this.principal = principal;
            return this;
        }

        public Builder expect(HttpStatus status) {
            this.expectedStatus = status;
            return this;
        }

        public Builder customize(Consumer<MockHttpServletRequestBuilder> customizer) {
            this.requestCustomizer = customizer;
            return this;
        }

        public AuthorizationMatrixRow build() {
            return new AuthorizationMatrixRow(description, method, path, principal, expectedStatus, requestCustomizer);
        }
    }
}
