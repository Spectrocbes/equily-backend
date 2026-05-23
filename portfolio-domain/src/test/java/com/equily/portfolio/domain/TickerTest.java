package com.equily.portfolio.domain;

import com.equily.portfolio.domain.exception.InvalidTickerException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TickerTest {

    @Test
    void valid_ticker_is_created_and_uppercased() {
        Ticker ticker = new Ticker("aapl");
        assertThat(ticker.symbol()).isEqualTo("AAPL");
    }

    @Test
    void already_uppercase_ticker_is_unchanged() {
        Ticker ticker = new Ticker("IWDA.AS");
        assertThat(ticker.symbol()).isEqualTo("IWDA.AS");
    }

    @Test
    void null_symbol_throws_InvalidTickerException() {
        assertThatThrownBy(() -> new Ticker(null))
                .isInstanceOf(InvalidTickerException.class);
    }

    @Test
    void blank_symbol_throws_InvalidTickerException() {
        assertThatThrownBy(() -> new Ticker("   "))
                .isInstanceOf(InvalidTickerException.class);
    }

    @Test
    void empty_symbol_throws_InvalidTickerException() {
        assertThatThrownBy(() -> new Ticker(""))
                .isInstanceOf(InvalidTickerException.class);
    }
}
