package com.template.api.shared.domain.valueobject;

import com.template.api.shared.domain.exception.ValidationException;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) implements ValueObject, Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "MONEY_AMOUNT_CANNOT_BE_NULL");
        Objects.requireNonNull(currency, "MONEY_CURRENCY_CANNOT_BE_NULL");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("MONEY_AMOUNT_CANNOT_BE_NEGATIVE");
        }
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        Objects.requireNonNull(currencyCode, "MONEY_CURRENCY_CODE_CANNOT_BE_NULL");
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money of(String amount, String currencyCode) {
        Objects.requireNonNull(amount, "MONEY_AMOUNT_CANNOT_BE_NULL");
        return of(new BigDecimal(amount), currencyCode);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Money zero(String currencyCode) {
        return of(BigDecimal.ZERO, currencyCode);
    }

    public Money plus(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money minus(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "MONEY_FACTOR_CANNOT_BE_NULL");
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public Money multiply(long factor) {
        return multiply(BigDecimal.valueOf(factor));
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public boolean isSameCurrency(Money other) {
        return other != null && Objects.equals(this.currency, other.currency);
    }

    private void validateSameCurrency(Money other) {
        Objects.requireNonNull(other, "MONEY_OTHER_CANNOT_BE_NULL");
        if (!isSameCurrency(other)) {
            throw new ValidationException(String.format(
                    "MONEY_CURRENCY_MISMATCH: Currency mismatch between %s and %s",
                    this.currency.getCurrencyCode(),
                    other.currency.getCurrencyCode()
            ));
        }
    }

    @Override
    public int compareTo(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return this.amount.compareTo(other.amount) == 0 && this.currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        int result = (this.amount.signum() == 0 ? BigDecimal.ZERO : this.amount.stripTrailingZeros()).hashCode();
        return 31 * result + this.currency.hashCode();
    }

    @Override
    public String toString() {
        return this.amount + " " + this.currency.getCurrencyCode();
    }
}
