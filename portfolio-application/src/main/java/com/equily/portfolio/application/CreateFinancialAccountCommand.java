package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.shared.Money;
import java.time.LocalDate;

public record CreateFinancialAccountCommand(
    String name,
    AccountType accountType,
    Money initialBalance,
    String broker,
    UserId ownerId,
    AccountSubType subType,
    LocalDate openedAt,
    String currency) {}
