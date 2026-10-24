package com.bunny.rtt_api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProps(
        @Valid @NotNull JwtProps jwt,
        @Valid @NotNull AdminProps admin,
        @Valid @NotNull DemoProps demo,
        @NotBlank String supportEmail,
        GeminiProps gemini,
        @Valid @NotNull WhisperProps whisper,
        @Valid @NotNull CorsProps cors,
        @NotBlank @Pattern(regexp = "gemini_live|whisper_local|mock") String engine,
        @NotBlank @Pattern(regexp = "firebase|noop") String push,
        boolean seedOnStartup
) {
    public record JwtProps(
            @NotBlank String accessSecret,
            @NotBlank String refreshSecret,
            int accessTtlMinutes,
            int refreshTtlDays
    ) {}

    public record AdminProps(
            @NotBlank String email,
            @NotBlank String password
    ) {}

    public record DemoProps(
            @NotBlank String email,
            @NotBlank String password,
            int credTtlHours
    ) {}

    public record GeminiProps(
            String apiKey
    ) {}

    public record WhisperProps(
            @NotBlank String url
    ) {}

    public record CorsProps(
            @NotEmpty List<String> allowedOrigins,
            @NotEmpty List<String> allowedMethods
    ) {}
}
