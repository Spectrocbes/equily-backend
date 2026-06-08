package com.equily.portfolio.web;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.application.AccountPortfolioSummary;
import com.equily.portfolio.application.BrokerCsvParserPort;
import com.equily.portfolio.application.CreateFinancialAccountCommand;
import com.equily.portfolio.application.FinancialAccountUseCase;
import com.equily.portfolio.application.RecordTransactionCommand;
import com.equily.portfolio.application.UpdateTransactionCommand;
import com.equily.portfolio.application.exception.CsvParsingException;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.UpdatedTransactionValues;
import com.equily.portfolio.domain.account.AccountBusinessRules;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.portfolio.domain.account.DepositLimits;
import com.equily.portfolio.domain.csv.CsvImportResult;
import com.equily.portfolio.domain.marketdata.EnrichedHolding;
import com.equily.shared.Money;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/accounts")
class FinancialAccountController {

  private static final Set<AccountType> INVESTMENT_TYPES =
      Set.of(
          AccountType.PEA,
          AccountType.PEA_PME,
          AccountType.COMPTE_TITRES,
          AccountType.PER,
          AccountType.ASSURANCE_VIE,
          AccountType.CRYPTO_WALLET);

  private final FinancialAccountUseCase useCase;
  private final BrokerCsvParserPort parserPort;

  FinancialAccountController(FinancialAccountUseCase useCase, BrokerCsvParserPort parserPort) {
    this.useCase = useCase;
    this.parserPort = parserPort;
  }

  private UserId extractUserId(Authentication auth) {
    return (UserId) auth.getPrincipal();
  }

  @GetMapping
  ResponseEntity<List<FinancialAccountResponse>> getAllAccounts(Authentication auth) {
    UserId userId = extractUserId(auth);
    List<FinancialAccount> accounts = useCase.getAllAccounts(userId);
    List<FinancialAccountResponse> responses =
        accounts.stream().map(a -> toAccountResponse(a, accounts)).toList();
    return ResponseEntity.ok(responses);
  }

  @GetMapping("/{id}")
  ResponseEntity<FinancialAccountResponse> getAccountById(
      @PathVariable String id, Authentication auth) {
    UserId userId = extractUserId(auth);
    FinancialAccount account =
        useCase.getAccountById(new FinancialAccountId(UUID.fromString(id)), userId);
    List<FinancialAccount> allUserAccounts = useCase.getAllAccounts(userId);
    return ResponseEntity.ok(toAccountResponse(account, allUserAccounts));
  }

