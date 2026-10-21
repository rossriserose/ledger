package ledger.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final AccountId ID = AccountId.of("a");

    @Test
    void debitAndCreditReturnNewAccount() {
        Account account = new Account(ID, Money.of(100, "EUR"));

        assertThat(account.debit(Money.of(30, "EUR")).balance()).isEqualTo(Money.of(70, "EUR"));
        assertThat(account.credit(Money.of(30, "EUR")).balance()).isEqualTo(Money.of(130, "EUR"));
        assertThat(account.balance()).isEqualTo(Money.of(100, "EUR"));
    }

    @Test
    void balanceCanNotGoNegative() {
        Account account = new Account(ID, Money.of(50, "EUR"));

        assertThatThrownBy(() -> account.debit(Money.of(51, "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
