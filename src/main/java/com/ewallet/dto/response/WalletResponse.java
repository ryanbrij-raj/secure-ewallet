package com.ewallet.dto.response;

import com.ewallet.entity.enums.TransactionStatus;
import com.ewallet.entity.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class WalletResponse {
    private UUID id;
    private String currencyCode;
    private BigDecimal balance;
    private boolean active;
    private Instant createdAt;
}
