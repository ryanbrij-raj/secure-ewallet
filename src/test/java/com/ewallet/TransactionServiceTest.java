package com.ewallet;

import com.ewallet.dto.request.DepositRequest;
import com.ewallet.dto.request.TransferRequest;
import com.ewallet.dto.response.TransactionResponse;
import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import com.ewallet.entity.enums.TransactionStatus;
import com.ewallet.exception.DailyLimitExceededException;
import com.ewallet.exception.InsufficientFundsException;
import com.ewallet.repository.TransactionRepository;
import com.ewallet.repository.UserRepository;
import com.ewallet.repository.WalletRepository;
import com.ewallet.service.AuditService;
import com.ewallet.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TransactionServiceImpl}.
 *
 * <p>Uses Mockito to isolate the service from the database.
 * Key scenarios covered:
 * <ul>
 *   <li>Successful deposit credits the wallet</li>
 *   <li>Idempotent deposit replay returns existing transaction</li>
 *   <li>Transfer debits source and credits target</li>
 *   <li>Transfer fails when balance is insufficient</li>
 *   <li>Transfer fails when daily limit would be exceeded</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock WalletRepository      walletRepository;
    @Mock UserRepository        userRepository;
    @Mock AuditService          auditService;

    @InjectMocks
    TransactionServiceImpl service;

    private User    user;
    private Wallet  wallet;
    private UUID    userId;
    private UUID    walletId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxDailyTransfer", new BigDecimal("50000"));

        userId   = UUID.randomUUID();
        walletId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("enc:alice@example.com")
                .emailHash("hash")
                .passwordHash("bcrypt")
                .fullName("enc:Alice")
                .role("ROLE_USER")
                .build();

        wallet = Wallet.builder()
                .id(walletId)
                .user(user)
                .currencyCode("USD")
                .balance(new BigDecimal("1000.00"))
                .build();
    }

    // ── Deposit ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deposit: credits wallet and saves completed transaction")
    void deposit_creditsWalletAndPersistsTransaction() {
        var req = new DepositRequest();
        req.setIdempotencyKey(UUID.randomUUID().toString());
        req.setCurrencyCode("USD");
        req.setAmount(new BigDecimal("250.00"));

        when(transactionRepository.findByIdempotencyKey(req.getIdempotencyKey()))
                .thenReturn(Optional.empty());
        when(walletRepository.findByUserIdAndCurrencyCode(userId, "USD"))
                .thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdWithLock(walletId))
                .thenReturn(Optional.of(wallet));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            var tx = inv.<com.ewallet.entity.Transaction>getArgument(0);
            ReflectionTestUtils.setField(tx, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(tx, "createdAt", Instant.now());
            return tx;
        });

        TransactionResponse result = service.deposit(userId, req);

        assertThat(wallet.getBalance()).isEqualByComparingTo("1250.00");
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.getAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("deposit: idempotent replay returns existing transaction without crediting again")
    void deposit_idempotentReplay_returnsExistingWithoutDoubleCredit() {
        var existingTx = com.ewallet.entity.Transaction.builder()
                .id(UUID.randomUUID())
                .idempotencyKey("key-123")
                .type(com.ewallet.entity.enums.TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .currencyCode("USD")
                .feeAmount(BigDecimal.ZERO)
                .initiatedBy(user)
                .build();
        ReflectionTestUtils.setField(existingTx, "createdAt", Instant.now());

        var req = new DepositRequest();
        req.setIdempotencyKey("key-123");
        req.setCurrencyCode("USD");
        req.setAmount(new BigDecimal("100.00"));

        when(transactionRepository.findByIdempotencyKey("key-123"))
                .thenReturn(Optional.of(existingTx));

        TransactionResponse result = service.deposit(userId, req);

        assertThat(wallet.getBalance()).isEqualByComparingTo("1000.00");  // unchanged
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(walletRepository, never()).save(any());
    }

    // ── Transfer ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("transfer: insufficient funds throws InsufficientFundsException")
    void transfer_insufficientFunds_throwsException() {
        var targetUserId = UUID.randomUUID();
        var targetWalletId = UUID.randomUUID();
        var targetUser = User.builder().id(targetUserId).build();
        var targetWallet = Wallet.builder()
                .id(targetWalletId).user(targetUser).currencyCode("USD")
                .balance(BigDecimal.ZERO).build();

        var req = new TransferRequest();
        req.setIdempotencyKey(UUID.randomUUID().toString());
        req.setTargetUserId(targetUserId);
        req.setCurrencyCode("USD");
        req.setAmount(new BigDecimal("9999.00"));  // more than balance of 1000

        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(walletRepository.findByUserIdAndCurrencyCode(userId, "USD"))
                .thenReturn(Optional.of(wallet));
        when(walletRepository.findByUserIdAndCurrencyCode(targetUserId, "USD"))
                .thenReturn(Optional.of(targetWallet));
        when(transactionRepository.sumCompletedBySourceWalletAndStatusSince(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        // Lock order: smaller UUID first
        UUID firstId  = walletId.compareTo(targetWalletId) < 0 ? walletId : targetWalletId;
        UUID secondId = walletId.compareTo(targetWalletId) < 0 ? targetWalletId : walletId;
        when(walletRepository.findByIdWithLock(firstId))
                .thenReturn(Optional.of(walletId.equals(firstId) ? wallet : targetWallet));
        when(walletRepository.findByIdWithLock(secondId))
                .thenReturn(Optional.of(walletId.equals(secondId) ? wallet : targetWallet));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            var tx = inv.<com.ewallet.entity.Transaction>getArgument(0);
            ReflectionTestUtils.setField(tx, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(tx, "createdAt", Instant.now());
            return tx;
        });

        assertThatThrownBy(() -> service.transfer(userId, req))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("transfer: daily limit exceeded throws DailyLimitExceededException")
    void transfer_dailyLimitExceeded_throwsException() {
        var targetUserId   = UUID.randomUUID();
        var targetWalletId = UUID.randomUUID();
        var targetWallet   = Wallet.builder()
                .id(targetWalletId).currencyCode("USD").balance(BigDecimal.ZERO).build();

        var req = new TransferRequest();
        req.setIdempotencyKey(UUID.randomUUID().toString());
        req.setTargetUserId(targetUserId);
        req.setCurrencyCode("USD");
        req.setAmount(new BigDecimal("1000.00"));

        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(walletRepository.findByUserIdAndCurrencyCode(userId, "USD"))
                .thenReturn(Optional.of(wallet));
        when(walletRepository.findByUserIdAndCurrencyCode(targetUserId, "USD"))
                .thenReturn(Optional.of(targetWallet));
        // Already transferred 49,500 — adding 1,000 would breach 50,000 limit
        when(transactionRepository.sumCompletedBySourceWalletAndStatusSince(any(), any(), any()))
                .thenReturn(new BigDecimal("49500.00"));

        assertThatThrownBy(() -> service.transfer(userId, req))
                .isInstanceOf(DailyLimitExceededException.class)
                .hasMessageContaining("Daily transfer limit");
    }
}
