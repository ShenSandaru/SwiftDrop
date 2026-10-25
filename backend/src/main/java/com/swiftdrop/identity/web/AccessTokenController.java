package com.swiftdrop.identity.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.identity.application.AccountService;
import com.swiftdrop.identity.security.AccessTokenIssuer;

/** Email/password login: exchanges credentials for an access token. */
@RestController
class AccessTokenController {

    private final AccountService accounts;
    private final AccessTokenIssuer tokens;

    AccessTokenController(AccountService accounts, AccessTokenIssuer tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    @PostMapping("/api/v1/auth/tokens")
    AccessTokenResponse login(@Valid @RequestBody LoginRequest request) {
        AccessTokenIssuer.AccessToken token =
                tokens.issue(accounts.authenticate(request.email(), request.password()));
        return new AccessTokenResponse(token.value(), "Bearer", token.lifetime().toSeconds());
    }
}
