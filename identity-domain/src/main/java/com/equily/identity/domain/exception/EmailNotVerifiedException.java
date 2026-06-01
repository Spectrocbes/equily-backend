package com.equily.identity.domain.exception;

public class EmailNotVerifiedException extends RuntimeException {
  public EmailNotVerifiedException() {
    super("Please verify your email address before signing in");
  }
}
