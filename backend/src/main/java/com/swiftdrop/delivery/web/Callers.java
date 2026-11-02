package com.swiftdrop.delivery.web;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

import com.swiftdrop.delivery.application.Viewer;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.identity.api.IdentityClaims;

/** Reads the caller from the access token issued by {@code identity}: subject = user ID, plus roles. */
final class Callers {

    private Callers() {
    }

    static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    static Viewer viewer(Jwt jwt) {
        Set<ActorRole> roles = EnumSet.noneOf(ActorRole.class);
        List<String> names = jwt.getClaimAsStringList(IdentityClaims.ROLES);
        if (names != null) {
            for (String name : names) {
                for (ActorRole role : ActorRole.values()) {
                    if (role.name().equals(name)) {
                        roles.add(role);
                    }
                }
            }
        }
        return new Viewer(userId(jwt), roles);
    }
}
