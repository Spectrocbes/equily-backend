package com.equily.shared;

import com.equily.shared.exception.CurrencyMismatchException;
import com.equily.shared.exception.InvalidMoneyException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) throw new InvalidMoneyException("amount must not be null");
        if (currency == null) throw new InvalidMoneyException("currency must not be null");
        amount = amount.stripTrailingZeros();
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        if (factor == null) throw new InvalidMoneyException("factor must not be null");
        return new Money(this.amount.multiply(factor).setScale(2, RoundingMode.HALF_EVEN), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    // Uses compareTo so that 1.00 EUR == 1 EUR (BigDecimal.equals is scale-sensitive)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money m)) return false;
        return currency.equals(m.currency) && amount.compareTo(m.amount) == 0;
    }

    @Override
    public int hashCode() {
        // stripTrailingZeros is idempotent; ensures equal amounts hash identically
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString()
                + " " + currency.getCurrencyCode();
    }

    private void requireSameCurrency(Money other) {
        if (other == null) throw new InvalidMoneyException("other must not be null");
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException(this.currency, other.currency);
        }
    }
}
