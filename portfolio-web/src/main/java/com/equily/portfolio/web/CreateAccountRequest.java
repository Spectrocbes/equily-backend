package com.equily.portfolio.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateAccountRequest(
    @NotBlank String name,
    @NotBlank String accountType,
    @NotNull BigDecimal initialBalance,
    @NotBlank String currency,
    @NotBlank String broker) {}
