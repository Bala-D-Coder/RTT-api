package com.bunny.rtt_api.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    public static final PostgreSQLContainer<?> postgres;

    static {
        System.setProperty("api.version", "1.44");
        System.setProperty("docker.client.api.version", "1.44");
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("rtt_test")
                .withUsername("rtt_test_user")
                .withPassword("rtt_test_pass");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }
}
