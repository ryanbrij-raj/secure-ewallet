package com.ewallet.service.impl;

import com.ewallet.dto.request.LoginRequest;
import com.ewallet.dto.request.RegisterRequest;
import com.ewallet.dto.response.AuthResponse;
import com.ewallet.dto.response.UserResponse;
import com.ewallet.entity.RefreshToken;
import com.ewallet.entity.User;
import com.ewallet.exception.AccountLockedException;
import com.ewallet.exception.DuplicateEmailException;
import com.ewallet.exception.InvalidCredentialsException;
import com.ewallet.exception.ResourceNotFoundException;
import com.ewallet.repository.RefreshTokenRepository;
import com.ewallet.repository.UserRepository;
import com.ewallet.security.JwtUtil;
import com.ewallet.service.AuditService;
import com.ewallet.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Handles user registration, login, and token lifecycle.
 *
 * <p><b>Refresh token security:</b>
 * <ul>
 *   <li>Raw token is a random UUID — high entropy, unpredictable.</li>
 *   <li>Only the SHA-256 hash is stored in the DB; if the DB is compromised,
 *       attackers cannot use the hashes directly.</li>
 *   <li>All existing refresh tokens are revoked on logout or password change.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl {

    private final UserRepository      userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder     passwordEncoder;
    private final JwtUtil             jwtUtil;
    private final EncryptionUtil      encryptionUtil;
    private final AuditService        auditService;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;
    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    // ── Registration ─────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String emailHash = encryptionUtil.hmac(req.getEmail());

        if (userRepository.existsByEmailHash(emailHash)) {
            throw new DuplicateEmailException("Email is already registered");
        }

        User user = User.builder()
                .email(encryptionUtil.encrypt(req.getEmail()))
                .emailHash(emailHash)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .fullName(encryptionUtil.encrypt(req.getFullName()))
                .phone(req.getPhone() != null ? encryptionUtil.encrypt(req.getPhone()) : null)
                .build();

        user = userRepository.save(user);

        auditService.log("USER_REGISTERED", user.getId(), "User", user.getId().toString(),
                null, Map.of("email_hash", emailHash));

        return buildAuthResponse(user);
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest req, String ipAddress) {
        String emailHash = encryptionUtil.hmac(req.getEmail());

        User user = userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (user.isLocked()) {
            auditService.log("LOGIN_BLOCKED_LOCKED", user.getId(), "User",
                    user.getId().toString(), ipAddress, Map.of());
            throw new AccountLockedException("Account locked after too many failed attempts. Contact support.");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            userRepository.incrementFailedLogins(user.getId());
            auditService.log("LOGIN_FAILED", user.getId(), "User",
                    user.getId().toString(), ipAddress, Map.of("attempts", user.getFailedLoginAttempts() + 1));
            throw new InvalidCredentialsException("Invalid email or password");
        }

        userRepository.recordSuccessfulLogin(user.getId(), Instant.now());
        auditService.log("LOGIN_SUCCESS", user.getId(), "User",
                user.getId().toString(), ipAddress, Map.of());

        return buildAuthResponse(user);
    }

    // ── Token refresh ────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse refreshTokens(String rawRefreshToken) {
        String tokenHash = sha256Hex(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (!stored.isValid()) {
            // Possible token theft — revoke all tokens for this user
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId());
            throw new InvalidCredentialsException("Refresh token expired or revoked");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return buildAuthResponse(stored.getUser());
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId);
        auditService.log("LOGOUT", userId, "User", userId.toString(), null, Map.of());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtUtil.generateAccessToken(user.getId(), user.getRole());
        String rawRefresh   = UUID.randomUUID().toString();
        String refreshHash  = sha256Hex(rawRefresh);

        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshHash)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpiryMs))
                .build();
        refreshTokenRepository.save(rt);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefresh)
                .expiresInSeconds(accessTokenExpiryMs / 1000)
                .user(toUserResponse(user))
                .build();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(encryptionUtil.decrypt(user.getEmail()))
                .fullName(encryptionUtil.decrypt(user.getFullName()))
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
