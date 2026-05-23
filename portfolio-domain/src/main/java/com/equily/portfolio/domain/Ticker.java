package com.equily.portfolio.domain;

import com.equily.portfolio.domain.exception.InvalidTickerException;

public record Ticker(String symbol) {

    public Ticker {
        if (symbol == null || symbol.isBlank()) {
            throw new InvalidTickerException("ticker symbol must not be null or blank");
        }
        symbol = symbol.toUpperCase();
    }
}
