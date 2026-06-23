package com.ewallet.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DepositRequest {
    @NotBlank
    private String idempotencyKey;
    @NotBlank @Size(min = 3, max = 3)
    private String currencyCode;
    @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;
    @Size(max = 255)
    private String description;
}
