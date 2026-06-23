package com.ewallet.service.impl;

import com.ewallet.dto.response.WalletResponse;
import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import com.ewallet.exception.ResourceNotFoundException;
import com.ewallet.exception.WalletAlreadyExistsException;
import com.ewallet.repository.UserRepository;
import com.ewallet.repository.WalletRepository;
import com.ewallet.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl {

    private final WalletRepository walletRepository;
    private final UserRepository   userRepository;
    private final AuditService     auditService;

    @Value("#{'${wallet.supported-currencies}'.split(',')}")
    private Set<String> supportedCurrencies;

    // ── Create wallet ────────────────────────────────────────────────────────

    @Transactional
    public WalletResponse createWallet(UUID userId, String currencyCode) {
        currencyCode = currencyCode.toUpperCase();

        if (!supportedCurrencies.contains(currencyCode)) {
            throw new IllegalArgumentException("Unsupported currency: " + currencyCode);
        }

        if (walletRepository.existsByUserIdAndCurrencyCode(userId, currencyCode)) {
            throw new WalletAlreadyExistsException(
                    "Wallet already exists for currency: " + currencyCode);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Wallet wallet = Wallet.builder()
                .user(user)
                .currencyCode(currencyCode)
                .build();

        wallet = walletRepository.save(wallet);

        auditService.log("WALLET_CREATED", userId, "Wallet",
                wallet.getId().toString(), null,
                Map.of("currency", currencyCode));

        return toResponse(wallet);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WalletResponse> getWallets(UUID userId) {
        return walletRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId, UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .filter(w -> w.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        return toResponse(wallet);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public WalletResponse toResponse(Wallet w) {
        return WalletResponse.builder()
                .id(w.getId())
                .currencyCode(w.getCurrencyCode())
                .balance(w.getBalance())
                .active(w.isActive())
                .createdAt(w.getCreatedAt())
                .build();
    }
}
