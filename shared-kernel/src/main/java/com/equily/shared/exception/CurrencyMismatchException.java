package com.equily.shared.exception;

import java.util.Currency;

public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Currency mismatch: expected " + expected.getCurrencyCode()
                + " but got " + actual.getCurrencyCode());
    }
}
