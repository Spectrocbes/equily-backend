package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.FinancialAccountId;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferCommand(
    FinancialAccountId fromAccountId,
    FinancialAccountId toAccountId, // null for external transfer
    UserId userId,
    BigDecimal amount,
    String currency,
    LocalDate date,
    String description, // optional
    String externalAddress // optional
    ) {}
