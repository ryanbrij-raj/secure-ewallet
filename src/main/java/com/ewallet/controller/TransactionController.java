package com.ewallet.controller;

import com.ewallet.dto.request.DepositRequest;
import com.ewallet.dto.request.TransferRequest;
import com.ewallet.dto.response.TransactionResponse;
import com.ewallet.service.impl.TransactionServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionServiceImpl transactionService;

    /**
     * Deposit funds into a wallet.
     *
     * <p>Idempotent: repeating the request with the same {@code idempotencyKey}
     * returns the original response without crediting twice.
     */
    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody DepositRequest req) {
        return transactionService.deposit(userId, req);
    }

    /**
     * Transfer funds to another user's wallet.
     *
     * <p>Both wallets must exist in the requested currency. The daily limit is
     * enforced across all outgoing transfers in a 24-hour rolling window.
     */
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody TransferRequest req) {
        return transactionService.transfer(userId, req);
    }

    /**
     * Paginated transaction history for the authenticated user.
     * Default: 20 per page, sorted by {@code createdAt DESC}.
     */
    @GetMapping
    public Page<TransactionResponse> history(
            @AuthenticationPrincipal UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return transactionService.getHistory(userId, pageable);
    }
}
