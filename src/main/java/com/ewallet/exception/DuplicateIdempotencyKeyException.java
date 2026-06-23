package com.ewallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateIdempotencyKeyException extends RuntimeException {
    public DuplicateIdempotencyKeyException(String key) {
        super("Transaction with idempotency key already exists: " + key);
    }
}
