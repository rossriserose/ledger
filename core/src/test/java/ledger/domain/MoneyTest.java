package ledger.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void addsAndSubtractsInMinorUnits() {
        Money ten = Money.of(1000, "EUR");
        Money three = Money.of(300, "EUR");

        assertThat(ten.plus(three)).isEqualTo(Money.of(1300, "EUR"));
        assertThat(ten.minus(three)).isEqualTo(Money.of(700, "EUR"));
    }

    @Test
    void overflowThrowsInsteadOfWrapping() {
        Money max = Money.of(Long.MAX_VALUE, "EUR");

        assertThatThrownBy(() -> max.plus(Money.of(1, "EUR")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void differentCurrenciesCanNotBeMixed() {
        assertThatThrownBy(() -> Money.of(100, "EUR").plus(Money.of(100, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
