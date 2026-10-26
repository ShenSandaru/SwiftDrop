package com.swiftdrop.test.security;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.swiftdrop.identity.api.IdentityClaims;
import com.swiftdrop.identity.domain.Role;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Represents a security principal state for table-driven authorization matrix tests (TST-2).
 * Supports anonymous access, authenticated JWT principals per role with custom subject UUIDs,
 * and merchant API keys.
 */
public interface TestPrincipal {

    String name();

    RequestPostProcessor postProcessor();

    static TestPrincipal anonymous() {
        return new AnonymousPrincipal();
    }

    static TestPrincipal customer() {
        return user(UUID.randomUUID(), Role.CUSTOMER);
    }

    static TestPrincipal customer(UUID userId) {
        return user(userId, Role.CUSTOMER);
    }

    static TestPrincipal merchant() {
        return user(UUID.randomUUID(), Role.MERCHANT);
    }

    static TestPrincipal merchant(UUID userId) {
        return user(userId, Role.MERCHANT);
    }

    static TestPrincipal courier() {
        return user(UUID.randomUUID(), Role.COURIER);
    }

    static TestPrincipal courier(UUID userId) {
        return user(userId, Role.COURIER);
    }

    static TestPrincipal dispatcherAdmin() {
        return user(UUID.randomUUID(), Role.DISPATCHER_ADMIN);
    }

    static TestPrincipal dispatcherAdmin(UUID userId) {
        return user(userId, Role.DISPATCHER_ADMIN);
    }

    static TestPrincipal user(UUID userId, Role... roles) {
        return new JwtPrincipal(userId, Set.of(roles));
    }

    static TestPrincipal user(UUID userId, Set<Role> roles) {
        return new JwtPrincipal(userId, roles);
    }

    static TestPrincipal apiKey(String key) {
        return new ApiKeyPrincipal(key);
    }

    record AnonymousPrincipal() implements TestPrincipal {
        @Override
        public String name() {
            return "ANONYMOUS";
        }

        @Override
        public RequestPostProcessor postProcessor() {
            return request -> request;
        }
    }

    record JwtPrincipal(UUID userId, Set<Role> roles) implements TestPrincipal {
        public JwtPrincipal {
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(roles, "roles must not be null");
        }

        @Override
        public String name() {
            String roleStr = roles.stream().map(Role::name).sorted().collect(Collectors.joining(","));
            return "JWT[sub=" + userId + ", roles=" + roleStr + "]";
        }

        @Override
        public RequestPostProcessor postProcessor() {
            List<String> roleNames = roles.stream().map(Role::name).sorted().toList();
            GrantedAuthority[] authorities = roles.stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                    .toArray(GrantedAuthority[]::new);

            return jwt()
                    .jwt(builder -> builder
                            .subject(userId.toString())
                            .claim(IdentityClaims.ROLES, roleNames))
                    .authorities(authorities);
        }
    }

    record ApiKeyPrincipal(String apiKey) implements TestPrincipal {
        public ApiKeyPrincipal {
            Objects.requireNonNull(apiKey, "apiKey must not be null");
        }

        @Override
        public String name() {
            return "API_KEY[" + apiKey + "]";
        }

        @Override
        public RequestPostProcessor postProcessor() {
            return request -> {
                request.addHeader("X-API-Key", apiKey);
                return request;
            };
        }
    }
}
