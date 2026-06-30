package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransferDirection;
import com.equily.portfolio.domain.analytics.GeographicExposure;
import com.equily.portfolio.domain.analytics.PortfolioHistoryPoint;
import com.equily.portfolio.domain.analytics.TopPerformer;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.portfolio.domain.marketdata.FxRatePort;
import com.equily.portfolio.domain.marketdata.MarketDataPort;
import com.equily.portfolio.domain.marketdata.Quote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class PortfolioAnalyticsService implements PortfolioAnalyticsUseCase {

  private static final Set<AccountType> INVESTMENT_ACCOUNT_TYPES =
      Set.of(
          AccountType.PEA,
          AccountType.PEA_PME,
          AccountType.COMPTE_TITRES,
          AccountType.PER,
          AccountType.ASSURANCE_VIE,
          AccountType.CRYPTO_WALLET);

  private static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");

  private final FinancialAccountRepository repository;
  private final MarketDataPort marketDataPort;
  private final FxRatePort fxRatePort;

  PortfolioAnalyticsService(
      FinancialAccountRepository repository, MarketDataPort marketDataPort, FxRatePort fxRatePort) {
    this.repository = repository;
    this.marketDataPort = marketDataPort;
    this.fxRatePort = fxRatePort;
  }

  @Override
  public List<PortfolioHistoryPoint> getPortfolioHistory(
      UserId userId, Period period, String targetCurrency) {
    LocalDate today = LocalDate.now(PARIS_ZONE);
    LocalDate from = periodToStartDate(period, today);
    List<FinancialAccount> accounts = repository.findAllByOwnerId(userId);
    return computeHistoryForAccounts(accounts, from, today, targetCurrency);
  }

  @Override
  public List<PortfolioHistoryPoint> getPortfolioHistoryByType(
      UserId userId, String accountTypeCategory, Period period, String targetCurrency) {
    LocalDate today = LocalDate.now(PARIS_ZONE);
    LocalDate from = periodToStartDate(period, today);
    List<FinancialAccount> accounts =
        repository.findAllByOwnerId(userId).stream()
            .filter(a -> matchesCategory(a, accountTypeCategory))
            .filter(a -> !a.isClosed())
            .toList();
    if (accounts.isEmpty()) return List.of();
    return computeHistoryForAccounts(accounts, from, today, targetCurrency);
  }

  private boolean matchesCategory(FinancialAccount account, String category) {
    return switch (category.toUpperCase()) {
      case "INVESTMENT" ->
          INVESTMENT_ACCOUNT_TYPES.contains(account.accountType())
              && account.accountType() != AccountType.CRYPTO_WALLET;
      case "CRYPTO" -> account.accountType() == AccountType.CRYPTO_WALLET;
      case "SAVINGS" -> account.accountType() == AccountType.SAVINGS_ACCOUNT;
      case "CASH" -> account.accountType() == AccountType.CASH_ACCOUNT;
      default -> true;
    };
  }

  private List<PortfolioHistoryPoint> computeHistoryForAccounts(
      List<FinancialAccount> accounts, LocalDate from, LocalDate today, String targetCurrency) {
    BigDecimal eurToTarget = resolveEurToTarget(targetCurrency);
    Map<String, Map<LocalDate, BigDecimal>> allPrices =
        fetchAllHistoricalPrices(accounts, from, today);
    Map<String, Quote> liveQuotes = fetchAllLiveQuotes(accounts);
    List<LocalDate> tradingDays = buildTradingDays(from, today);
    return tradingDays.stream()
        .map(
            date ->
                computePointForDate(
                    accounts, date, today, allPrices, liveQuotes, eurToTarget, targetCurrency))
        .toList();
  }

  private PortfolioHistoryPoint computePointForDate(
      List<FinancialAccount> accounts,
      LocalDate date,
      LocalDate today,
      Map<String, Map<LocalDate, BigDecimal>> allPrices,
      Map<String, Quote> liveQuotes,
      BigDecimal eurToTarget,
      String targetCurrency) {
    BigDecimal totalValue = BigDecimal.ZERO;
    BigDecimal totalCost = BigDecimal.ZERO;
    boolean isToday = date.equals(today);
    for (FinancialAccount account : accounts) {
      AccountValues vals =
          computeAccountValues(
              account, date, isToday, allPrices, liveQuotes, eurToTarget, targetCurrency);
      totalValue = totalValue.add(vals.value());
      totalCost = totalCost.add(vals.cost());
    }
    return new PortfolioHistoryPoint(
        date,
        totalValue.setScale(2, RoundingMode.HALF_EVEN),
        totalCost.setScale(2, RoundingMode.HALF_EVEN),
        totalValue.subtract(totalCost).setScale(2, RoundingMode.HALF_EVEN));
  }

  private record AccountValues(BigDecimal value, BigDecimal cost) {}

  private AccountValues computeAccountValues(
      FinancialAccount account,
      LocalDate date,
      boolean isToday,
      Map<String, Map<LocalDate, BigDecimal>> allPrices,
      Map<String, Quote> liveQuotes,
      BigDecimal eurToTarget,
      String targetCurrency) {
    if (account.isClosed() && account.closedAt() != null && account.closedAt().isBefore(date)) {
      return new AccountValues(BigDecimal.ZERO, BigDecimal.ZERO);
    }
    if (INVESTMENT_ACCOUNT_TYPES.contains(account.accountType())) {
      return computeInvestmentValues(
          account, date, isToday, allPrices, liveQuotes, eurToTarget, targetCurrency);
    }
    BigDecimal balance =
        computeBalanceAsOf(account, date).multiply(eurToTarget).setScale(2, RoundingMode.HALF_EVEN);
    return new AccountValues(balance, balance);
  }

  private AccountValues computeInvestmentValues(
      FinancialAccount account,
      LocalDate date,
      boolean isToday,
      Map<String, Map<LocalDate, BigDecimal>> allPrices,
      Map<String, Quote> liveQuotes,
      BigDecimal eurToTarget,
      String targetCurrency) {
    List<Transaction> txUpToDate =
        account.transactions().stream().filter(t -> !t.date().isAfter(date)).toList();
    List<Holding> holdings = Holding.computeFrom(txUpToDate);
    BigDecimal holdingsValue = BigDecimal.ZERO;
    BigDecimal costBasis = BigDecimal.ZERO;
    for (Holding h : holdings) {
      BigDecimal price = resolvePrice(h, date, isToday, allPrices, liveQuotes, targetCurrency);
      if (price != null) {
        holdingsValue =
            holdingsValue.add(price.multiply(h.quantity()).setScale(2, RoundingMode.HALF_EVEN));
      } else {
        holdingsValue =
            holdingsValue.add(
                h.totalInvested()
                    .amount()
                    .multiply(eurToTarget)
                    .setScale(2, RoundingMode.HALF_EVEN));
      }
      costBasis =
          costBasis.add(
              h.totalInvested().amount().multiply(eurToTarget).setScale(2, RoundingMode.HALF_EVEN));
    }
    BigDecimal cashBalance =
        computeBalanceAsOf(account, date).multiply(eurToTarget).setScale(2, RoundingMode.HALF_EVEN);
    return new AccountValues(holdingsValue.add(cashBalance), costBasis.add(cashBalance));
  }

  private BigDecimal resolvePrice(
      Holding h,
      LocalDate date,
      boolean isToday,
      Map<String, Map<LocalDate, BigDecimal>> allPrices,
      Map<String, Quote> liveQuotes,
      String targetCurrency) {
    String ticker = h.ticker().symbol();
    if (isToday) {
      Quote liveQuote = liveQuotes.get(ticker);
      if (liveQuote != null) {
        String quoteCurrency = liveQuote.currency() != null ? liveQuote.currency() : "USD";
        BigDecimal fxRate =
            quoteCurrency.equals(targetCurrency)
                ? BigDecimal.ONE
                : fxRatePort.getRate(quoteCurrency, targetCurrency).orElse(BigDecimal.ONE);
        return liveQuote.price().multiply(fxRate);
      }
    }
    BigDecimal historicalPrice = getClosestPrice(allPrices.getOrDefault(ticker, Map.of()), date);
    if (historicalPrice == null) return null;
    String quoteCurrency = inferQuoteCurrency(ticker);
    BigDecimal fxRate =
        quoteCurrency.equals(targetCurrency)
            ? BigDecimal.ONE
            : fxRatePort.getRate(quoteCurrency, targetCurrency).orElse(BigDecimal.ONE);
    return historicalPrice.multiply(fxRate);
  }

  private BigDecimal resolveEurToTarget(String targetCurrency) {
    return "EUR".equals(targetCurrency)
        ? BigDecimal.ONE
        : fxRatePort.getRate("EUR", targetCurrency).orElse(BigDecimal.ONE);
  }

  private Map<String, Map<LocalDate, BigDecimal>> fetchAllHistoricalPrices(
      List<FinancialAccount> accounts, LocalDate from, LocalDate today) {
    Map<String, Map<LocalDate, BigDecimal>> allPrices = new HashMap<>();
    for (FinancialAccount account : accounts) {
      if (INVESTMENT_ACCOUNT_TYPES.contains(account.accountType())) {
        Holding.computeFrom(account.transactions())
            .forEach(
                h ->
                    allPrices.put(
                        h.ticker().symbol(),
                        marketDataPort.getHistoricalPrices(h.ticker().symbol(), from, today)));
      }
    }
    return allPrices;
  }

  private Map<String, Quote> fetchAllLiveQuotes(List<FinancialAccount> accounts) {
    List<String> tickers =
        accounts.stream()
            .filter(a -> INVESTMENT_ACCOUNT_TYPES.contains(a.accountType()))
            .flatMap(a -> Holding.computeFrom(a.transactions()).stream())
            .map(h -> h.ticker().symbol())
            .distinct()
            .toList();
    return tickers.isEmpty() ? Map.of() : marketDataPort.getQuotes(tickers);
  }

  @Override
  public List<GeographicExposure> getGeographicExposure(
      FinancialAccountId accountId, UserId userId, String targetCurrency) {
    FinancialAccount account =
        repository
            .findById(accountId)
            .filter(a -> a.ownerId().equals(userId))
            .orElseThrow(() -> new AccountNotFoundException(accountId));

    List<Holding> holdings = Holding.computeFrom(account.transactions());
    if (holdings.isEmpty()) return List.of();

    List<String> symbols = holdings.stream().map(h -> h.ticker().symbol()).distinct().toList();
    Map<String, Quote> quotes = marketDataPort.getQuotes(symbols);

    BigDecimal eurToTarget =
        targetCurrency.equals("EUR")
            ? BigDecimal.ONE
            : fxRatePort.getRate("EUR", targetCurrency).orElse(BigDecimal.ONE);

    Map<String, BigDecimal> regionValues = new LinkedHashMap<>();
    BigDecimal total = BigDecimal.ZERO;

    for (Holding h : holdings) {
      Quote q = quotes.get(h.ticker().symbol());
      BigDecimal price = q != null ? q.price() : h.averageCostPrice().amount();

      BigDecimal fxRate =
          q != null
              ? fxRatePort.getRate(q.currency(), targetCurrency).orElse(BigDecimal.ONE)
              : eurToTarget;

      BigDecimal value =
          price.multiply(fxRate).multiply(h.quantity()).setScale(2, RoundingMode.HALF_EVEN);

      String region = inferRegion(h.ticker().symbol());
      regionValues.merge(region, value, BigDecimal::add);
      total = total.add(value);
    }

    if (total.compareTo(BigDecimal.ZERO) == 0) return List.of();

    BigDecimal finalTotal = total;
    return regionValues.entrySet().stream()
        .map(
            e ->
                new GeographicExposure(
                    e.getKey(),
                    e.getValue().setScale(2, RoundingMode.HALF_EVEN),
                    e.getValue()
                        .divide(finalTotal, 4, RoundingMode.HALF_EVEN)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_EVEN)))
        .sorted(Comparator.comparing(GeographicExposure::value).reversed())
        .toList();
  }

  @Override
  public List<TopPerformer> getTopPerformers(UserId userId, String targetCurrency, int limit) {
    List<FinancialAccount> accounts =
        repository.findAllByOwnerId(userId).stream()
            .filter(a -> !a.isClosed())
            .filter(a -> INVESTMENT_ACCOUNT_TYPES.contains(a.accountType()))
            .toList();

    BigDecimal eurToTarget =
        targetCurrency.equals("EUR")
            ? BigDecimal.ONE
            : fxRatePort.getRate("EUR", targetCurrency).orElse(BigDecimal.ONE);

    List<TopPerformer> performers = new ArrayList<>();

    for (FinancialAccount account : accounts) {
      List<Holding> holdings = Holding.computeFrom(account.transactions());
      if (holdings.isEmpty()) continue;

      List<String> symbols = holdings.stream().map(h -> h.ticker().symbol()).distinct().toList();
      Map<String, Quote> quotes = marketDataPort.getQuotes(symbols);

      for (Holding h : holdings) {
        Quote q = quotes.get(h.ticker().symbol());
        if (q == null) continue;

        BigDecimal liveToTarget =
            fxRatePort.getRate(q.currency(), targetCurrency).orElse(BigDecimal.ONE);

        BigDecimal currentValue =
            q.price()
                .multiply(liveToTarget)
                .multiply(h.quantity())
                .setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal invested =
            h.totalInvested().amount().multiply(eurToTarget).setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal pnl = currentValue.subtract(invested).setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal pnlPct =
            invested.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : pnl.divide(invested, 4, RoundingMode.HALF_EVEN)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_EVEN);

        performers.add(
            new TopPerformer(
                h.ticker().symbol(),
                account.name(),
                currentValue,
                invested,
                pnl,
                pnlPct,
                q.changePercent()));
      }
    }

    return performers.stream()
        .sorted(Comparator.comparing(TopPerformer::pnlPercent).reversed())
        .limit(limit)
        .toList();
  }

  @Override
  public List<PortfolioHistoryPoint> getAccountHistory(
      FinancialAccountId accountId, UserId userId, Period period, String targetCurrency) {

    FinancialAccount account =
        repository
            .findById(accountId)
            .filter(a -> a.ownerId().equals(userId))
            .orElseThrow(() -> new AccountNotFoundException(accountId));

    LocalDate today = LocalDate.now(PARIS_ZONE);
    LocalDate from = periodToStartDate(period, today, account.openedAt());

    BigDecimal eurToTarget =
        targetCurrency.equals("EUR")
            ? BigDecimal.ONE
            : fxRatePort.getRate("EUR", targetCurrency).orElse(BigDecimal.ONE);

    BigDecimal usdToTarget =
        targetCurrency.equals("USD")
            ? BigDecimal.ONE
            : fxRatePort.getRate("USD", targetCurrency).orElse(BigDecimal.ONE);

    boolean isInvestment = INVESTMENT_ACCOUNT_TYPES.contains(account.accountType());

    List<Holding> currentHoldings =
        isInvestment ? Holding.computeFrom(account.transactions()) : List.of();

    Map<String, Map<LocalDate, BigDecimal>> allPrices = new HashMap<>();
    for (Holding h : currentHoldings) {
      allPrices.put(
          h.ticker().symbol(),
          marketDataPort.getHistoricalPrices(h.ticker().symbol(), from, today));
    }

    Map<String, Quote> liveQuotes =
        currentHoldings.isEmpty()
            ? Map.of()
            : marketDataPort.getQuotes(
                currentHoldings.stream().map(h -> h.ticker().symbol()).distinct().toList());

    List<LocalDate> tradingDays = buildTradingDays(from, today);

    if (tradingDays.isEmpty()) {
      BigDecimal currentValue = computeCurrentValue(account, liveQuotes, eurToTarget, usdToTarget);
      return List.of(new PortfolioHistoryPoint(today, currentValue, currentValue, BigDecimal.ZERO));
    }

    List<PortfolioHistoryPoint> points = new ArrayList<>();

    for (LocalDate date : tradingDays) {
      boolean isToday = date.equals(today);
      BigDecimal totalValue;
      BigDecimal totalCost;

      if (isInvestment) {
        List<Transaction> txUpToDate =
            account.transactions().stream().filter(t -> !t.date().isAfter(date)).toList();
        List<Holding> holdingsAsOfDate = Holding.computeFrom(txUpToDate);

        BigDecimal holdingsValue = BigDecimal.ZERO;
        BigDecimal costBasis = BigDecimal.ZERO;

        for (Holding h : holdingsAsOfDate) {
          BigDecimal priceInTarget = null;
          if (isToday) {
            Quote liveQuote = liveQuotes.get(h.ticker().symbol());
            if (liveQuote != null) {
              String quoteCurrency = liveQuote.currency() != null ? liveQuote.currency() : "USD";
              BigDecimal fxRate =
                  quoteCurrency.equals(targetCurrency)
                      ? BigDecimal.ONE
                      : fxRatePort.getRate(quoteCurrency, targetCurrency).orElse(BigDecimal.ONE);
              priceInTarget = liveQuote.price().multiply(fxRate);
            }
          }
          if (priceInTarget == null) {
            BigDecimal raw =
                getClosestPrice(allPrices.getOrDefault(h.ticker().symbol(), Map.of()), date);
            if (raw != null) {
              String quoteCurrency = inferQuoteCurrency(h.ticker().symbol());
              BigDecimal fxRate =
                  quoteCurrency.equals(targetCurrency)
                      ? BigDecimal.ONE
                      : fxRatePort.getRate(quoteCurrency, targetCurrency).orElse(BigDecimal.ONE);
              priceInTarget = raw.multiply(fxRate);
            }
          }

          if (priceInTarget != null) {
            holdingsValue =
                holdingsValue.add(
                    priceInTarget.multiply(h.quantity()).setScale(2, RoundingMode.HALF_EVEN));
          } else {
            holdingsValue =
                holdingsValue.add(
                    h.totalInvested()
                        .amount()
                        .multiply(eurToTarget)
                        .setScale(2, RoundingMode.HALF_EVEN));
          }
          costBasis =
              costBasis.add(
                  h.totalInvested()
                      .amount()
                      .multiply(eurToTarget)
                      .setScale(2, RoundingMode.HALF_EVEN));
        }

        BigDecimal cashConverted = computeBalanceAsOf(account, date).multiply(eurToTarget);
        totalValue = holdingsValue.add(cashConverted).setScale(2, RoundingMode.HALF_EVEN);
        totalCost = costBasis.add(cashConverted).setScale(2, RoundingMode.HALF_EVEN);
      } else {
        BigDecimal balance =
            computeBalanceAsOf(account, date)
                .multiply(eurToTarget)
                .setScale(2, RoundingMode.HALF_EVEN);
        totalValue = balance;
        totalCost = balance;
      }

      BigDecimal pnl = totalValue.subtract(totalCost).setScale(2, RoundingMode.HALF_EVEN);
      points.add(new PortfolioHistoryPoint(date, totalValue, totalCost, pnl));
    }
    return points;
  }

  private BigDecimal computeCurrentValue(
      FinancialAccount account,
      Map<String, Quote> liveQuotes,
      BigDecimal eurToTarget,
      BigDecimal usdToTarget) {
    BigDecimal cashBalance =
        computeBalanceAsOf(account, LocalDate.now(PARIS_ZONE)).multiply(eurToTarget);
    if (!INVESTMENT_ACCOUNT_TYPES.contains(account.accountType())) {
      return cashBalance.setScale(2, RoundingMode.HALF_EVEN);
    }
    List<Holding> holdings = Holding.computeFrom(account.transactions());
    BigDecimal holdingsValue = BigDecimal.ZERO;
    for (Holding h : holdings) {
      Quote q = liveQuotes.get(h.ticker().symbol());
      BigDecimal price = q != null ? q.price() : h.averageCostPrice().amount();
      BigDecimal fx = q != null ? usdToTarget : eurToTarget;
      holdingsValue =
          holdingsValue.add(
              price.multiply(fx).multiply(h.quantity()).setScale(2, RoundingMode.HALF_EVEN));
    }
    return holdingsValue.add(cashBalance).setScale(2, RoundingMode.HALF_EVEN);
  }

  private BigDecimal computeBalanceAsOf(FinancialAccount account, LocalDate date) {
    return account.transactions().stream()
        .filter(t -> !t.date().isAfter(date))
        .map(
            t ->
                switch (t.type()) {
                  case DEPOSIT, DIVIDEND, INTEREST, SELL -> t.totalAmount().amount();
                  case WITHDRAWAL, BUY, PAYMENT -> t.totalAmount().amount().negate();
                  case TRANSFER ->
                      t.transferDirection() == TransferDirection.INCOMING
                          ? t.totalAmount().amount()
                          : t.totalAmount().amount().negate();
                })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal getClosestPrice(Map<LocalDate, BigDecimal> prices, LocalDate date) {
    if (prices.containsKey(date)) return prices.get(date);
    return prices.entrySet().stream()
        .filter(e -> !e.getKey().isAfter(date))
        .max(Map.Entry.comparingByKey())
        .map(Map.Entry::getValue)
        .orElse(null);
  }

  private List<LocalDate> buildTradingDays(LocalDate from, LocalDate to) {
    List<LocalDate> days = new ArrayList<>();
    LocalDate current = from;
    while (!current.isAfter(to)) {
      DayOfWeek dow = current.getDayOfWeek();
      if ((dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) || current.equals(to)) {
        days.add(current);
      }
      current = current.plusDays(1);
    }
    return days;
  }

  private LocalDate periodToStartDate(Period period, LocalDate today) {
    return periodToStartDate(period, today, null);
  }

  private LocalDate periodToStartDate(Period period, LocalDate today, LocalDate accountOpenedAt) {
    LocalDate base =
        switch (period) {
          case ONE_DAY -> today.minusDays(1);
          case ONE_WEEK -> today.minusWeeks(1);
          case ONE_MONTH -> today.minusMonths(1);
          case YTD -> today.withDayOfYear(1);
          case ONE_YEAR -> today.minusYears(1);
          case ALL -> accountOpenedAt != null ? accountOpenedAt : today.minusYears(10);
        };
    if (period != Period.ALL && accountOpenedAt != null && accountOpenedAt.isAfter(base)) {
      return accountOpenedAt;
    }
    return base;
  }

  String inferQuoteCurrency(String ticker) {
    if (ticker.endsWith(".PA")
        || ticker.endsWith(".AS")
        || ticker.endsWith(".DE")
        || ticker.endsWith(".MI")
        || ticker.endsWith(".BR")
        || ticker.endsWith(".F")) return "EUR";
    if (ticker.endsWith(".L")) return "GBP";
    if (ticker.endsWith(".SW")) return "CHF";
    if (ticker.contains("-")) return "USD";
    return "USD";
  }

  String inferRegion(String ticker) {
    if (ticker.contains("-USD") || ticker.contains("-EUR")) return "Crypto";
    if (ticker.endsWith(".PA") || ticker.endsWith(".NX")) return "France";
    if (ticker.endsWith(".AS")) return "Netherlands";
    if (ticker.endsWith(".DE") || ticker.endsWith(".F")) return "Germany";
    if (ticker.endsWith(".L")) return "United Kingdom";
    if (ticker.endsWith(".MI")) return "Italy";
    if (ticker.endsWith(".BR")) return "Belgium";
    if (ticker.endsWith(".SW")) return "Switzerland";
    return "United States";
  }
}
