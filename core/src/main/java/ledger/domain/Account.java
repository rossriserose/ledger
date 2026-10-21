package ledger.domain;

import java.util.Objects;

public record Account(AccountId id, Money balance) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(balance, "balance");
        if (balance.isNegative()) {
            throw new IllegalArgumentException("balance must not be negative: " + balance);
        }
    }

    public Account debit(Money amount) {
        return new Account(id, balance.minus(amount));
    }

    public Account credit(Money amount) {
        return new Account(id, balance.plus(amount));
    }
}
