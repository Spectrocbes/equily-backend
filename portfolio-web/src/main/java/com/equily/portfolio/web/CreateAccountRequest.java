package com.equily.portfolio.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateAccountRequest(
    @NotBlank String name,
    @NotBlank String accountType,
    @NotNull BigDecimal initialBalance,
    @NotBlank String currency,
    @NotBlank String broker,
    String subType,
    LocalDate openedAt,
    UUID linkedCheckingAccountId) {}
