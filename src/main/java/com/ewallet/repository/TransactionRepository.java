package com.ewallet.repository;

import com.ewallet.entity.Transaction;
import com.ewallet.entity.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Page<Transaction> findBySourceWalletIdOrTargetWalletId(
            UUID sourceWalletId, UUID targetWalletId, Pageable pageable);

    /** Sum of all completed outgoing transfers in a rolling 24-hour window. */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM   Transaction t
        WHERE  t.sourceWallet.id = :walletId
        AND    t.status          = :status
        AND    t.createdAt      >= :since
        """)
    BigDecimal sumCompletedBySourceWalletAndStatusSince(
            @Param("walletId") UUID walletId,
            @Param("status") TransactionStatus status,
            @Param("since") Instant since);

    Page<Transaction> findByInitiatedByIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
