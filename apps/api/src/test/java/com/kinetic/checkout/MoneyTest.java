package com.kinetic.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.kinetic.checkout.util.Money;

import org.junit.jupiter.api.Test;

import java.util.List;

class MoneyTest {
    @Test
    void doubleInputsAreCalculatedAsDecimalValues() {
        assertThat(Money.line(0.1, 3)).isEqualTo(0.3);
        assertThat(Money.sum(List.of(0.1, 0.2, 29.0))).isEqualTo(29.3);
        assertThat(Money.equal(29.30, 29.3)).isTrue();
        assertThat(Money.equal(29.299, 29.3)).isFalse();
        assertThat(Money.valid(Double.NaN)).isFalse();
        assertThat(Money.valid(Double.POSITIVE_INFINITY)).isFalse();
        assertThat(Money.valid(-1)).isFalse();
    }
}
