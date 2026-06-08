package com.equily.portfolio.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePreferencesRequest(
    @NotBlank
        @Pattern(
            regexp = "EUR|USD|GBP|CHF",
            message = "Currency must be one of: EUR, USD, GBP, CHF")
        String currency,
    @NotBlank String locale) {}
