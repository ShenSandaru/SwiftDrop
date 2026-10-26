package com.swiftdrop.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.UserRepository;

/**
 * Creates the initial {@link Role#DISPATCHER_ADMIN} user at application startup if configured
 * via environment variables and no administrator account exists yet.
 */
@Service
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminService.class);

    private final UserRepository users;
    private final AccountService accounts;
    private final BootstrapAdminProperties properties;

    public BootstrapAdminService(UserRepository users, AccountService accounts,
            BootstrapAdminProperties properties) {
        this.users = users;
        this.accounts = accounts;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    void bootstrap() {
        if (!properties.isConfigured()) {
            return;
        }
        if (users.existsByRoles(Role.DISPATCHER_ADMIN)) {
            log.info("Administrator account already exists; skipping bootstrap");
            return;
        }
        accounts.bootstrapAdmin(properties.adminEmail(), properties.adminPassword());
        log.info("Bootstrapped initial Dispatcher/Admin account");
    }
}
