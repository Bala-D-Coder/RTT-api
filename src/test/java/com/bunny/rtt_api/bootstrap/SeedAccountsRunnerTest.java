package com.bunny.rtt_api.bootstrap;

import com.bunny.rtt_api.config.AppProps;
import com.bunny.rtt_api.entity.User;
import com.bunny.rtt_api.repository.UserRepository;
import com.bunny.rtt_api.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SeedAccountsRunnerTest extends IntegrationTestBase {

    @Autowired
    private SeedAccountsRunner seedAccountsRunner;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppProps appProps;

    @Test
    @DisplayName("SeedAccountsRunner creates super admin and demo user, and is idempotent")
    void seedsAccountsIdempotently() {
        // Run seed
        seedAccountsRunner.seedAccounts();

        // 1. Verify Super Admin
        String adminEmail = appProps.admin().email();
        Optional<User> adminOpt = userRepository.findByEmail(adminEmail);
        assertThat(adminOpt).isPresent();
        User admin = adminOpt.get();
        assertThat(admin.getRole()).isEqualTo("admin");
        assertThat(admin.getExpiresAt()).isNull(); // Super admin never expires
        assertThat(passwordEncoder.matches(appProps.admin().password(), admin.getPasswordHash())).isTrue();

        // 2. Verify Demo Account
        String demoEmail = appProps.demo().email();
        Optional<User> demoOpt = userRepository.findByEmail(demoEmail);
        assertThat(demoOpt).isPresent();
        User demo = demoOpt.get();
        assertThat(demo.getRole()).isEqualTo("demo");
        assertThat(demo.getExpiresAt()).isNotNull(); // 14-day expiry set
        assertThat(passwordEncoder.matches(appProps.demo().password(), demo.getPasswordHash())).isTrue();

        // 3. Second execution should be completely idempotent (no duplicates or errors)
        seedAccountsRunner.seedAccounts();

        long adminCount = userRepository.findAll().stream()
                .filter(u -> adminEmail.equalsIgnoreCase(u.getEmail()))
                .count();
        long demoCount = userRepository.findAll().stream()
                .filter(u -> demoEmail.equalsIgnoreCase(u.getEmail()))
                .count();

        assertThat(adminCount).isEqualTo(1);
        assertThat(demoCount).isEqualTo(1);
    }
}
