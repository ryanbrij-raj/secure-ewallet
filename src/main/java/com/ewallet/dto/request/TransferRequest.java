package com.ewallet.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferRequest {

    /** Client-generated UUID; guarantees exactly-once execution. */
    @NotBlank
    private String idempotencyKey;

    @NotNull
    private UUID targetUserId;

    @NotBlank @Size(min = 3, max = 3)
    private String currencyCode;

    @NotNull @DecimalMin(value = "0.01")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @Size(max = 255)
    private String description;
}
