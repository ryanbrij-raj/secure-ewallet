package com.ewallet.service.impl;

import com.ewallet.dto.request.DepositRequest;
import com.ewallet.dto.request.TransferRequest;
import com.ewallet.dto.response.TransactionResponse;
import com.ewallet.entity.Transaction;
import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import com.ewallet.entity.enums.TransactionStatus;
import com.ewallet.entity.enums.TransactionType;
import com.ewallet.exception.DailyLimitExceededException;
import com.ewallet.exception.DuplicateIdempotencyKeyException;
import com.ewallet.exception.InsufficientFundsException;
import com.ewallet.exception.ResourceNotFoundException;
import com.ewallet.repository.TransactionRepository;
import com.ewallet.repository.UserRepository;
import com.ewallet.repository.WalletRepository;
import com.ewallet.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Core financial engine.
 *
 * <h3>Concurrency strategy</h3>
 * <ul>
 *   <li><b>Pessimistic write lock</b> — {@code SELECT FOR UPDATE} on both wallets
 *       before mutating balances. Locks are acquired in a canonical order
 *       (min UUID first) to prevent deadlocks when two concurrent transfers
 *       involve the same pair of wallets.</li>
 *   <li><b>Isolation level REPEATABLE_READ</b> — prevents phantom reads so the
 *       daily-limit sum query sees a consistent snapshot.</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * <p>Every mutating endpoint requires a client-supplied {@code idempotencyKey}.
 * A UNIQUE constraint on the column is the final safety net; the service layer
 * also checks first and returns the existing transaction response so clients
 * get a 200 rather than a 409 on duplicate submissions.
 *
 * <h3>Daily transfer limit</h3>
 * <p>Configurable via {@code wallet.max-daily-transfer}. The check is inside the
 * same serialised transaction as the debit to avoid TOCTOU races.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl {

    private final TransactionRepository transactionRepository;
    private final WalletRepository      walletRepository;
    private final UserRepository        userRepository;
    private final AuditService          auditService;

    @Value("${wallet.max-daily-transfer}")
    private BigDecimal maxDailyTransfer;

    // ── Deposit ──────────────────────────────────────────────────────────────

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse deposit(UUID userId, DepositRequest req) {
        // Idempotency: return existing result if key was already processed
        var existing = transactionRepository.findByIdempotencyKey(req.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent deposit replay for key={}", req.getIdempotencyKey());
            return toResponse(existing.get());
        }

        Wallet wallet = walletRepository
                .findByIdWithLock(resolveWalletId(userId, req.getCurrencyCode()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No " + req.getCurrencyCode() + " wallet found"));

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Transaction tx = Transaction.builder()
                .idempotencyKey(req.getIdempotencyKey())
                .type(TransactionType.DEPOSIT)
                .targetWallet(wallet)
                .amount(req.getAmount())
                .currencyCode(req.getCurrencyCode())
                .description(req.getDescription())
                .initiatedBy(actor)
                .build();

        wallet.credit(req.getAmount());
        walletRepository.save(wallet);

        tx.markCompleted();
        tx = transactionRepository.save(tx);

        auditService.log("DEPOSIT_COMPLETED", userId, "Transaction",
                tx.getId().toString(), null,
                Map.of("amount", req.getAmount(), "currency", req.getCurrencyCode()));

        return toResponse(tx);
    }

    // ── Transfer ─────────────────────────────────────────────────────────────

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse transfer(UUID initiatorId, TransferRequest req) {
        var existing = transactionRepository.findByIdempotencyKey(req.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent transfer replay for key={}", req.getIdempotencyKey());
            return toResponse(existing.get());
        }

        // Resolve wallets
        UUID sourceWalletId = resolveWalletId(initiatorId, req.getCurrencyCode());
        UUID targetWalletId = resolveWalletId(req.getTargetUserId(), req.getCurrencyCode());

        if (sourceWalletId.equals(targetWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }

        // Acquire locks in deterministic order to prevent deadlock
        UUID firstLock  = sourceWalletId.compareTo(targetWalletId) < 0 ? sourceWalletId : targetWalletId;
        UUID secondLock = sourceWalletId.compareTo(targetWalletId) < 0 ? targetWalletId : sourceWalletId;

        Wallet first  = walletRepository.findByIdWithLock(firstLock)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        Wallet second = walletRepository.findByIdWithLock(secondLock)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        Wallet sourceWallet = first.getId().equals(sourceWalletId) ? first : second;
        Wallet targetWallet = first.getId().equals(targetWalletId) ? first : second;

        // Daily limit check
        checkDailyLimit(sourceWalletId, req.getAmount());

        User actor = userRepository.findById(initiatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Transaction tx = Transaction.builder()
                .idempotencyKey(req.getIdempotencyKey())
                .type(TransactionType.TRANSFER)
                .sourceWallet(sourceWallet)
                .targetWallet(targetWallet)
                .amount(req.getAmount())
                .currencyCode(req.getCurrencyCode())
                .description(req.getDescription())
                .initiatedBy(actor)
                .build();

        try {
            sourceWallet.debit(req.getAmount());
            targetWallet.credit(req.getAmount());

            walletRepository.save(sourceWallet);
            walletRepository.save(targetWallet);

            tx.markCompleted();
        } catch (InsufficientFundsException e) {
            tx.markFailed(e.getMessage());
            transactionRepository.save(tx);
            throw e;
        }

        tx = transactionRepository.save(tx);

        auditService.log("TRANSFER_COMPLETED", initiatorId, "Transaction",
                tx.getId().toString(), null,
                Map.of("amount", req.getAmount(), "currency", req.getCurrencyCode(),
                        "to_user", req.getTargetUserId()));

        return toResponse(tx);
    }

    // ── Ledger query ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getHistory(UUID userId, Pageable pageable) {
        return transactionRepository
                .findByInitiatedByIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UUID resolveWalletId(UUID userId, String currency) {
        return walletRepository
                .findByUserIdAndCurrencyCode(userId, currency.toUpperCase())
                .map(Wallet::getId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No " + currency + " wallet for user " + userId));
    }

    private void checkDailyLimit(UUID sourceWalletId, BigDecimal requestedAmount) {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        BigDecimal alreadyTransferred = transactionRepository
                .sumCompletedBySourceWalletAndStatusSince(
                        sourceWalletId, TransactionStatus.COMPLETED, since);

        if (alreadyTransferred.add(requestedAmount).compareTo(maxDailyTransfer) > 0) {
            throw new DailyLimitExceededException(
                    "Daily transfer limit of " + maxDailyTransfer + " would be exceeded. " +
                    "Already transferred: " + alreadyTransferred);
        }
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .idempotencyKey(tx.getIdempotencyKey())
                .type(tx.getType())
                .status(tx.getStatus())
                .sourceWalletId(tx.getSourceWallet() != null ? tx.getSourceWallet().getId() : null)
                .targetWalletId(tx.getTargetWallet() != null ? tx.getTargetWallet().getId() : null)
                .amount(tx.getAmount())
                .currencyCode(tx.getCurrencyCode())
                .feeAmount(tx.getFeeAmount())
                .description(tx.getDescription())
                .createdAt(tx.getCreatedAt())
                .completedAt(tx.getCompletedAt())
                .build();
    }
}
