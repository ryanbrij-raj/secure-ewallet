package com.ewallet.controller;

import com.ewallet.dto.request.LoginRequest;
import com.ewallet.dto.request.RegisterRequest;
import com.ewallet.dto.response.AuthResponse;
import com.ewallet.service.impl.AuthServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Authentication endpoints.
 *
 * <p>All endpoints are public (configured in {@link com.ewallet.config.SecurityConfig}).
 * Sensitive operations like logout require a valid access token so the principal
 * is available via {@code @AuthenticationPrincipal}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthServiceImpl authService;

    /** Register a new user and return tokens immediately. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    /** Authenticate and receive access + refresh tokens. */
    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpReq) {
        return authService.login(req, httpReq.getRemoteAddr());
    }

    /** Exchange a valid refresh token for a fresh token pair. */
    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken is required");
        }
        return authService.refreshTokens(refreshToken);
    }

    /** Revoke all refresh tokens for the authenticated user. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal UUID userId) {
        authService.logout(userId);
    }
}
