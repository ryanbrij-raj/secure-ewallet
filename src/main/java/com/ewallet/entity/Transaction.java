package com.ewallet.entity;

import com.ewallet.entity.enums.TransactionStatus;
import com.ewallet.entity.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable ledger entry.
 *
 * <p>Transactions are <b>never deleted or updated</b>. Corrections are modelled
 * as new {@code REVERSAL} transactions that cancel a prior entry. This gives a
 * full, tamper-evident history that satisfies typical financial audit requirements.
 *
 * <p>{@code idempotencyKey} is a client-supplied UUID stored with a UNIQUE
 * constraint. Re-submitting the same key returns the original response without
 * executing a second transfer.
 */
@Entity
@Table(name = "transactions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_wallet_id")
    private Wallet sourceWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_wallet_id")
    private Wallet targetWallet;

    /** Amount in the *target* currency (= {@code currencyCode}). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** Amount in the *source* currency (for FX conversions). */
    @Column(name = "source_amount", precision = 19, scale = 4)
    private BigDecimal sourceAmount;

    @Column(name = "source_currency", length = 3)
    private String sourceCurrency;

    @Column(name = "exchange_rate", precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column
    private String description;

    /** Arbitrary key-value metadata (stored as JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiated_by", nullable = false)
    private User initiatedBy;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // ── state-machine helpers ───────────────────────────────────────────────

    public void markCompleted() {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING transactions can be completed");
        }
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markFailed(String reason) {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING transactions can be failed");
        }
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }
}
