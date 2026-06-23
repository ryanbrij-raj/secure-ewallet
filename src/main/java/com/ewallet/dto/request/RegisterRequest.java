package com.ewallet.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

// ── Auth ─────────────────────────────────────────────────────────────────────

@Data
public class RegisterRequest {
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 8, max = 72)
    private String password;
    @NotBlank @Size(min = 2, max = 100)
    private String fullName;
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Invalid phone number")
    private String phone;
}
