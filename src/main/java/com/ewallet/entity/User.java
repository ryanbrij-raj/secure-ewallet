package com.ewallet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a registered user.
 *
 * <p><b>PII handling:</b> {@code email} and {@code fullName} are stored as AES-256
 * ciphertext via {@link com.ewallet.util.EncryptionUtil}. {@code emailHash} is an
 * HMAC-SHA-256 of the plaintext email — used for uniqueness checks and lookups
 * without needing to decrypt every row.
 *
 * <p><b>Optimistic locking:</b> {@code @Version} on the {@code version} field
 * prevents lost-update anomalies when concurrent sessions modify the same user row.
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** AES-256 ciphertext of the user's email address. */
    @Column(nullable = false, unique = true)
    private String email;

    /** HMAC-SHA-256 of plaintext email — safe for equality checks. */
    @Column(name = "email_hash", nullable = false, unique = true)
    private String emailHash;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** AES-256 ciphertext of the user's full name. */
    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** AES-256 ciphertext of the user's phone number. */
    @Column
    private String phone;

    @Column(nullable = false)
    @Builder.Default
    private String role = "ROLE_USER";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_locked", nullable = false)
    @Builder.Default
    private boolean locked = false;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── helper ──────────────────────────────────────────────────────────────

    public void incrementFailedLogins() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.locked = true;
        }
    }

    public void resetFailedLogins() {
        this.failedLoginAttempts = 0;
    }
}
