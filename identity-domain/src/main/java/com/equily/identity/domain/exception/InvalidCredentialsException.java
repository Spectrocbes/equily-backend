package com.equily.identity.domain.exception;

public class InvalidCredentialsException extends RuntimeException {
  public InvalidCredentialsException() {
    super("Invalid email or password");
  }
}
