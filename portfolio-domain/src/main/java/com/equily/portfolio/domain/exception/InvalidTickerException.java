package com.equily.portfolio.domain.exception;

public class InvalidTickerException extends RuntimeException {
    public InvalidTickerException(String message) {
        super(message);
    }
}
