package com.ewallet;

import com.ewallet.entity.Wallet;
import com.ewallet.exception.InsufficientFundsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class WalletTest {

    private Wallet wallet(String balance) {
        return Wallet.builder().balance(new BigDecimal(balance)).build();
    }

    @Test @DisplayName("credit increases balance")
    void credit() {
        Wallet w = wallet("100.00");
        w.credit(new BigDecimal("50.00"));
        assertThat(w.getBalance()).isEqualByComparingTo("150.00");
    }

    @Test @DisplayName("debit decreases balance")
    void debit() {
        Wallet w = wallet("100.00");
        w.debit(new BigDecimal("40.00"));
        assertThat(w.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test @DisplayName("debit exact balance succeeds")
    void debitExactBalance() {
        Wallet w = wallet("100.00");
        w.debit(new BigDecimal("100.00"));
        assertThat(w.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test @DisplayName("debit beyond balance throws InsufficientFundsException")
    void debitBeyondBalance() {
        Wallet w = wallet("50.00");
        assertThatThrownBy(() -> w.debit(new BigDecimal("100.00")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test @DisplayName("credit zero throws IllegalArgumentException")
    void creditZero() {
        assertThatThrownBy(() -> wallet("100.00").credit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("debit negative throws IllegalArgumentException")
    void debitNegative() {
        assertThatThrownBy(() -> wallet("100.00").debit(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
