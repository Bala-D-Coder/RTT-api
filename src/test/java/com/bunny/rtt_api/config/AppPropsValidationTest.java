package com.bunny.rtt_api.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropsValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void shouldFailValidationWhenRequiredPropertiesAreMissing() {
        AppProps invalidProps = new AppProps(
                new AppProps.JwtProps("", "", 0, 0),
                new AppProps.AdminProps("", ""),
                new AppProps.DemoProps("", "", 0),
                "", // Blank support email
                new AppProps.GeminiProps(""),
                new AppProps.WhisperProps(""),
                new AppProps.CorsProps(List.of(), List.of()), // Empty CORS origins/methods
                "invalid_engine", // Invalid pattern
                "invalid_push",   // Invalid pattern
                false,
                false,
                new AppProps.SheetsProps("")
        );

        var violations = validator.validate(invalidProps);
        assertThat(violations).isNotEmpty();

        boolean hasSupportEmailViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("supportEmail"));
        boolean hasEngineViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("engine"));
        boolean hasCorsOriginsViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().contains("cors.allowedOrigins"));
        boolean hasCorsMethodsViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().contains("cors.allowedMethods"));

        assertThat(hasSupportEmailViolation).isTrue();
        assertThat(hasEngineViolation).isTrue();
        assertThat(hasCorsOriginsViolation).isTrue();
        assertThat(hasCorsMethodsViolation).isTrue();
    }

    @Test
    void shouldPassValidationWhenAllRequiredPropertiesAreValid() {
        AppProps validProps = new AppProps(
                new AppProps.JwtProps("access-secret-32-bytes-long-key-for-test!", "refresh-secret-32-bytes-long-key-for-test!", 15, 14),
                new AppProps.AdminProps("admin@apex.in", "adminPass123!"),
                new AppProps.DemoProps("demo@apex.in", "demoPass123!", 24),
                "support@apex.in",
                new AppProps.GeminiProps("fake-gemini-key"),
                new AppProps.WhisperProps("http://127.0.0.1:8001"),
                new AppProps.CorsProps(List.of("http://localhost:5173"), List.of("GET", "POST")),
                "gemini_live",
                "firebase",
                true,
                false,
                new AppProps.SheetsProps("https://script.google.com/test")
        );

        var violations = validator.validate(validProps);
        assertThat(violations).isEmpty();
    }
}