  @PostMapping
  ResponseEntity<Map<String, String>> createAccount(
      @RequestBody @Valid CreateAccountRequest request, Authentication auth) {
    UserId userId = extractUserId(auth);
    AccountSubType subType =
        request.subType() != null ? AccountSubType.valueOf(request.subType()) : null;
    LocalDate openedAt = request.openedAt() != null ? request.openedAt() : LocalDate.now();
    CreateFinancialAccountCommand command =
        new CreateFinancialAccountCommand(
            request.name(),
            AccountType.valueOf(request.accountType()),
            new Money(request.initialBalance(), Currency.getInstance(request.currency())),
            request.broker(),
            userId,
            subType,
            openedAt);
    FinancialAccountId id = useCase.createAccount(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.value().toString()));
  }

  @PostMapping("/{id}/transactions")
  ResponseEntity<Void> recordTransaction(
      @PathVariable String id,
      @RequestBody @Valid RecordTransactionRequest request,
      Authentication auth) {
    UserId userId = extractUserId(auth);
    FinancialAccountId accountId = new FinancialAccountId(UUID.fromString(id));
    useCase.getAccountById(accountId, userId);
    Ticker ticker = request.ticker() != null ? new Ticker(request.ticker()) : null;
    Money pricePerUnit =
        request.pricePerUnit() != null
            ? new Money(request.pricePerUnit(), Currency.getInstance(request.priceCurrency()))
            : null;
    RecordTransactionCommand command =
        new RecordTransactionCommand(
            accountId,
            userId,
            TransactionType.valueOf(request.type()),
            ticker,
            request.quantity(),
            pricePerUnit,
            new Money(request.totalAmount(), Currency.getInstance(request.totalCurrency())),
            request.date(),
            request.fees(),
            request.description());
    useCase.recordTransaction(command);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/holdings/enriched")
  ResponseEntity<List<EnrichedHoldingResponse>> getHoldings(
      @PathVariable String id, Authentication auth) {
    UserId userId = extractUserId(auth);
    List<EnrichedHolding> holdings =
        useCase.getEnrichedHoldings(new FinancialAccountId(UUID.fromString(id)), userId);
    List<EnrichedHoldingResponse> response =
        holdings.stream().map(this::toEnrichedHoldingResponse).toList();
    return ResponseEntity.ok(response);
  }

  private EnrichedHoldingResponse toEnrichedHoldingResponse(EnrichedHolding eh) {
    Holding h = eh.holding();
    return new EnrichedHoldingResponse(
        h.ticker().symbol(),
        h.quantity(),
        h.averageCostPrice().amount(),
        h.totalInvested().amount(),
        h.totalFeesPaid().amount(),
        eh.currentPrice(),
        eh.currency(),
        eh.marketValue(),
        eh.unrealizedPnl(),
        eh.unrealizedPnlPct(),
        eh.dayChangePercent(),
        eh.priceAvailable());
  }

  @PutMapping("/{accountId}/transactions/{transactionId}")
  ResponseEntity<Void> updateTransaction(
      @PathVariable String accountId,
      @PathVariable String transactionId,
      @RequestBody @Valid UpdateTransactionRequest request,
      Authentication authentication) {

    UserId userId = extractUserId(authentication);
    FinancialAccountId fId = new FinancialAccountId(UUID.fromString(accountId));
    TransactionId tId = new TransactionId(UUID.fromString(transactionId));

    TransactionType type = useCase.getTransactionType(fId, tId, userId);
    BigDecimal fees = request.fees();

    BigDecimal totalAmount;
    if ((type == TransactionType.BUY || type == TransactionType.SELL)
        && request.quantity() != null
        && request.pricePerUnit() != null) {
      BigDecimal base =
          request.quantity().multiply(request.pricePerUnit()).setScale(2, RoundingMode.HALF_EVEN);
      totalAmount = type == TransactionType.BUY ? base.add(fees) : base.subtract(fees);
    } else {
      totalAmount = request.totalAmount();
    }

    UpdateTransactionCommand command =
        new UpdateTransactionCommand(
            fId,
            tId,
            userId,
            new UpdatedTransactionValues(
                request.quantity(),
                request.pricePerUnit() != null
                    ? new Money(request.pricePerUnit(), Currency.getInstance("EUR"))
                    : null,
                new Money(totalAmount, Currency.getInstance("EUR")),
                request.date(),
                fees,
                request.description()));

    useCase.updateTransaction(command);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/portfolio-summary")
  ResponseEntity<List<AccountPortfolioSummaryResponse>> getPortfolioSummaries(
      Authentication authentication) {
    UserId userId = extractUserId(authentication);
    List<AccountPortfolioSummary> summaries = useCase.getPortfolioSummaries(userId);
    List<AccountPortfolioSummaryResponse> response =
        summaries.stream()
            .map(
                s ->
                    new AccountPortfolioSummaryResponse(
                        s.accountId().value().toString(),
                        s.livePortfolioValue(),
                        s.costPortfolioValue(),
                        s.unrealizedPnl(),
                        s.unrealizedPnlPct(),
                        s.priceAvailable()))
            .toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping("/summary/pea")
  ResponseEntity<PeaSummaryResponse> getPeaSummary(Authentication authentication) {
    UserId userId = extractUserId(authentication);
    List<FinancialAccount> accounts = useCase.getAllAccounts(userId);

    FinancialAccount pea =
        accounts.stream().filter(a -> a.subType() == AccountSubType.PEA).findFirst().orElse(null);

    FinancialAccount peaPme =
        accounts.stream()
            .filter(a -> a.subType() == AccountSubType.PEA_PME)
            .findFirst()
            .orElse(null);

    BigDecimal peaDep = pea != null ? sumDeposits(pea) : BigDecimal.ZERO;
    BigDecimal peaPmeDep = peaPme != null ? sumDeposits(peaPme) : BigDecimal.ZERO;
    BigDecimal combined = peaDep.add(peaPmeDep);

    BigDecimal peaLimit = new BigDecimal("150000");
    BigDecimal combinedLimit = new BigDecimal("225000");

    return ResponseEntity.ok(
        new PeaSummaryResponse(
            pea != null,
            peaPme != null,
            peaDep,
            peaPmeDep,
            combined,
            combinedLimit,
            combinedLimit.subtract(combined).max(BigDecimal.ZERO),
            peaLimit,
            peaLimit.subtract(peaDep).max(BigDecimal.ZERO),
            pea != null ? pea.id().value().toString() : null,
            peaPme != null ? peaPme.id().value().toString() : null));
  }

  @GetMapping("/{id}/transactions")
  ResponseEntity<List<TransactionResponse>> getTransactions(
      @PathVariable String id, Authentication auth) {
    UserId userId = extractUserId(auth);
    FinancialAccount account =
        useCase.getAccountById(new FinancialAccountId(UUID.fromString(id)), userId);
    List<TransactionResponse> transactions =
        account.transactions().stream().map(this::toTransactionResponse).toList();
    return ResponseEntity.ok(transactions);
  }

  @PostMapping(value = "/{id}/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<CsvImportResponse> importCsv(
      @PathVariable String id,
      @RequestParam("file") MultipartFile file,
      @RequestParam("broker") String broker,
      @RequestParam("mode") String mode,
      Authentication auth) {

    if (file.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(new CsvImportResponse(0, 0, 1, List.of("File is empty")));
    }

    try {
      UserId userId = extractUserId(auth);
      CsvImportResult parsed = parserPort.parse(file.getInputStream(), broker, mode);
      FinancialAccountId accountId = new FinancialAccountId(UUID.fromString(id));
      CsvImportResult result = useCase.importCsv(accountId, parsed, userId);
      return ResponseEntity.ok(
          new CsvImportResponse(
              result.imported(), result.skipped(), result.errors(), result.errorDetails()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .body(new CsvImportResponse(0, 0, 1, List.of(e.getMessage())));
    } catch (CsvParsingException e) {
      return ResponseEntity.badRequest()
          .body(new CsvImportResponse(0, 0, 1, List.of(e.getMessage())));
    } catch (IOException e) {
      return ResponseEntity.internalServerError()
          .body(new CsvImportResponse(0, 0, 1, List.of("Failed to read file: " + e.getMessage())));
    }
  }

  private BigDecimal sumDeposits(FinancialAccount account) {
    return account.transactions().stream()
        .filter(t -> t.type() == TransactionType.DEPOSIT)
        .map(t -> t.totalAmount().amount())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private FinancialAccountResponse toAccountResponse(
      FinancialAccount account, List<FinancialAccount> allUserAccounts) {
    AccountSubType subType = account.subType();
    BigDecimal depositLimit =
        subType != null ? DepositLimits.limitFor(subType).map(Money::amount).orElse(null) : null;
    BigDecimal totalDeposits =
        account.transactions().stream()
            .filter(t -> t.type() == TransactionType.DEPOSIT)
            .map(t -> t.totalAmount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal remainingCapacity =
        AccountBusinessRules.remainingCapacity(account, allUserAccounts)
            .map(Money::amount)
            .orElse(null);
    return new FinancialAccountResponse(
        account.id().value().toString(),
        account.name(),
        account.accountType().name(),
        subType != null ? subType.name() : null,
        account.balance().amount(),
        account.balance().currency().getCurrencyCode(),
        account.transactions().size(),
        account.broker(),
        depositLimit,
        totalDeposits,
        remainingCapacity,
        account.openedAt(),
        computePortfolioValue(account));
  }

  private BigDecimal computePortfolioValue(FinancialAccount account) {
    if (!INVESTMENT_TYPES.contains(account.accountType())) return null;
    List<Holding> holdings = Holding.computeFrom(account.transactions());
    return holdings.stream()
        .map(
            h ->
                h.averageCostPrice()
                    .amount()
                    .multiply(h.quantity())
                    .setScale(2, RoundingMode.HALF_EVEN))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private TransactionResponse toTransactionResponse(Transaction tx) {
    return new TransactionResponse(
        tx.id().value().toString(),
        tx.type().name(),
        tx.ticker() != null ? tx.ticker().symbol() : null,
        tx.quantity(),
        tx.pricePerUnit() != null ? tx.pricePerUnit().amount() : null,
        tx.totalAmount().amount(),
        tx.totalAmount().currency().getCurrencyCode(),
        tx.date(),
        tx.fees(),
        tx.description());
  }
}
