package com.ewallet.controller;

import com.ewallet.dto.response.WalletResponse;
import com.ewallet.service.impl.WalletServiceImpl;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Validated
public class WalletController {

    private final WalletServiceImpl walletService;

    /** List all wallets owned by the authenticated user. */
    @GetMapping
    public List<WalletResponse> listWallets(@AuthenticationPrincipal UUID userId) {
        return walletService.getWallets(userId);
    }

    /** Get a specific wallet by ID (must belong to the authenticated user). */
    @GetMapping("/{walletId}")
    public WalletResponse getWallet(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID walletId) {
        return walletService.getWallet(userId, walletId);
    }

    /** Open a new wallet in the given ISO 4217 currency. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(
            @AuthenticationPrincipal UUID userId,
            @RequestParam @Pattern(regexp = "[A-Z]{3}") String currency) {
        return walletService.createWallet(userId, currency);
    }
}
