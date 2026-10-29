package com.bunny.rtt_api.bootstrap;

import com.bunny.rtt_api.config.AppProps;
import com.bunny.rtt_api.entity.User;
import com.bunny.rtt_api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Order(1)
public class SeedAccountsRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedAccountsRunner.class);

    private final AppProps appProps;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedAccountsRunner(AppProps appProps, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.appProps = appProps;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean shouldSeed = appProps.seedOnStartup()
                || appProps.seedAccounts()
                || args.containsOption("app.seed-accounts")
                || args.containsOption("seed-accounts");

        if (!shouldSeed) {
            log.info("SeedAccountsRunner skipped (seed-on-startup and seed-accounts are false)");
            return;
        }

        seedAccounts();
    }

    @Transactional
    public void seedAccounts() {
        seedAdminAccount();
        seedDemoAccount();
    }

    private void seedAdminAccount() {
        String adminEmail = appProps.admin().email();
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            User admin = User.builder()
                    .email(adminEmail)
                    .name("Super Admin")
                    .passwordHash(passwordEncoder.encode(appProps.admin().password()))
                    .role("admin")
                    .timezone("Asia/Kolkata")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .expiresAt(null)
                    .build();
            userRepository.save(admin);
            log.info("Seeded super admin account: {}", adminEmail);
        } else {
            log.info("Admin account already exists: {}", adminEmail);
        }
    }

    private void seedDemoAccount() {
        String demoEmail = appProps.demo().email();
        if (userRepository.findByEmail(demoEmail).isEmpty()) {
            // Per B20: newly issued/seeded demo pair gets 14-day expiry until first login shortens to 24h
            Instant expiresAt = Instant.now().plus(14, ChronoUnit.DAYS);
            User demo = User.builder()
                    .email(demoEmail)
                    .name("Demo User")
                    .passwordHash(passwordEncoder.encode(appProps.demo().password()))
                    .role("demo")
                    .timezone("Asia/Kolkata")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .expiresAt(expiresAt)
                    .build();
            userRepository.save(demo);
            log.info("Seeded demo account: {}", demoEmail);
        } else {
            log.info("Demo account already exists: {}", demoEmail);
        }
    }
}
