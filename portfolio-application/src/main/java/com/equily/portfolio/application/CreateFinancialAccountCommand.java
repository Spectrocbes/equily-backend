package com.equily.portfolio.application;

import com.equily.portfolio.domain.AccountType;
import com.equily.shared.Money;

public record CreateFinancialAccountCommand(
    String name, AccountType accountType, Money initialBalance, String broker) {}
