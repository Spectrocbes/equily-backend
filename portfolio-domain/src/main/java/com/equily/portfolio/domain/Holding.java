package com.equily.portfolio.domain;

import com.equily.portfolio.domain.exception.InvalidHoldingException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record Holding(
    Ticker ticker,
    AssetType assetType,
    AssetMetadata metadata,
    BigDecimal quantity,
    Money averageCostPrice,
    Money totalInvested,
    Money totalFeesPaid) {

  private static final int QUANTITY_SCALE = 8;
  private static final int MONEY_SCALE = 4;
  private static final Currency EUR = Currency.getInstance("EUR");

  public static Optional<Holding> computeFrom(
      List<Transaction> transactions, AssetType assetType, AssetMetadata metadata) {
    if (transactions == null || transactions.isEmpty()) return Optional.empty();

    Ticker ticker = transactions.get(0).ticker();

    BigDecimal totalQty = BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.HALF_EVEN);
    BigDecimal totalBoughtQty = BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.HALF_EVEN);
    BigDecimal weightedCostSum =
        BigDecimal.ZERO.setScale(MONEY_SCALE + QUANTITY_SCALE, RoundingMode.HALF_EVEN);
    BigDecimal totalFees = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);

    for (Transaction t : transactions) {
      if (t.type() == TransactionType.BUY) {
        BigDecimal qty = t.quantity().setScale(QUANTITY_SCALE, RoundingMode.HALF_EVEN);
        // Cost basis in EUR: amountEur represents the EUR-equivalent of what was paid
        BigDecimal costEur =
            t.amountEur().setScale(MONEY_SCALE + QUANTITY_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal feesEur = t.fees().setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
        weightedCostSum = weightedCostSum.add(costEur);
        totalFees = totalFees.add(feesEur);
        totalQty = totalQty.add(qty);
        totalBoughtQty = totalBoughtQty.add(qty);
      } else if (t.type() == TransactionType.SELL) {
        BigDecimal qty = t.quantity().setScale(QUANTITY_SCALE, RoundingMode.HALF_EVEN);
        totalQty = totalQty.subtract(qty);
        if (totalQty.compareTo(BigDecimal.ZERO) < 0) {
          throw new InvalidHoldingException(ticker.symbol(), qty, totalQty.add(qty));
        }
        // French fiscal rule: SELL reduces quantity but does not change averageCostPrice.
      }
    }

    if (totalQty.compareTo(BigDecimal.ZERO) == 0) {
      return Optional.empty();
    }

    BigDecimal avgCostAmount =
        totalBoughtQty.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN)
            : weightedCostSum.divide(totalBoughtQty, MONEY_SCALE, RoundingMode.HALF_EVEN);

    Money averageCostPrice = new Money(avgCostAmount, EUR);
    Money totalInvested = averageCostPrice.multiply(totalQty);
    Money totalFeesPaid = new Money(totalFees, EUR);

    return Optional.of(
        new Holding(
            ticker, assetType, metadata, totalQty, averageCostPrice, totalInvested, totalFeesPaid));
  }

  public static List<Holding> computeFrom(List<Transaction> transactions) {
    if (transactions == null || transactions.isEmpty()) return List.of();
    return transactions.stream()
        .filter(
            t ->
                t.ticker() != null
                    && (t.type() == TransactionType.BUY || t.type() == TransactionType.SELL))
        .collect(Collectors.groupingBy(t -> t.ticker().symbol()))
        .values()
        .stream()
        .map(group -> computeFrom(group, null, null))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }
}
