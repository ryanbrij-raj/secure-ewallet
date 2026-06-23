package com.ewallet.dto.response;

import com.ewallet.entity.enums.TransactionStatus;
import com.ewallet.entity.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class TransactionResponse {
    private UUID id;
    private String idempotencyKey;
    private TransactionType type;
    private TransactionStatus status;
    private UUID sourceWalletId;
    private UUID targetWalletId;
    private BigDecimal amount;
    private String currencyCode;
    private BigDecimal feeAmount;
    private String description;
    private Instant createdAt;
    private Instant completedAt;
}
