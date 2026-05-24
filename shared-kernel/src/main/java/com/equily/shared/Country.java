package com.equily.shared;

public record Country(String isoCode) {

  public Country {
    if (isoCode == null || isoCode.isBlank()) {
      throw new IllegalArgumentException("isoCode must not be null or blank");
    }
    if (isoCode.length() != 2) {
      throw new IllegalArgumentException("isoCode must be a 2-letter ISO 3166-1 alpha-2 code");
    }
    isoCode = isoCode.toUpperCase();
  }
}
