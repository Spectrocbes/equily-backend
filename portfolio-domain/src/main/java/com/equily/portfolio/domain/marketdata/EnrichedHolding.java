package com.equily.portfolio.domain.marketdata;

import com.equily.portfolio.domain.Holding;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record EnrichedHolding(
    Holding holding,
    BigDecimal currentPrice,
    BigDecimal avgCostInTarget,
    BigDecimal totalInvestedInTarget,
    BigDecimal totalFeesInTarget,
    String currency,
    BigDecimal marketValue,
    BigDecimal unrealizedPnl,
    BigDecimal unrealizedPnlPct,
    BigDecimal dayChangePercent,
    boolean priceAvailable) {

  public static EnrichedHolding withPrice(
      Holding holding,
      Quote quote,
      String targetCurrency,
      BigDecimal liveToTarget,
      BigDecimal costToTarget) {
    BigDecimal priceInTarget =
        quote.price().multiply(liveToTarget).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal marketValue =
        priceInTarget.multiply(holding.quantity()).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal avgCostInTarget =
        holding
            .averageCostPrice()
            .amount()
            .multiply(costToTarget)
            .setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal totalInvestedInTarget =
        holding.totalInvested().amount().multiply(costToTarget).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal totalFeesInTarget =
        holding.totalFeesPaid().amount().multiply(costToTarget).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal pnl =
        marketValue.subtract(totalInvestedInTarget).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal pnlPct =
        totalInvestedInTarget.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : pnl.divide(totalInvestedInTarget, 4, RoundingMode.HALF_EVEN)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_EVEN);
    return new EnrichedHolding(
        holding,
        priceInTarget,
        avgCostInTarget,
        totalInvestedInTarget,
        totalFeesInTarget,
        targetCurrency,
        marketValue,
        pnl,
        pnlPct,
        quote.changePercent(),
        true);
  }

  public static EnrichedHolding withoutPrice(Holding holding) {
    return new EnrichedHolding(
        holding,
        null,
        holding.averageCostPrice().amount(),
        holding.totalInvested().amount(),
        holding.totalFeesPaid().amount(),
        null,
        null,
        null,
        null,
        null,
        false);
  }
}
