package com.equily.portfolio.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AnalyticsTest {

  @Test
  void portfolioHistoryPoint_creates_with_all_fields() {
    LocalDate date = LocalDate.of(2026, 6, 1);
    BigDecimal totalValue = new BigDecimal("10500.00");
    BigDecimal invested = new BigDecimal("9000.00");
    BigDecimal pnl = new BigDecimal("1500.00");

    PortfolioHistoryPoint point = new PortfolioHistoryPoint(date, totalValue, invested, pnl);

    assertThat(point.date()).isEqualTo(date);
    assertThat(point.totalValue()).isEqualByComparingTo(totalValue);
    assertThat(point.invested()).isEqualByComparingTo(invested);
    assertThat(point.pnl()).isEqualByComparingTo(pnl);
  }

  @Test
  void geographicExposure_creates_with_all_fields() {
    BigDecimal value = new BigDecimal("5000.00");
    BigDecimal weight = new BigDecimal("60.00");

    GeographicExposure exposure = new GeographicExposure("United States", value, weight);

    assertThat(exposure.region()).isEqualTo("United States");
    assertThat(exposure.value()).isEqualByComparingTo(value);
    assertThat(exposure.weight()).isEqualByComparingTo(weight);
  }

  @Test
  void topPerformer_creates_with_all_fields() {
    BigDecimal currentValue = new BigDecimal("2000.00");
    BigDecimal totalInvested = new BigDecimal("1000.00");
    BigDecimal pnl = new BigDecimal("1000.00");
    BigDecimal pnlPercent = new BigDecimal("100.00");
    BigDecimal dayChange = new BigDecimal("1.50");

    TopPerformer performer =
        new TopPerformer("AAPL", "My PEA", currentValue, totalInvested, pnl, pnlPercent, dayChange);

    assertThat(performer.ticker()).isEqualTo("AAPL");
    assertThat(performer.accountName()).isEqualTo("My PEA");
    assertThat(performer.currentValue()).isEqualByComparingTo(currentValue);
    assertThat(performer.totalInvested()).isEqualByComparingTo(totalInvested);
    assertThat(performer.pnl()).isEqualByComparingTo(pnl);
    assertThat(performer.pnlPercent()).isEqualByComparingTo(pnlPercent);
    assertThat(performer.dayChangePercent()).isEqualByComparingTo(dayChange);
  }
}
