package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.TransactionType;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Command to record a new transaction on a FinancialAccount. Validated at the web layer before
 * reaching here.
 */
public record RecordTransactionCommand(
    FinancialAccountId accountId,
    UserId userId,
    TransactionType type,
    Ticker ticker,
    BigDecimal quantity,
    Money pricePerUnit,
    Money totalAmount,
    LocalDate date,
    BigDecimal fees,
    String description,
    String currency,
    String externalAddress) {}
