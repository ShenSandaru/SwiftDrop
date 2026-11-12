package com.swiftdrop.dispatch.web;

import java.util.UUID;

import com.swiftdrop.identity.api.UserSummary;

record DispatchCourierResponse(UUID id, String fullName) {

    static DispatchCourierResponse from(UserSummary user) {
        return new DispatchCourierResponse(user.id(), user.fullName());
    }
}

