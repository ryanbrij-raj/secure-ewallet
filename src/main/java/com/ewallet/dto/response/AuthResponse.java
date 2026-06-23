package com.ewallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// ── Auth ─────────────────────────────────────────────────────────────────────

@Data @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresInSeconds;
    private UserResponse user;
}
