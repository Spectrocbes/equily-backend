package com.equily.identity.domain;

import java.util.Objects;
import java.util.Set;

public record UserPreferences(UserId userId, String currency, String locale) {

  public static final Set<String> SUPPORTED_CURRENCIES = Set.of("EUR", "USD", "GBP", "CHF");
  public static final String DEFAULT_CURRENCY = "EUR";
  public static final String DEFAULT_LOCALE = "fr";

  public UserPreferences {
    Objects.requireNonNull(userId, "userId must not be null");
    if (!SUPPORTED_CURRENCIES.contains(currency)) {
      throw new IllegalArgumentException("Unsupported currency: " + currency);
    }
    Objects.requireNonNull(locale, "locale must not be null");
  }

  public static UserPreferences defaultFor(UserId userId) {
    return new UserPreferences(userId, DEFAULT_CURRENCY, DEFAULT_LOCALE);
  }
}
