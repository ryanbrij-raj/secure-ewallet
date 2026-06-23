package com.ewallet.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for PII stored at rest (email, full name, phone).
 *
 * <p><b>Why GCM?</b> GCM provides both confidentiality <em>and</em> authenticity
 * (AEAD). If anyone tampers with the ciphertext in the database the decrypt step
 * throws {@link javax.crypto.AEADBadTagException}, making silent data corruption
 * impossible.
 *
 * <p><b>Format:</b> {@code Base64(IV || AuthTag || Ciphertext)} — the 12-byte
 * random IV is stored alongside each ciphertext so that the same plaintext always
 * produces a different ciphertext (IND-CPA secure).
 *
 * <p><b>HMAC:</b> {@link #hmac(String)} produces a deterministic token used as
 * an index-friendly surrogate for encrypted fields that need equality lookups
 * (e.g. find-user-by-email) without exposing the plaintext.
 */
@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;   // 96-bit IV recommended by NIST
    private static final int GCM_TAG_LENGTH = 128;  // 128-bit auth tag

    private final SecretKey aesKey;
    private final SecretKey hmacKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionUtil(@Value("${encryption.secret-key}") String hexKey) {
        byte[] keyBytes = hexToBytes(hexKey);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("Encryption key must be exactly 32 bytes (256 bits)");
        }
        this.aesKey  = new SecretKeySpec(keyBytes, "AES");
        // Derive a separate HMAC key from the same master key via a simple label expansion.
        // In production, prefer a proper KDF (HKDF).
        byte[] hmacBytes = new byte[32];
        System.arraycopy(keyBytes, 0, hmacBytes, 0, 32);
        hmacBytes[0] ^= 0xFF; // cheap domain separation
        this.hmacKey = new SecretKeySpec(hmacBytes, "HmacSHA256");
    }

    // ── Encryption ──────────────────────────────────────────────────────────

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext for self-contained storage
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            byte[] encryptedData = new byte[buffer.remaining()];
            buffer.get(encryptedData);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(encryptedData), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    // ── HMAC ────────────────────────────────────────────────────────────────

    /**
     * Returns a constant-time HMAC-SHA-256 hex digest.
     * Safe to store as a searchable index; reveals nothing about the plaintext.
     */
    public String hmac(String plaintext) {
        if (plaintext == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            byte[] digest = mac.doFinal(plaintext.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
