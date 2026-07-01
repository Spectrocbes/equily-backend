package com.equily.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.analytics.GeographicExposure;
import com.equily.portfolio.domain.analytics.PortfolioHistoryPoint;
import com.equily.portfolio.domain.analytics.TopPerformer;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.portfolio.domain.marketdata.FxRatePort;
import com.equily.portfolio.domain.marketdata.MarketDataPort;
import com.equily.portfolio.domain.marketdata.Quote;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioAnalyticsServiceTest {

  @Mock private FinancialAccountRepository repository;
  @Mock private MarketDataPort marketDataPort;
  @Mock private FxRatePort fxRatePort;

  @InjectMocks private PortfolioAnalyticsService service;

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final LocalDate OPENED_AT = LocalDate.of(2024, Month.JANUARY, 1);
  private static final UserId USER_ID = UserId.generate();

  private FinancialAccount cashAccount() {
    return FinancialAccount.open(
        "Checking",
        AccountType.CASH_ACCOUNT,
        new Money(BigDecimal.ZERO, EUR),
        "BNP",
        USER_ID,
        null,
        OPENED_AT);
  }

  private FinancialAccount peaAccount() {
    return FinancialAccount.open(
        "My PEA",
        AccountType.PEA,
        new Money(BigDecimal.ZERO, EUR),
        "Fortuneo",
        USER_ID,
        null,
        OPENED_AT);
  }

  @Test
  void getPortfolioHistory_returns_points_for_period() {
    FinancialAccount account = cashAccount();
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "Initial deposit"));

    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(account));

    List<PortfolioHistoryPoint> points =
        service.getPortfolioHistory(USER_ID, Period.ONE_WEEK, "EUR");

    assertThat(points).isNotEmpty().allMatch(p -> p.totalValue().compareTo(BigDecimal.ZERO) >= 0);
  }

  @Test
  void getPortfolioHistory_skips_weekends() {
    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(cashAccount()));

    List<PortfolioHistoryPoint> points =
        service.getPortfolioHistory(USER_ID, Period.ONE_MONTH, "EUR");

    LocalDate today = LocalDate.now(ZoneId.of("Europe/Paris"));
    assertThat(points)
        .allMatch(
            p ->
                (!DayOfWeek.SATURDAY.equals(p.date().getDayOfWeek())
                        && !DayOfWeek.SUNDAY.equals(p.date().getDayOfWeek()))
                    || p.date().equals(today));
  }

  @Test
  void getPortfolioHistory_uses_closest_price_for_non_trading_days() {
    FinancialAccount pea = peaAccount();
    // Deposit first, then buy
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT,
            null,
            "dep"));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            BigDecimal.ONE,
            new Money(BigDecimal.valueOf(100), EUR),
            new Money(BigDecimal.valueOf(100), EUR),
            OPENED_AT.plusDays(1),
            null,
            "buy"));

    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(pea));

    // Return price only for one specific date (not all trading days)
    LocalDate priceDate = LocalDate.now().minusDays(3);
    when(marketDataPort.getHistoricalPrices(eq("AAPL"), any(), any()))
        .thenReturn(Map.of(priceDate, BigDecimal.valueOf(150)));
    when(fxRatePort.getRate("USD", "EUR")).thenReturn(Optional.of(BigDecimal.ONE));

    List<PortfolioHistoryPoint> points =
        service.getPortfolioHistory(USER_ID, Period.ONE_WEEK, "EUR");

    // Points after priceDate should carry forward the price
    assertThat(points)
        .filteredOn(p -> !p.date().isBefore(priceDate) && !p.date().isAfter(LocalDate.now()))
        .isNotEmpty()
        .allMatch(p -> p.totalValue().compareTo(BigDecimal.ZERO) > 0);
  }

  @Test
  void getGeographicExposure_infers_us_for_ticker_without_suffix() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT,
            null,
            "dep"));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            BigDecimal.TEN,
            new Money(BigDecimal.valueOf(100), EUR),
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "buy"));

    FinancialAccountId accountId = pea.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(pea));
    when(marketDataPort.getQuotes(anyList()))
        .thenReturn(
            Map.of(
                "AAPL",
                new Quote(
                    "AAPL",
                    BigDecimal.valueOf(150),
                    "USD",
                    "Apple",
                    Instant.now(),
                    BigDecimal.ZERO)));
    when(fxRatePort.getRate("USD", "EUR")).thenReturn(Optional.of(BigDecimal.ONE));

    List<GeographicExposure> exposure = service.getGeographicExposure(accountId, USER_ID, "EUR");

    assertThat(exposure).hasSize(1);
    assertThat(exposure.get(0).region()).isEqualTo("United States");
    assertThat(exposure.get(0).weight()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  void getGeographicExposure_infers_france_for_pa_suffix() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT,
            null,
            "dep"));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AIR.PA"),
            BigDecimal.TEN,
            new Money(BigDecimal.valueOf(100), EUR),
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "buy"));

    FinancialAccountId accountId = pea.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(pea));
    when(marketDataPort.getQuotes(anyList()))
        .thenReturn(
            Map.of(
                "AIR.PA",
                new Quote(
                    "AIR.PA",
                    BigDecimal.valueOf(130),
                    "EUR",
                    "Airbus",
                    Instant.now(),
                    BigDecimal.ZERO)));
    when(fxRatePort.getRate("EUR", "EUR")).thenReturn(Optional.of(BigDecimal.ONE));

    List<GeographicExposure> exposure = service.getGeographicExposure(accountId, USER_ID, "EUR");

    assertThat(exposure).hasSize(1);
    assertThat(exposure.get(0).region()).isEqualTo("France");
  }

  @Test
  void getGeographicExposure_calculates_weights_correctly() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(2000), EUR),
            OPENED_AT,
            null,
            "dep"));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            BigDecimal.TEN,
            new Money(BigDecimal.valueOf(100), EUR),
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "buy AAPL"));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AIR.PA"),
            BigDecimal.TEN,
            new Money(BigDecimal.valueOf(100), EUR),
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "buy AIR"));

    FinancialAccountId accountId = pea.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(pea));
    Quote usQuote =
        new Quote("AAPL", BigDecimal.valueOf(100), "USD", "Apple", Instant.now(), BigDecimal.ZERO);
    Quote frQuote =
        new Quote(
            "AIR.PA", BigDecimal.valueOf(100), "EUR", "Airbus", Instant.now(), BigDecimal.ZERO);
    when(marketDataPort.getQuotes(anyList()))
        .thenReturn(Map.of("AAPL", usQuote, "AIR.PA", frQuote));
    when(fxRatePort.getRate(anyString(), eq("EUR"))).thenReturn(Optional.of(BigDecimal.ONE));

    List<GeographicExposure> exposure = service.getGeographicExposure(accountId, USER_ID, "EUR");

    assertThat(exposure).hasSize(2);
    BigDecimal totalWeight =
        exposure.stream().map(GeographicExposure::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(totalWeight).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  void getGeographicExposure_throws_account_not_found_when_not_owned() {
    FinancialAccountId accountId = FinancialAccountId.generate();
    when(repository.findById(accountId)).thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertThrows(
        AccountNotFoundException.class,
        () -> service.getGeographicExposure(accountId, USER_ID, "EUR"));
  }

  @Test
  void getTopPerformers_sorts_by_pnl_percent_descending() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(2000), EUR),
            OPENED_AT,
            null,
            "dep"));
    // AAPL: bought at 100, now 200 → +100%
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            BigDecimal.TEN,
            new Money(BigDecimal.valueOf(100), EUR),
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "buy AAPL"));
    // MSFT: bought at 100, now 110 → +10%
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("MSFT"),
            BigDecimal.TEN,
            new Money(BigDecimal.valueOf(100), EUR),
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "buy MSFT"));

    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(pea));
    when(marketDataPort.getQuotes(anyList()))
        .thenReturn(
            Map.of(
                "AAPL",
                new Quote(
                    "AAPL",
                    BigDecimal.valueOf(200),
                    "EUR",
                    "Apple",
                    Instant.now(),
                    BigDecimal.ZERO),
                "MSFT",
                new Quote(
                    "MSFT",
                    BigDecimal.valueOf(110),
                    "EUR",
                    "Microsoft",
                    Instant.now(),
                    BigDecimal.ZERO)));
    when(fxRatePort.getRate("EUR", "EUR")).thenReturn(Optional.of(BigDecimal.ONE));

    List<TopPerformer> performers = service.getTopPerformers(USER_ID, "EUR", 10);

    assertThat(performers).hasSize(2);
    assertThat(performers.get(0).ticker()).isEqualTo("AAPL");
    assertThat(performers.get(0).pnlPercent()).isGreaterThan(performers.get(1).pnlPercent());
  }

  @Test
  void getTopPerformers_limits_results() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(3000), EUR),
            OPENED_AT,
            null,
            "dep"));
    for (String ticker : List.of("AAPL", "MSFT", "GOOG")) {
      pea.recordTransaction(
          Transaction.ofEur(
              TransactionId.generate(),
              TransactionType.BUY,
              new Ticker(ticker),
              BigDecimal.ONE,
              new Money(BigDecimal.valueOf(1000), EUR),
              new Money(BigDecimal.valueOf(1000), EUR),
              OPENED_AT.plusDays(1),
              null,
              "buy " + ticker));
    }

    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(pea));
    when(marketDataPort.getQuotes(anyList()))
        .thenReturn(
            Map.of(
                "AAPL",
                new Quote(
                    "AAPL",
                    BigDecimal.valueOf(1100),
                    "EUR",
                    "Apple",
                    Instant.now(),
                    BigDecimal.ZERO),
                "MSFT",
                new Quote(
                    "MSFT",
                    BigDecimal.valueOf(1050),
                    "EUR",
                    "Microsoft",
                    Instant.now(),
                    BigDecimal.ZERO),
                "GOOG",
                new Quote(
                    "GOOG",
                    BigDecimal.valueOf(1200),
                    "EUR",
                    "Google",
                    Instant.now(),
                    BigDecimal.ZERO)));
    when(fxRatePort.getRate("EUR", "EUR")).thenReturn(Optional.of(BigDecimal.ONE));

    List<TopPerformer> performers = service.getTopPerformers(USER_ID, "EUR", 2);

    assertThat(performers).hasSize(2);
  }

  @Test
  void computeBalanceAsOf_subtracts_buy_from_balance() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(2000), EUR),
            OPENED_AT,
            null,
            "dep"));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            BigDecimal.TEN,
            new Money(BigDecimal.valueOf(100), EUR),
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "buy"));

    FinancialAccountId accountId = pea.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(pea));
    when(marketDataPort.getHistoricalPrices(anyString(), any(), any())).thenReturn(Map.of());
    when(marketDataPort.getQuotes(anyList())).thenReturn(Map.of());

    List<PortfolioHistoryPoint> points =
        service.getAccountHistory(accountId, USER_ID, Period.ONE_WEEK, "EUR");

    // cash = DEPOSIT(2000) - BUY(1000) = 1000; holdings fallback = totalInvested(1000)
    // totalValue = 1000 + 1000 = 2000 (not 3000 if BUY were ignored)
    PortfolioHistoryPoint last = points.get(points.size() - 1);
    assertThat(last.totalValue()).isEqualByComparingTo(new BigDecimal("2000.00"));
  }

  @Test
  void computeBalanceAsOf_adds_sell_to_balance() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(2000), EUR),
            OPENED_AT,
            null,
            "dep"));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            BigDecimal.valueOf(20),
            new Money(BigDecimal.valueOf(100), EUR),
            new Money(BigDecimal.valueOf(2000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "buy 20"));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.SELL,
            new Ticker("AAPL"),
            BigDecimal.TEN,
            new Money(BigDecimal.valueOf(150), EUR),
            new Money(BigDecimal.valueOf(1500), EUR),
            OPENED_AT.plusDays(2),
            null,
            "sell 10"));

    FinancialAccountId accountId = pea.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(pea));
    when(marketDataPort.getHistoricalPrices(anyString(), any(), any())).thenReturn(Map.of());
    when(marketDataPort.getQuotes(anyList())).thenReturn(Map.of());

    List<PortfolioHistoryPoint> points =
        service.getAccountHistory(accountId, USER_ID, Period.ONE_WEEK, "EUR");

    // cash = DEPOSIT(2000) - BUY(2000) + SELL(1500) = 1500
    // remaining 10 shares: totalInvested = 10 × 100 (French fiscal rule) = 1000 (fallback)
    // totalValue = 1000 + 1500 = 2500
    PortfolioHistoryPoint last = points.get(points.size() - 1);
    assertThat(last.totalValue()).isEqualByComparingTo(new BigDecimal("2500.00"));
  }

  @Test
  void getAccountHistory_last_point_matches_live_portfolio_value() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT,
            null,
            "dep"));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AIR.PA"),
            BigDecimal.TEN,
            new Money(BigDecimal.valueOf(100), EUR),
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "buy"));

    FinancialAccountId accountId = pea.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(pea));
    when(marketDataPort.getHistoricalPrices(anyString(), any(), any())).thenReturn(Map.of());
    when(marketDataPort.getQuotes(anyList()))
        .thenReturn(
            Map.of(
                "AIR.PA",
                new Quote(
                    "AIR.PA",
                    BigDecimal.valueOf(150),
                    "EUR",
                    "Airbus",
                    Instant.now(),
                    BigDecimal.ZERO)));

    List<PortfolioHistoryPoint> points =
        service.getAccountHistory(accountId, USER_ID, Period.ONE_WEEK, "EUR");

    // cash = DEPOSIT(1000) - BUY(1000) = 0
    // live holdings = 10 × 150 EUR = 1500 (EUR quote, EUR target → fxRate = 1)
    // totalValue = 1500 + 0 = 1500; costBasis = 10 × 100 = 1000; pnl = 500
    PortfolioHistoryPoint last = points.get(points.size() - 1);
    assertThat(last.totalValue()).isEqualByComparingTo(new BigDecimal("1500.00"));
    assertThat(last.invested()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(last.pnl()).isEqualByComparingTo(new BigDecimal("500.00"));
  }

  @Test
  void inferRegion_crypto_returns_crypto() {
    assertThat(service.inferRegion("BTC-USD")).isEqualTo("Crypto");
    assertThat(service.inferRegion("ETH-EUR")).isEqualTo("Crypto");
  }

  @Test
  void getPortfolioHistoryByType_filters_by_investment_type() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT,
            null,
            "dep"));

    FinancialAccount cash = cashAccount();
    cash.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(500), EUR),
            OPENED_AT,
            null,
            "dep cash"));

    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(pea, cash));

    List<PortfolioHistoryPoint> points =
        service.getPortfolioHistoryByType(USER_ID, "CASH", Period.ONE_WEEK, "EUR");

    // Only the CASH_ACCOUNT is included — PEA deposits must not appear
    assertThat(points)
        .isNotEmpty()
        .allMatch(
            p ->
                p.totalValue().compareTo(BigDecimal.valueOf(500)) <= 0
                    && p.totalValue().compareTo(BigDecimal.ZERO) >= 0);
  }

  @Test
  void getPortfolioHistoryByType_returns_empty_when_no_accounts_match() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT,
            null,
            "dep"));

    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(pea));

    // Request CRYPTO category — no CRYPTO_WALLET accounts exist
    List<PortfolioHistoryPoint> points =
        service.getPortfolioHistoryByType(USER_ID, "CRYPTO", Period.ONE_WEEK, "EUR");

    assertThat(points).isEmpty();
  }

  @Test
  void getAccountHistory_all_period_starts_from_openedAt() {
    LocalDate openedAt = LocalDate.now().minusDays(30);
    FinancialAccount cash =
        FinancialAccount.open(
            "Cash",
            AccountType.CASH_ACCOUNT,
            new Money(BigDecimal.ZERO, EUR),
            "BNP",
            USER_ID,
            null,
            openedAt);
    cash.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(500), EUR),
            openedAt,
            null,
            "dep"));

    FinancialAccountId accountId = cash.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(cash));

    List<PortfolioHistoryPoint> points =
        service.getAccountHistory(accountId, USER_ID, Period.ALL, "EUR");

    assertThat(points).isNotEmpty();
    // ALL period starts from openedAt, not 10 years ago
    assertThat(points.get(0).date()).isBetween(openedAt, openedAt.plusDays(6));
  }

  @Test
  void getAccountHistory_returns_single_point_when_no_trading_days() {
    // openedAt set to tomorrow so from > today, producing an empty tradingDays list
    LocalDate tomorrow = LocalDate.now().plusDays(1);
    FinancialAccount cash =
        FinancialAccount.open(
            "Future Cash",
            AccountType.CASH_ACCOUNT,
            new Money(BigDecimal.ZERO, EUR),
            "BNP",
            USER_ID,
            null,
            tomorrow);

    FinancialAccountId accountId = cash.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(cash));

    List<PortfolioHistoryPoint> points =
        service.getAccountHistory(accountId, USER_ID, Period.ALL, "EUR");

    assertThat(points).hasSize(1);
    assertThat(points.get(0).date()).isEqualTo(LocalDate.now(ZoneId.of("Europe/Paris")));
    assertThat(points.get(0).pnl()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void getAccountHistory_last_point_uses_live_price() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT,
            null,
            "dep"));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            BigDecimal.TEN,
            new Money(BigDecimal.valueOf(100), EUR),
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "buy"));

    FinancialAccountId accountId = pea.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(pea));
    when(marketDataPort.getHistoricalPrices(anyString(), any(), any())).thenReturn(Map.of());
    when(marketDataPort.getQuotes(anyList()))
        .thenReturn(
            Map.of(
                "AAPL",
                new Quote(
                    "AAPL",
                    BigDecimal.valueOf(150),
                    "USD",
                    "Apple",
                    Instant.now(),
                    BigDecimal.ZERO)));
    when(fxRatePort.getRate("USD", "EUR")).thenReturn(Optional.of(BigDecimal.ONE));

    service.getAccountHistory(accountId, USER_ID, Period.ONE_WEEK, "EUR");

    // Verify live quotes are fetched once for today's data point
    verify(marketDataPort).getQuotes(anyList());
  }

  @Test
  void getAccountHistory_returns_points_for_investment_account() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT,
            null,
            "dep"));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            BigDecimal.TEN,
            new Money(BigDecimal.valueOf(100), EUR),
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "buy"));

    FinancialAccountId accountId = pea.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(pea));
    LocalDate priceDate = LocalDate.now().minusDays(3);
    when(marketDataPort.getHistoricalPrices(eq("AAPL"), any(), any()))
        .thenReturn(Map.of(priceDate, BigDecimal.valueOf(120)));
    when(fxRatePort.getRate("USD", "EUR")).thenReturn(Optional.of(BigDecimal.ONE));

    List<PortfolioHistoryPoint> points =
        service.getAccountHistory(accountId, USER_ID, Period.ONE_WEEK, "EUR");

    assertThat(points).isNotEmpty().allMatch(p -> p.totalValue().compareTo(BigDecimal.ZERO) >= 0);
  }

  @Test
  void getAccountHistory_respects_account_openedAt() {
    LocalDate recentOpen = LocalDate.now().minusDays(5);
    FinancialAccount cash =
        FinancialAccount.open(
            "Recent",
            AccountType.CASH_ACCOUNT,
            new Money(BigDecimal.ZERO, EUR),
            "BNP",
            USER_ID,
            null,
            recentOpen);
    cash.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(500), EUR),
            recentOpen,
            null,
            "dep"));

    FinancialAccountId accountId = cash.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(cash));

    List<PortfolioHistoryPoint> points =
        service.getAccountHistory(accountId, USER_ID, Period.ONE_MONTH, "EUR");

    assertThat(points).isNotEmpty();
    assertThat(points.get(0).date()).isAfterOrEqualTo(recentOpen);
  }

  @Test
  void matchesCategory_investment_includes_pea_excludes_crypto_wallet() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT,
            null,
            "dep"));
    FinancialAccount crypto =
        FinancialAccount.open(
            "Crypto",
            AccountType.CRYPTO_WALLET,
            new Money(BigDecimal.ZERO, EUR),
            "Binance",
            USER_ID,
            null,
            OPENED_AT);
    crypto.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(500), EUR),
            OPENED_AT,
            null,
            "dep crypto"));

    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(pea, crypto));

    List<PortfolioHistoryPoint> points =
        service.getPortfolioHistoryByType(USER_ID, "INVESTMENT", Period.ONE_WEEK, "EUR");

    // PEA included (investment, not crypto), CRYPTO_WALLET excluded → only 1000 visible
    assertThat(points).isNotEmpty();
    assertThat(points.get(points.size() - 1).totalValue())
        .isEqualByComparingTo(new BigDecimal("1000.00"));
  }

  @Test
  void matchesCategory_crypto_includes_crypto_wallet() {
    FinancialAccount crypto =
        FinancialAccount.open(
            "BTC Wallet",
            AccountType.CRYPTO_WALLET,
            new Money(BigDecimal.ZERO, EUR),
            "Binance",
            USER_ID,
            null,
            OPENED_AT);
    crypto.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(2000), EUR),
            OPENED_AT,
            null,
            "dep"));

    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(crypto));

    List<PortfolioHistoryPoint> points =
        service.getPortfolioHistoryByType(USER_ID, "CRYPTO", Period.ONE_WEEK, "EUR");

    assertThat(points).isNotEmpty();
    assertThat(points.get(points.size() - 1).totalValue())
        .isEqualByComparingTo(new BigDecimal("2000.00"));
  }

  @Test
  void matchesCategory_savings_includes_savings_account() {
    FinancialAccount savings =
        FinancialAccount.open(
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(BigDecimal.ZERO, EUR),
            "BNP",
            USER_ID,
            null,
            OPENED_AT);
    savings.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(800), EUR),
            OPENED_AT,
            null,
            "dep"));
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT,
            null,
            "dep pea"));

    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(savings, pea));

    List<PortfolioHistoryPoint> points =
        service.getPortfolioHistoryByType(USER_ID, "SAVINGS", Period.ONE_WEEK, "EUR");

    // Only savings account included → max 800
    assertThat(points).isNotEmpty();
    assertThat(points.get(points.size() - 1).totalValue())
        .isEqualByComparingTo(new BigDecimal("800.00"));
  }

  @Test
  void matchesCategory_cash_includes_cash_account() {
    FinancialAccount cash = cashAccount();
    cash.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(300), EUR),
            OPENED_AT,
            null,
            "dep"));
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT,
            null,
            "dep pea"));

    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(cash, pea));

    List<PortfolioHistoryPoint> points =
        service.getPortfolioHistoryByType(USER_ID, "CASH", Period.ONE_WEEK, "EUR");

    // Only CASH_ACCOUNT included → max 300
    assertThat(points).isNotEmpty();
    assertThat(points.get(points.size() - 1).totalValue())
        .isEqualByComparingTo(new BigDecimal("300.00"));
  }

  @Test
  void matchesCategory_unknown_category_includes_all_accounts() {
    FinancialAccount pea = peaAccount();
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT,
            null,
            "dep pea"));
    FinancialAccount cash = cashAccount();
    cash.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(500), EUR),
            OPENED_AT,
            null,
            "dep cash"));

    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(pea, cash));

    List<PortfolioHistoryPoint> points =
        service.getPortfolioHistoryByType(USER_ID, "UNKNOWN_CATEGORY", Period.ONE_WEEK, "EUR");

    // Default → all accounts included → 1500
    assertThat(points).isNotEmpty();
    assertThat(points.get(points.size() - 1).totalValue())
        .isEqualByComparingTo(new BigDecimal("1500.00"));
  }

  @Test
  void computeCurrentValue_investment_account_with_no_holdings_returns_zero() {
    // openedAt in the future forces Period.ALL to produce empty tradingDays list,
    // triggering the computeCurrentValue path for an investment account
    LocalDate tomorrow = LocalDate.now().plusDays(1);
    FinancialAccount pea =
        FinancialAccount.open(
            "Future PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            USER_ID,
            null,
            tomorrow);

    FinancialAccountId accountId = pea.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(pea));

    List<PortfolioHistoryPoint> points =
        service.getAccountHistory(accountId, USER_ID, Period.ALL, "EUR");

    assertThat(points).hasSize(1);
    assertThat(points.get(0).totalValue()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void getAccountHistory_throws_when_account_not_found() {
    FinancialAccountId accountId = FinancialAccountId.generate();
    when(repository.findById(accountId)).thenReturn(Optional.empty());

    Assertions.assertThrows(
        AccountNotFoundException.class,
        () -> service.getAccountHistory(accountId, USER_ID, Period.ONE_WEEK, "EUR"));
  }

  @Test
  void periodToStartDate_ytd_starts_at_beginning_of_current_year() {
    when(repository.findAllByOwnerId(USER_ID)).thenReturn(List.of(cashAccount()));

    List<PortfolioHistoryPoint> points = service.getPortfolioHistory(USER_ID, Period.YTD, "EUR");

    LocalDate startOfYear = LocalDate.now(ZoneId.of("Europe/Paris")).withDayOfYear(1);
    assertThat(points).isNotEmpty();
    assertThat(points.get(0).date()).isAfterOrEqualTo(startOfYear);
  }

  @Test
  void periodToStartDate_all_returns_openedAt_as_start() {
    LocalDate openedAt = LocalDate.now().minusDays(20);
    FinancialAccount cash =
        FinancialAccount.open(
            "Cash",
            AccountType.CASH_ACCOUNT,
            new Money(BigDecimal.ZERO, EUR),
            "BNP",
            USER_ID,
            null,
            openedAt);
    cash.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(500), EUR),
            openedAt,
            null,
            "dep"));

    FinancialAccountId accountId = cash.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(cash));

    List<PortfolioHistoryPoint> points =
        service.getAccountHistory(accountId, USER_ID, Period.ALL, "EUR");

    assertThat(points).isNotEmpty();
    assertThat(points.get(0).date()).isBetween(openedAt, openedAt.plusWeeks(1));
  }

  @Test
  void getAccountHistory_returns_balance_only_for_savings_account() {
    FinancialAccount cash = cashAccount();
    cash.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), EUR),
            OPENED_AT.plusDays(1),
            null,
            "dep"));

    FinancialAccountId accountId = cash.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(cash));

    List<PortfolioHistoryPoint> points =
        service.getAccountHistory(accountId, USER_ID, Period.ONE_WEEK, "EUR");

    assertThat(points).isNotEmpty().allMatch(p -> p.pnl().compareTo(BigDecimal.ZERO) == 0);
  }
}
