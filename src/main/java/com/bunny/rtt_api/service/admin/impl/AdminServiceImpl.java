package com.bunny.rtt_api.service.admin.impl;

import com.bunny.rtt_api.config.AppProps;
import com.bunny.rtt_api.dto.request.CredentialIssueRequest;
import com.bunny.rtt_api.dto.request.CredentialUpdateRequest;
import com.bunny.rtt_api.dto.response.CredentialIssuedResponse;
import com.bunny.rtt_api.dto.response.CredentialRowResponse;
import com.bunny.rtt_api.dto.response.ReseedResponse;
import com.bunny.rtt_api.entity.User;
import com.bunny.rtt_api.exception.AppException;
import com.bunny.rtt_api.exception.ErrorCode;
import com.bunny.rtt_api.mapper.UserMapper;
import com.bunny.rtt_api.repository.UserRepository;
import com.bunny.rtt_api.service.admin.AdminService;
import com.bunny.rtt_api.service.common.DemoDataService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AdminServiceImpl implements AdminService {

    private static final List<String> WORDS = List.of(
            "amber", "breeze", "cedar", "delta", "echo", "frost", "harbor", "haven",
            "matrix", "meadow", "nexus", "orbit", "pulse", "quarry", "river", "silver",
            "solar", "stride", "summit", "swift", "timber", "valley", "vortex", "zenith"
    );

    private static final Pattern DEMO_EMAIL_PATTERN = Pattern.compile("^demo-(\\d+)@apex\\.in$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final DemoDataService demoDataService;
    private final AppProps appProps;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            DemoDataService demoDataService,
            AppProps appProps
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.demoDataService = demoDataService;
        this.appProps = appProps;
    }

    @Override
    @Transactional
    public CredentialIssuedResponse issueCredential(CredentialIssueRequest request) {
        String email;
        if (request != null && request.email() != null && !request.email().isBlank()) {
            email = request.email().trim().toLowerCase();
            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Invalid email address format: " + email);
            }
            if (userRepository.existsByEmail(email)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.CONFLICT, "A user with email '" + email + "' already exists");
            }
        } else {
            email = deriveNextDemoEmail();
        }

        String rawPassword;
        if (request != null && request.password() != null && !request.password().isBlank()) {
            rawPassword = request.password().trim();
            if (rawPassword.length() < 6) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Password must be at least 6 characters long");
            }
        } else {
            rawPassword = generateReadablePassword();
        }

        String passwordHash = passwordEncoder.encode(rawPassword);

        String label = request != null ? request.label() : null;
        String name;
        if (label != null && !label.isBlank()) {
            name = label.trim();
        } else if (email.startsWith("demo-")) {
            name = "Demo User";
        } else {
            name = email.split("@")[0];
        }

        Instant now = Instant.now();
        Instant expiresAt;
        if (request != null && request.daysValid() != null) {
            if (request.daysValid() <= 0) {
                expiresAt = null; // Permanent
            } else {
                expiresAt = now.plus(request.daysValid(), ChronoUnit.DAYS);
            }
        } else {
            expiresAt = now.plus(14, ChronoUnit.DAYS);
        }

        User user = User.builder()
                .email(email)
                .name(name)
                .label(label)
                .passwordHash(passwordHash)
                .role("demo")
                .timezone("Asia/Kolkata")
                .expiresAt(expiresAt)
                .attemptCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        User savedUser = userRepository.save(user);

        return new CredentialIssuedResponse(userMapper.toUserResponse(savedUser), rawPassword);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CredentialRowResponse> listCredentials() {
        String adminEmail = appProps.admin().email();
        List<User> users = userRepository.findAllByRoleNotOrderByCreatedAtDesc("admin");

        return users.stream()
                .filter(u -> !u.getEmail().equalsIgnoreCase(adminEmail))
                .map(userMapper::toCredentialRow)
                .toList();
    }

    @Override
    @Transactional
    public CredentialRowResponse updateCredential(UUID id, CredentialUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Credential account not found"));

        if ("admin".equalsIgnoreCase(user.getRole()) || user.getEmail().equalsIgnoreCase(appProps.admin().email())) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Cannot modify super admin account");
        }

        if (request != null) {
            if (Boolean.TRUE.equals(request.extend())) {
                user.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
            }
            if (request.disabled() != null) {
                user.setDisabledAt(request.disabled() ? Instant.now() : null);
            }
        }

        User saved = userRepository.save(user);
        return userMapper.toCredentialRow(saved);
    }

    @Override
    @Transactional
    public void deleteCredential(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Credential account not found"));

        if ("admin".equalsIgnoreCase(user.getRole()) || user.getEmail().equalsIgnoreCase(appProps.admin().email())) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Cannot delete super admin account");
        }

        // Revoke the account only; workspace notes remain intact
        userRepository.delete(user);
    }

    @Override
    @Transactional
    public ReseedResponse reseed() {
        DemoDataService.ReseedResult result = demoDataService.seedDemoData();
        return new ReseedResponse(new ReseedResponse.ReseedCounts(
                result.notes(),
                result.blocks(),
                result.reminders(),
                result.tags()
        ));
    }

    private String deriveNextDemoEmail() {
        List<String> emails = userRepository.findEmailsByPattern("demo-%@apex.in");
        int maxCounter = 0;

        for (String email : emails) {
            Matcher matcher = DEMO_EMAIL_PATTERN.matcher(email.toLowerCase());
            if (matcher.matches()) {
                try {
                    int num = Integer.parseInt(matcher.group(1));
                    if (num > maxCounter) {
                        maxCounter = num;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return String.format("demo-%02d@apex.in", maxCounter + 1);
    }

    private String generateReadablePassword() {
        List<String> shuffled = new ArrayList<>(WORDS);
        Collections.shuffle(shuffled, secureRandom);
        String w1 = shuffled.get(0);
        String w2 = shuffled.get(1);
        String w3 = shuffled.get(2);
        int num = 10 + secureRandom.nextInt(90); // 10..99
        return String.format("%s-%s-%s-%02d", w1, w2, w3, num);
    }
}
