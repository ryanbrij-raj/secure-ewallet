package com.ewallet;

import com.ewallet.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EncryptionUtilTest {

    private EncryptionUtil util;

    @BeforeEach
    void setUp() {
        // 32-byte hex key (256 bits)
        util = new EncryptionUtil("0123456789abcdef0123456789abcdef");
    }

    @Test
    @DisplayName("encrypt → decrypt round-trip returns original plaintext")
    void roundTrip() {
        String plaintext = "alice@example.com";
        String cipher    = util.encrypt(plaintext);
        assertThat(cipher).isNotEqualTo(plaintext);
        assertThat(util.decrypt(cipher)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("two encryptions of the same plaintext produce different ciphertexts (IND-CPA)")
    void encryptionIsNonDeterministic() {
        String a = util.encrypt("same");
        String b = util.encrypt("same");
        assertThat(a).isNotEqualTo(b);  // different random IVs
    }

    @Test
    @DisplayName("HMAC is deterministic and case-insensitive")
    void hmacIsDeterministicAndCaseInsensitive() {
        assertThat(util.hmac("Alice@Example.COM"))
                .isEqualTo(util.hmac("alice@example.com"));
    }

    @Test
    @DisplayName("null input returns null")
    void nullInputReturnsNull() {
        assertThat(util.encrypt(null)).isNull();
        assertThat(util.decrypt(null)).isNull();
        assertThat(util.hmac(null)).isNull();
    }

    @Test
    @DisplayName("tampered ciphertext throws on decrypt")
    void tamperedCiphertextThrows() {
        String cipher  = util.encrypt("hello");
        String tampered = cipher.substring(0, cipher.length() - 4) + "XXXX";
        assertThatThrownBy(() -> util.decrypt(tampered))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }
}
