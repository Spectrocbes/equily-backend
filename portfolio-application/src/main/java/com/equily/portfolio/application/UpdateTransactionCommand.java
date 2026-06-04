package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.UpdatedTransactionValues;

public record UpdateTransactionCommand(
    FinancialAccountId accountId,
    TransactionId transactionId,
    UserId userId,
    UpdatedTransactionValues values) {}
