package com.ewallet.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates domain exceptions to RFC 7807 {@link ProblemDetail} responses.
 *
 * <p>Using ProblemDetail (Spring 6+) means clients get a consistent JSON error
 * envelope with a {@code type} URI, {@code title}, {@code status}, {@code detail},
 * and optional extensions — no custom error DTO needed.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (a, b) -> a
                ));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setType(URI.create("/errors/validation"));
        pd.setProperty("violations", errors);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex, "/errors/insufficient-funds");
    }

    @ExceptionHandler(DuplicateIdempotencyKeyException.class)
    public ProblemDetail handleDuplicateKey(DuplicateIdempotencyKeyException ex) {
        // Return 200 so clients know the original request succeeded
        return problem(HttpStatus.OK, ex, "/errors/idempotency");
    }

    @ExceptionHandler(DailyLimitExceededException.class)
    public ProblemDetail handleDailyLimit(DailyLimitExceededException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, ex, "/errors/daily-limit");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex, "/errors/not-found");
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
        return problem(HttpStatus.CONFLICT, ex, "/errors/duplicate-email");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex, "/errors/invalid-credentials");
    }

    @ExceptionHandler(AccountLockedException.class)
    public ProblemDetail handleLocked(AccountLockedException ex) {
        return problem(HttpStatus.FORBIDDEN, ex, "/errors/account-locked");
    }

    @ExceptionHandler(WalletAlreadyExistsException.class)
    public ProblemDetail handleWalletExists(WalletAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, ex, "/errors/wallet-exists");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, ex, "/errors/forbidden");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setType(URI.create("/errors/internal"));
        pd.setDetail("An unexpected error occurred. Reference: contact support.");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // ── helper ──────────────────────────────────────────────────────────────

    private ProblemDetail problem(HttpStatus status, Exception ex, String typeUri) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setType(URI.create(typeUri));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
