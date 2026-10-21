package ledger.domain;

import java.util.Objects;

public record AccountId(String value) implements Comparable<AccountId> {

    public AccountId {
        Objects.requireNonNull(value, "account id");
        if (value.isBlank()) {
            throw new IllegalArgumentException("account id must not be blank");
        }
    }

    public static AccountId of(String value) {
        return new AccountId(value);
    }

    @Override
    public int compareTo(AccountId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
