package com.ewallet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A user's wallet in a specific currency.
 *
 * <p><b>Optimistic locking:</b> {@code @Version} on {@code version} ensures
 * concurrent fund transfers are serialised safely. Any competing write causes
 * Hibernate to throw {@link jakarta.persistence.OptimisticLockException}, which
 * the service layer catches and retries with back-off.
 *
 * <p><b>Invariant:</b> {@code balance >= 0} is enforced both at the DB level
 * (CHECK constraint in the migration) and in the service before every debit.
 */
@Entity
@Table(
    name = "wallets",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_wallet_user_currency",
        columnNames = {"user_id", "currency_code"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Hibernate optimistic-lock version. */
    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── business operations ─────────────────────────────────────────────────

    /**
     * Credits this wallet.
     *
     * @param amount must be positive
     */
    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance = this.balance.add(amount);
    }

    /**
     * Debits this wallet.
     *
     * @param amount must be positive and ≤ current balance
     * @throws com.ewallet.exception.InsufficientFundsException if balance is too low
     */
    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (this.balance.compareTo(amount) < 0) {
            throw new com.ewallet.exception.InsufficientFundsException(
                "Insufficient funds: balance=" + this.balance + ", requested=" + amount);
        }
        this.balance = this.balance.subtract(amount);
    }
}
