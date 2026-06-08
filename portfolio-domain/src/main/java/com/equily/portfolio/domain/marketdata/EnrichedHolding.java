package com.equily.portfolio.domain.marketdata;

import com.equily.portfolio.domain.Holding;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record EnrichedHolding(
    Holding holding,
    BigDecimal currentPrice,
    String currency,
    BigDecimal marketValue,
    BigDecimal unrealizedPnl,
    BigDecimal unrealizedPnlPct,
    BigDecimal dayChangePercent,
    boolean priceAvailable) {

  public static EnrichedHolding withPrice(Holding holding, Quote quote) {
    BigDecimal marketValue =
        quote.price().multiply(holding.quantity()).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal pnl =
        marketValue.subtract(holding.totalInvested().amount()).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal pnlPct =
        holding.totalInvested().amount().compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : pnl.divide(holding.totalInvested().amount(), 4, RoundingMode.HALF_EVEN)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_EVEN);
    return new EnrichedHolding(
        holding,
        quote.price(),
        quote.currency(),
        marketValue,
        pnl,
        pnlPct,
        quote.changePercent(),
        true);
  }

  public static EnrichedHolding withoutPrice(Holding holding) {
    return new EnrichedHolding(holding, null, null, null, null, null, null, false);
  }
}
