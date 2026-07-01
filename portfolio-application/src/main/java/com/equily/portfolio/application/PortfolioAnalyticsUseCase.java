package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.analytics.GeographicExposure;
import com.equily.portfolio.domain.analytics.PortfolioHistoryPoint;
import com.equily.portfolio.domain.analytics.TopPerformer;
import java.util.List;

public interface PortfolioAnalyticsUseCase {

  /** Computes portfolio total value over time for all accounts owned by the user. */
  List<PortfolioHistoryPoint> getPortfolioHistory(
      UserId userId, Period period, String targetCurrency);

  /**
   * Portfolio history filtered by account type category.
   *
   * @param accountTypeCategory one of "INVESTMENT", "CRYPTO", "SAVINGS", "CASH" (case-insensitive)
   */
  List<PortfolioHistoryPoint> getPortfolioHistoryByType(
      UserId userId, String accountTypeCategory, Period period, String targetCurrency);

  /** Computes geographic exposure for an investment account. */
  List<GeographicExposure> getGeographicExposure(
      FinancialAccountId accountId, UserId userId, String targetCurrency);

  /** Returns the top/bottom performers across all investment accounts. */
  List<TopPerformer> getTopPerformers(UserId userId, String targetCurrency, int limit);

  /** Computes value history for a single account over the given period. */
  List<PortfolioHistoryPoint> getAccountHistory(
      FinancialAccountId accountId, UserId userId, Period period, String targetCurrency);
}
