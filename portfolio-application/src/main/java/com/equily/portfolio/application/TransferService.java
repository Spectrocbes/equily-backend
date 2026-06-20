package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.TransferDirection;
import com.equily.portfolio.domain.TransferRoutingRules;
import com.equily.portfolio.domain.account.AccountBusinessRules;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.portfolio.domain.marketdata.FxRatePort;
import com.equily.portfolio.domain.marketdata.MarketDataPort;
import com.equily.portfolio.domain.marketdata.Quote;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class TransferService implements TransferUseCase {

  private static final BigDecimal PS_RATE = new BigDecimal("0.186");
  private static final Currency EUR = Currency.getInstance("EUR");

  private final FinancialAccountRepository repository;
  private final FxRatePort fxRatePort;
  private final MarketDataPort marketDataPort;

  TransferService(
      FinancialAccountRepository repository, FxRatePort fxRatePort, MarketDataPort marketDataPort) {
    this.repository = repository;
    this.fxRatePort = fxRatePort;
    this.marketDataPort = marketDataPort;
  }

  @Override
  public UUID executeTransfer(TransferCommand command) {
    FinancialAccount from = getAccount(command.fromAccountId(), command.userId());
    FinancialAccount to =
        command.toAccountId() != null ? getAccount(command.toAccountId(), command.userId()) : null;

    TransferRoutingRules.validate(from, to);

    String currency = command.currency() != null ? command.currency() : "EUR";
    BigDecimal eurFxRate =
        "EUR".equals(currency)
            ? BigDecimal.ONE
            : fxRatePort.getRateToEur(currency, command.date()).orElse(BigDecimal.ONE);
    BigDecimal amountEur = command.amount().multiply(eurFxRate).setScale(4, RoundingMode.HALF_EVEN);

    if (isPeaAccount(from) && to != null) {
      return executePeaTransfer(from, to, command, eurFxRate, amountEur);
    }

    return executeStandardTransfer(from, to, command, currency, eurFxRate, amountEur);
  }

  private UUID executeStandardTransfer(
      FinancialAccount from,
      FinancialAccount to,
      TransferCommand command,
      String currency,
      BigDecimal eurFxRate,
      BigDecimal amountEur) {

    UUID transferId = UUID.randomUUID();
    Money money = new Money(command.amount(), Currency.getInstance(currency));

    String descOut =
        command.description() != null
            ? command.description()
            : "Transfer to "
                + (to != null
                    ? to.name()
                    : (command.externalAddress() != null ? command.externalAddress() : "external"));

    Transaction txOut =
        Transaction.ofTransfer(
            TransactionId.generate(),
            money,
            command.date(),
            descOut,
            transferId,
            to != null ? to.id().value() : null,
            command.externalAddress(),
            amountEur,
            eurFxRate,
            TransferDirection.OUTGOING);
    from.recordTransaction(txOut);
    repository.save(from);

    if (to != null) {
      String descIn =
          command.description() != null ? command.description() : "Transfer from " + from.name();

      Transaction txIn =
          Transaction.ofTransfer(
              TransactionId.generate(),
              money,
              command.date(),
              descIn,
              transferId,
              from.id().value(),
              null,
              amountEur,
              eurFxRate,
              TransferDirection.INCOMING);
      to.recordTransaction(txIn);

      if (to.subType() != null) {
        List<FinancialAccount> allAccounts = repository.findAllByOwnerId(command.userId());
        AccountBusinessRules.validateDeposit(to, new Money(amountEur, EUR), allAccounts);
      }
      repository.save(to);
    }

    return transferId;
  }

  private UUID executePeaTransfer(
      FinancialAccount pea,
      FinancialAccount checking,
      TransferCommand command,
      BigDecimal eurFxRate,
      BigDecimal amountEur) {

    BigDecimal livePortfolioValue = computeLivePortfolioValue(pea);
    BigDecimal liquidationValue =
        livePortfolioValue.add(pea.balance().amount()).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal totalDeposits = computeAdjustedTotalDeposits(pea);

    boolean older5y = AccountBusinessRules.isPeaOlderThan5Years(pea);
    BigDecimal gainGlobal = liquidationValue.subtract(totalDeposits).max(BigDecimal.ZERO);
    BigDecimal gainRatio =
        liquidationValue.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : gainGlobal.divide(liquidationValue, 6, RoundingMode.HALF_EVEN);

    BigDecimal psTax = BigDecimal.ZERO;
    if (older5y && gainRatio.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal taxableGain =
          command.amount().multiply(gainRatio).setScale(2, RoundingMode.HALF_EVEN);
      psTax = taxableGain.multiply(PS_RATE).setScale(2, RoundingMode.HALF_EVEN);
    }
    BigDecimal netAmount = command.amount().subtract(psTax);

    UUID transferId = UUID.randomUUID();
    Money grossMoney = new Money(command.amount(), EUR);
    Money netMoney = new Money(netAmount, EUR);
    Money taxMoney = new Money(psTax, EUR);

    Transaction peaOut =
        Transaction.ofTransfer(
            TransactionId.generate(),
            grossMoney,
            command.date(),
            "PEA transfer to " + checking.name(),
            transferId,
            checking.id().value(),
            null,
            amountEur,
            eurFxRate,
            TransferDirection.OUTGOING);
    pea.recordTransaction(peaOut);

    Transaction checkingIn =
        Transaction.ofTransfer(
            TransactionId.generate(),
            netMoney,
            command.date(),
            "Transfer from PEA (net after PS tax)",
            transferId,
            pea.id().value(),
            null,
            netAmount.multiply(eurFxRate).setScale(4, RoundingMode.HALF_EVEN),
            eurFxRate,
            TransferDirection.INCOMING);
    checking.recordTransaction(checkingIn);

    if (psTax.compareTo(BigDecimal.ZERO) > 0) {
      Transaction taxTx =
          Transaction.ofEur(
              TransactionId.generate(),
              TransactionType.WITHDRAWAL,
              null,
              null,
              null,
              taxMoney,
              command.date(),
              BigDecimal.ZERO,
              String.format(
                  "PS tax (18.6%% on %.2f€ taxable gain)",
                  command.amount().multiply(gainRatio).setScale(2, RoundingMode.HALF_EVEN)));
      pea.recordTransaction(taxTx);
    }

    repository.save(pea);
    repository.save(checking);
    return transferId;
  }

  private boolean isPeaAccount(FinancialAccount account) {
    return account.subType() == AccountSubType.PEA || account.subType() == AccountSubType.PEA_PME;
  }

  private BigDecimal computeLivePortfolioValue(FinancialAccount account) {
    List<Holding> holdings = Holding.computeFrom(account.transactions());
    if (holdings.isEmpty()) return BigDecimal.ZERO;

    List<String> symbols = holdings.stream().map(h -> h.ticker().symbol()).distinct().toList();
    Map<String, Quote> quotes = marketDataPort.getQuotes(symbols);

    return holdings.stream()
        .map(
            h -> {
              Quote q = quotes.get(h.ticker().symbol());
              BigDecimal price = q != null ? q.price() : h.averageCostPrice().amount();
              return price.multiply(h.quantity()).setScale(2, RoundingMode.HALF_EVEN);
            })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal computeAdjustedTotalDeposits(FinancialAccount account) {
    return AccountBusinessRules.computeAdjustedTotalDepositsForCapacity(account);
  }

  private FinancialAccount getAccount(FinancialAccountId id, UserId userId) {
    return repository
        .findById(id)
        .filter(a -> a.ownerId().equals(userId))
        .orElseThrow(() -> new AccountNotFoundException(id));
  }
}
