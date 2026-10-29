package com.bunny.rtt_api.controller;

import com.bunny.rtt_api.bootstrap.SeedAccountsRunner;
import com.bunny.rtt_api.config.AppProps;
import com.bunny.rtt_api.dto.request.CredentialIssueRequest;
import com.bunny.rtt_api.dto.request.CredentialUpdateRequest;
import com.bunny.rtt_api.dto.request.LoginRequest;
import com.bunny.rtt_api.dto.response.CredentialIssuedResponse;
import com.bunny.rtt_api.dto.response.CredentialRowResponse;
import com.bunny.rtt_api.dto.response.SessionResponse;
import com.bunny.rtt_api.entity.User;
import com.bunny.rtt_api.repository.UserRepository;
import com.bunny.rtt_api.security.IpRateLimiter;
import com.bunny.rtt_api.security.TokenService;
import com.bunny.rtt_api.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndAdminIntegrationTest extends IntegrationTestBase {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private SeedAccountsRunner seedAccountsRunner;

        @Autowired
        private AppProps appProps;

        @Autowired
        private TokenService tokenService;

        @Autowired
        private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

        @Autowired
        private IpRateLimiter ipRateLimiter;

        @BeforeEach
        void setUp() {
                seedAccountsRunner.seedAccounts();
                userRepository.findByEmail(appProps.admin().email()).ifPresent(admin -> {
                        admin.setPasswordHash(passwordEncoder.encode(appProps.admin().password()));
                        admin.setLockedUntil(null);
                        admin.setAttemptCount(0);
                        admin.setDisabledAt(null);
                        admin.setExpiresAt(null);
                        userRepository.save(admin);
                });
                userRepository.findByEmail(appProps.demo().email()).ifPresent(demo -> {
                        demo.setPasswordHash(passwordEncoder.encode(appProps.demo().password()));
                        demo.setLockedUntil(null);
                        demo.setAttemptCount(0);
                        demo.setDisabledAt(null);
                        userRepository.save(demo);
                });
                ipRateLimiter.reset("127.0.0.1");
        }

        @Test
        void shouldLoginAsAdminAndSetHttpOnlyRefreshCookie() throws Exception {
                LoginRequest request = new LoginRequest(appProps.admin().email(), appProps.admin().password());

                MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.user.email").value(appProps.admin().email()))
                                .andExpect(jsonPath("$.user.role").value("admin"))
                                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                                .andReturn();

                String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                assertThat(setCookie).isNotNull();
                assertThat(setCookie).contains("rtt_refresh=");
                assertThat(setCookie).contains("HttpOnly");
                assertThat(setCookie).contains("Path=/api/v1/auth");
                assertThat(setCookie).contains("SameSite=Lax");
        }

        @Test
        void shouldFailLoginWithInvalidCredentials() throws Exception {
                LoginRequest request = new LoginRequest(appProps.admin().email(), "wrong-password");

                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.error.code").value("invalid_credentials"))
                                .andExpect(jsonPath("$.error.message").isNotEmpty());
        }

        @Test
        void shouldLockAccountAfter5FailedAttemptsAndReturnInvalidCredentials() throws Exception {
                String email = "lockout-test@apex.in";
                User user = User.builder()
                                .email(email)
                                .name("Lockout Test")
                                .passwordHash(passwordEncoder.encode("correctPassword123!"))
                                .role("demo")
                                .timezone("Asia/Kolkata")
                                .attemptCount(0)
                                .build();
                userRepository.save(user);

                LoginRequest wrongReq = new LoginRequest(email, "wrong-pass");

                // 5 consecutive failed attempts
                for (int i = 1; i <= 5; i++) {
                        mockMvc.perform(post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(wrongReq)))
                                        .andExpect(status().isUnauthorized())
                                        .andExpect(jsonPath("$.error.code").value("invalid_credentials"));
                }

                User lockedUser = userRepository.findByEmail(email).orElseThrow();
                assertThat(lockedUser.getLockedUntil()).isNotNull();
                assertThat(lockedUser.getLockedUntil()).isAfter(Instant.now());

                // 6th attempt (even with any password) returns 401 invalid_credentials without
                // leaking lock status
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(wrongReq)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.error.code").value("invalid_credentials"));
        }

        @Test
        void shouldShortenDemoExpiryOnFirstLogin() throws Exception {
                // Mint demo credential with 14d initial expiry
                String adminToken = obtainAdminToken();
                CredentialIssueRequest issueReq = new CredentialIssueRequest("First Login Test");

                MvcResult issueResult = mockMvc.perform(post("/api/v1/admin/credentials")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(issueReq)))
                                .andExpect(status().isCreated())
                                .andReturn();

                CredentialIssuedResponse issued = objectMapper.readValue(
                                issueResult.getResponse().getContentAsString(),
                                CredentialIssuedResponse.class);

                User beforeLogin = userRepository.findById(issued.user().id()).orElseThrow();
                assertThat(beforeLogin.getLastLoginAt()).isNull();
                Instant initialExpiry = beforeLogin.getExpiresAt();
                assertThat(initialExpiry).isAfter(Instant.now().plus(13, ChronoUnit.DAYS));

                // First login with the generated password
                LoginRequest loginReq = new LoginRequest(issued.user().email(), issued.password());
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginReq)))
                                .andExpect(status().isOk());

                User afterLogin = userRepository.findById(issued.user().id()).orElseThrow();
                assertThat(afterLogin.getLastLoginAt()).isNotNull();
                assertThat(afterLogin.getExpiresAt()).isBefore(Instant.now().plus(25, ChronoUnit.HOURS));
                assertThat(afterLogin.getExpiresAt()).isBefore(initialExpiry);
        }

        @Test
        void shouldRejectExpiredAccountWithCredentialsExpiredAndSupportEmail() throws Exception {
                String adminToken = obtainAdminToken();
                CredentialIssueRequest issueReq = new CredentialIssueRequest("Expiry Test");

                MvcResult issueResult = mockMvc.perform(post("/api/v1/admin/credentials")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(issueReq)))
                                .andExpect(status().isCreated())
                                .andReturn();

                CredentialIssuedResponse issued = objectMapper.readValue(
                                issueResult.getResponse().getContentAsString(),
                                CredentialIssuedResponse.class);

                // Force expiry into the past
                User user = userRepository.findById(issued.user().id()).orElseThrow();
                user.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
                userRepository.save(user);

                // 1. Login fails with credentials_expired
                LoginRequest loginReq = new LoginRequest(issued.user().email(), issued.password());
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginReq)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.error.code").value("credentials_expired"))
                                .andExpect(jsonPath("$.error.message")
                                                .value(org.hamcrest.Matchers.containsString(appProps.supportEmail())));

                // 2. Existing active bearer token is rejected per-request with
                // credentials_expired
                String liveToken = tokenService.generateAccessToken(user.getId().toString(), user.getEmail(),
                                user.getRole());
                mockMvc.perform(get("/api/v1/auth/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + liveToken))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.error.code").value("credentials_expired"))
                                .andExpect(jsonPath("$.error.message")
                                                .value(org.hamcrest.Matchers.containsString(appProps.supportEmail())));
        }

        @Test
        void shouldRejectDisabledAccountWithCredentialsDisabled() throws Exception {
                String adminToken = obtainAdminToken();
                CredentialIssueRequest issueReq = new CredentialIssueRequest("Disable Test");

                MvcResult issueResult = mockMvc.perform(post("/api/v1/admin/credentials")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(issueReq)))
                                .andExpect(status().isCreated())
                                .andReturn();

                CredentialIssuedResponse issued = objectMapper.readValue(
                                issueResult.getResponse().getContentAsString(),
                                CredentialIssuedResponse.class);

                User user = userRepository.findById(issued.user().id()).orElseThrow();
                user.setDisabledAt(Instant.now());
                userRepository.save(user);

                // Login fails with credentials_disabled
                LoginRequest loginReq = new LoginRequest(issued.user().email(), issued.password());
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginReq)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.error.code").value("credentials_disabled"));

                // Protected request with valid token fails with credentials_disabled
                String liveToken = tokenService.generateAccessToken(user.getId().toString(), user.getEmail(),
                                user.getRole());
                mockMvc.perform(get("/api/v1/auth/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + liveToken))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.error.code").value("credentials_disabled"));
        }

        @Test
        void shouldIssueNewDemoCredentialsWithAutoIncrementEmailAndReadablePassword() throws Exception {
                String adminToken = obtainAdminToken();

                CredentialIssueRequest req1 = new CredentialIssueRequest("Candidate Alpha");
                MvcResult res1 = mockMvc.perform(post("/api/v1/admin/credentials")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req1)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.user.email")
                                                .value(org.hamcrest.Matchers.matchesPattern("demo-\\d{2}@apex\\.in")))
                                .andExpect(jsonPath("$.password").value(
                                                org.hamcrest.Matchers.matchesPattern("[a-z]+-[a-z]+-[a-z]+-\\d{2}")))
                                .andReturn();

                CredentialIssuedResponse cred1 = objectMapper.readValue(res1.getResponse().getContentAsString(),
                                CredentialIssuedResponse.class);

                CredentialIssueRequest req2 = new CredentialIssueRequest("Candidate Beta");
                MvcResult res2 = mockMvc.perform(post("/api/v1/admin/credentials")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req2)))
                                .andExpect(status().isCreated())
                                .andReturn();

                CredentialIssuedResponse cred2 = objectMapper.readValue(res2.getResponse().getContentAsString(),
                                CredentialIssuedResponse.class);

                // Auto-incremented email verification
                assertThat(cred1.user().email()).isNotEqualTo(cred2.user().email());
        }

        @Test
        void shouldListCredentialsExcludingSuperAdmin() throws Exception {
                String adminToken = obtainAdminToken();

                MvcResult result = mockMvc.perform(get("/api/v1/admin/credentials")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andReturn();

                CredentialRowResponse[] rows = objectMapper.readValue(
                                result.getResponse().getContentAsString(),
                                CredentialRowResponse[].class);

                for (CredentialRowResponse row : rows) {
                        assertThat(row.email()).isNotEqualTo(appProps.admin().email());
                }
        }

        @Test
        void shouldExtendAndToggleDisabledViaAdminPatch() throws Exception {
                String adminToken = obtainAdminToken();

                // Mint credential
                CredentialIssueRequest issueReq = new CredentialIssueRequest("Patch Test");
                MvcResult issueResult = mockMvc.perform(post("/api/v1/admin/credentials")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(issueReq)))
                                .andExpect(status().isCreated())
                                .andReturn();

                CredentialIssuedResponse issued = objectMapper.readValue(
                                issueResult.getResponse().getContentAsString(),
                                CredentialIssuedResponse.class);

                // 1. Extend expiry by 24h
                CredentialUpdateRequest extendReq = new CredentialUpdateRequest(true, null);
                mockMvc.perform(patch("/api/v1/admin/credentials/" + issued.user().id())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(extendReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

                User afterExtend = userRepository.findById(issued.user().id()).orElseThrow();
                assertThat(afterExtend.getExpiresAt()).isAfter(Instant.now().plus(23, ChronoUnit.HOURS));

                // 2. Toggle disabled
                CredentialUpdateRequest disableReq = new CredentialUpdateRequest(null, true);
                mockMvc.perform(patch("/api/v1/admin/credentials/" + issued.user().id())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(disableReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.disabledAt").isNotEmpty());

                User afterDisable = userRepository.findById(issued.user().id()).orElseThrow();
                assertThat(afterDisable.getDisabledAt()).isNotNull();

                // 3. Toggle back to enabled
                CredentialUpdateRequest enableReq = new CredentialUpdateRequest(null, false);
                mockMvc.perform(patch("/api/v1/admin/credentials/" + issued.user().id())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(enableReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.disabledAt").isEmpty());

                User afterEnable = userRepository.findById(issued.user().id()).orElseThrow();
                assertThat(afterEnable.getDisabledAt()).isNull();
        }

        @Test
        void shouldDeleteCredentialAccount() throws Exception {
                String adminToken = obtainAdminToken();

                CredentialIssueRequest issueReq = new CredentialIssueRequest("Delete Test");
                MvcResult issueResult = mockMvc.perform(post("/api/v1/admin/credentials")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(issueReq)))
                                .andExpect(status().isCreated())
                                .andReturn();

                CredentialIssuedResponse issued = objectMapper.readValue(
                                issueResult.getResponse().getContentAsString(),
                                CredentialIssuedResponse.class);

                mockMvc.perform(delete("/api/v1/admin/credentials/" + issued.user().id())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isNoContent());

                assertThat(userRepository.findById(issued.user().id())).isEmpty();
        }

        @Test
        void shouldForbidModifyingOrDeletingSuperAdmin() throws Exception {
                String adminToken = obtainAdminToken();
                User admin = userRepository.findByEmail(appProps.admin().email()).orElseThrow();

                // Attempt PATCH on super admin
                CredentialUpdateRequest updateReq = new CredentialUpdateRequest(true, true);
                mockMvc.perform(patch("/api/v1/admin/credentials/" + admin.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateReq)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.error.code").value("forbidden"));

                // Attempt DELETE on super admin
                mockMvc.perform(delete("/api/v1/admin/credentials/" + admin.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.error.code").value("forbidden"));
        }

        @Test
        void shouldDenyDemoUserAccessToAdminEndpoints() throws Exception {
                String demoToken = obtainDemoToken();

                mockMvc.perform(get("/api/v1/admin/credentials")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + demoToken))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.error.code").value("forbidden"));

                mockMvc.perform(post("/api/v1/admin/reseed")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + demoToken))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.error.code").value("forbidden"));
        }

        @Test
        void shouldReseedDemoDataIdempotently() throws Exception {
                String adminToken = obtainAdminToken();

                // 1st reseed: creates seed rows
                mockMvc.perform(post("/api/v1/admin/reseed")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.created").exists());

                // 2nd reseed: idempotent, creates 0 rows
                mockMvc.perform(post("/api/v1/admin/reseed")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.created.notes").value(0))
                                .andExpect(jsonPath("$.created.blocks").value(0))
                                .andExpect(jsonPath("$.created.reminders").value(0))
                                .andExpect(jsonPath("$.created.tags").value(0));
        }

        @Test
        void shouldRotateRefreshCookie() throws Exception {
                LoginRequest loginReq = new LoginRequest(appProps.admin().email(), appProps.admin().password());
                MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginReq)))
                                .andExpect(status().isOk())
                                .andReturn();

                String rawCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                String refreshToken = extractTokenFromSetCookie(rawCookie);

                // Rotate token
                MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                                .cookie(new Cookie("rtt_refresh", refreshToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                                .andReturn();

                String newRawCookie = refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                String newRefreshToken = extractTokenFromSetCookie(newRawCookie);

                assertThat(newRefreshToken).isNotBlank();
                assertThat(newRefreshToken).isNotEqualTo(refreshToken);
        }

        @Test
        void shouldClearRefreshCookieOnLogout() throws Exception {
                MvcResult result = mockMvc.perform(post("/api/v1/auth/logout"))
                                .andExpect(status().isNoContent())
                                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                                .andReturn();

                String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                assertThat(setCookie).contains("Max-Age=0");
        }

        @Test
        void shouldRateLimitAfter10RequestsPerMinute() throws Exception {
                String testIp = "192.168.1.100";
                LoginRequest badReq = new LoginRequest("wrong@apex.in", "bad");

                for (int i = 0; i < 10; i++) {
                        mockMvc.perform(post("/api/v1/auth/login")
                                        .header("X-Forwarded-For", testIp)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(badReq)))
                                        .andExpect(status().isUnauthorized());
                }

                // 11th request triggers rate limiter
                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-Forwarded-For", testIp)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(badReq)))
                                .andExpect(status().isTooManyRequests())
                                .andExpect(jsonPath("$.error.code").value("rate_limited"));
        }

        @Test
        void shouldIssueCustomCredentialAndLoginSuccessfully() throws Exception {
                String adminToken = obtainAdminToken();

                CredentialIssueRequest customReq = new CredentialIssueRequest(
                                "intervie@tcs.com",
                                "TcsSecret123!",
                                "TCS Technical Interviewer",
                                30);

                MvcResult issueResult = mockMvc.perform(post("/api/v1/admin/credentials")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customReq)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.user.email").value("intervie@tcs.com"))
                                .andExpect(jsonPath("$.password").value("TcsSecret123!"))
                                .andReturn();

                CredentialIssuedResponse issued = objectMapper.readValue(
                                issueResult.getResponse().getContentAsString(),
                                CredentialIssuedResponse.class);

                assertThat(issued.user().email()).isEqualTo("intervie@tcs.com");

                // Attempting to issue the same email again should return 409 Conflict
                mockMvc.perform(post("/api/v1/admin/credentials")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customReq)))
                                .andExpect(status().isConflict());

                // Login with custom credentials
                LoginRequest loginReq = new LoginRequest("intervie@tcs.com", "TcsSecret123!");
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.user.email").value("intervie@tcs.com"))
                                .andExpect(jsonPath("$.accessToken").isNotEmpty());

                // Custom account retains 30 days validity, not shortened to 24h
                User customUser = userRepository.findByEmail("intervie@tcs.com").orElseThrow();
                assertThat(customUser.getLastLoginAt()).isNotNull();
                assertThat(customUser.getExpiresAt()).isAfter(Instant.now().plus(28, ChronoUnit.DAYS));
        }

        private String obtainAdminToken() throws Exception {
                LoginRequest request = new LoginRequest(appProps.admin().email(), appProps.admin().password());
                MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn();

                SessionResponse session = objectMapper.readValue(result.getResponse().getContentAsString(),
                                SessionResponse.class);
                return session.accessToken();
        }

        private String obtainDemoToken() throws Exception {
                LoginRequest request = new LoginRequest(appProps.demo().email(), appProps.demo().password());
                MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn();

                SessionResponse session = objectMapper.readValue(result.getResponse().getContentAsString(),
                                SessionResponse.class);
                return session.accessToken();
        }

        private String extractTokenFromSetCookie(String setCookie) {
                for (String part : setCookie.split(";")) {
                        String trimmed = part.trim();
                        if (trimmed.startsWith("rtt_refresh=")) {
                                return trimmed.substring("rtt_refresh=".length());
                        }
                }
                return null;
        }
}
