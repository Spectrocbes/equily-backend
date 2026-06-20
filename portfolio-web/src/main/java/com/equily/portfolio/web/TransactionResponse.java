package com.equily.portfolio.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
    String id,
    String type,
    String ticker,
    BigDecimal quantity,
    BigDecimal pricePerUnit,
    BigDecimal totalAmount,
    BigDecimal totalAmountNative,
    String nativeCurrency,
    LocalDate date,
    BigDecimal fees,
    BigDecimal feesNative,
    String description,
    String transferId,
    String linkedAccountId,
    String externalAddress,
    String transferDirection) {}
