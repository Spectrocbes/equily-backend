package com.equily.portfolio.domain;

import com.equily.portfolio.domain.exception.InvalidHoldingException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

public record Holding(
    Ticker ticker,
    AssetType assetType,
    AssetMetadata metadata,
    BigDecimal quantity,
    Money averageCostPrice,
    Money totalInvested) {

  private static final int QUANTITY_SCALE = 8;
  private static final int MONEY_SCALE = 2;

  public static Optional<Holding> computeFrom(
      List<Transaction> transactions, AssetType assetType, AssetMetadata metadata) {
    if (transactions == null || transactions.isEmpty()) return Optional.empty();

    Ticker ticker = transactions.get(0).ticker();
    Currency currency =
        transactions.stream()
            .filter(t -> t.totalAmount() != null)
            .map(t -> t.totalAmount().currency())
            .findFirst()
            .orElseThrow(() -> new InvalidHoldingException("no transactions with a totalAmount"));

    BigDecimal totalQty = BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.HALF_EVEN);
    BigDecimal totalBoughtQty = BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.HALF_EVEN);
    BigDecimal weightedCostSum =
        BigDecimal.ZERO.setScale(MONEY_SCALE + QUANTITY_SCALE, RoundingMode.HALF_EVEN);

    for (Transaction t : transactions) {
      if (t.type() == TransactionType.BUY) {
        BigDecimal qty = t.quantity().setScale(QUANTITY_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal price = t.pricePerUnit().amount().setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
        weightedCostSum = weightedCostSum.add(qty.multiply(price));
        totalQty = totalQty.add(qty);
        totalBoughtQty = totalBoughtQty.add(qty);
      } else if (t.type() == TransactionType.SELL) {
        BigDecimal qty = t.quantity().setScale(QUANTITY_SCALE, RoundingMode.HALF_EVEN);
        totalQty = totalQty.subtract(qty);
        if (totalQty.compareTo(BigDecimal.ZERO) < 0) {
          throw new InvalidHoldingException(
              "sell quantity exceeds held quantity for ticker " + ticker.symbol());
        }
        // French fiscal rule: SELL reduces quantity but does not change averageCostPrice.
        // weightedCostSum is preserved; averageCostPrice will be recalculated from totalBoughtQty
        // below.
      }
    }

    if (totalQty.compareTo(BigDecimal.ZERO) == 0) {
      return Optional.empty();
    }

    BigDecimal avgCostAmount =
        totalBoughtQty.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN)
            : weightedCostSum.divide(totalBoughtQty, MONEY_SCALE, RoundingMode.HALF_EVEN);

    Money averageCostPrice = new Money(avgCostAmount, currency);
    Money totalInvested = averageCostPrice.multiply(totalQty);

    return Optional.of(
        new Holding(ticker, assetType, metadata, totalQty, averageCostPrice, totalInvested));
  }
}
