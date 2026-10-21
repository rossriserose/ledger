package ledger.domain;

import java.util.Currency;
import java.util.Objects;

/**
 * Amount in minor units (cents for EUR). We use long and not double, because
 * double can not keep 0.10 exactly. Arithmetic throws ArithmeticException on overflow.
 */
public record Money(long minorUnits, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long minorUnits, String currencyCode) {
        return new Money(minorUnits, Currency.getInstance(currencyCode));
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return minorUnits < other.minorUnits;
    }

    public boolean isNegative() {
        return minorUnits < 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch: " + currency + " and " + other.currency);
        }
    }

    @Override
    public String toString() {
        return minorUnits + " " + currency.getCurrencyCode();
    }
}
