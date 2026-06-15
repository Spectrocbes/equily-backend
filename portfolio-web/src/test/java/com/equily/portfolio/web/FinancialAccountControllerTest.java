package com.equily.portfolio.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.application.AccountPortfolioSummary;
import com.equily.portfolio.application.BrokerCsvParserPort;
import com.equily.portfolio.application.FinancialAccountUseCase;
import com.equily.portfolio.application.exception.CsvParsingException;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.AssetMetadata;
import com.equily.portfolio.domain.AssetType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.PeaWithdrawalSimulation;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.portfolio.domain.csv.CsvImportResult;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.portfolio.domain.exception.DepositLimitExceededException;
import com.equily.portfolio.domain.exception.InsufficientFundsException;
import com.equily.portfolio.domain.exception.InvalidHoldingException;
import com.equily.portfolio.domain.exception.InvalidTransactionException;
import com.equily.portfolio.domain.exception.TransactionNotFoundException;
import com.equily.portfolio.domain.marketdata.EnrichedHolding;
import com.equily.portfolio.domain.marketdata.FxRatePort;
import com.equily.portfolio.domain.marketdata.Quote;
import com.equily.shared.Country;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FinancialAccountController.class)
@Import(TestSecurityConfig.class)
class FinancialAccountControllerTest {

  @MockitoBean private FinancialAccountUseCase useCase;
  @MockitoBean private BrokerCsvParserPort parserPort;
  @MockitoBean private FxRatePort fxRatePort;

  @Autowired private MockMvc mockMvc;

  private FinancialAccount testAccount;
  private UserId testUserId;

  @BeforeEach
  void setUp() {
    testUserId = UserId.generate();
    testAccount =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.valueOf(1000), Currency.getInstance("EUR")),
            "Fortuneo",
            testUserId,
            null,
            LocalDate.of(2024, 1, 1));
  }

  private Authentication mockAuth() {
    return new UsernamePasswordAuthenticationToken(testUserId, null, List.of());
  }

  @Test
  void getAllAccounts_returns200WithList() throws Exception {
    when(useCase.getAllAccounts(any())).thenReturn(List.of(testAccount));

    mockMvc
        .perform(get("/api/v1/accounts").with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void getAccounts_converts_balance_to_target_currency() throws Exception {
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            "Fortuneo",
            testUserId,
            null,
            LocalDate.of(2024, 1, 1));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("1000"), Currency.getInstance("EUR")),
            LocalDate.of(2024, 1, 1),
            BigDecimal.ZERO,
            null));
    when(useCase.getAllAccounts(any())).thenReturn(List.of(account));
    when(fxRatePort.getRate("EUR", "USD")).thenReturn(Optional.of(new BigDecimal("1.10")));

    mockMvc
        .perform(get("/api/v1/accounts").param("currency", "USD").with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].currency").value("USD"))
        .andExpect(jsonPath("$[0].balance").value(1100.00));
  }

  @Test
  void getAccounts_keeps_eur_when_no_currency_param() throws Exception {
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            "Fortuneo",
            testUserId,
            null,
            LocalDate.of(2024, 1, 1));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("1000"), Currency.getInstance("EUR")),
            LocalDate.of(2024, 1, 1),
            BigDecimal.ZERO,
            null));
    when(useCase.getAllAccounts(any())).thenReturn(List.of(account));

    mockMvc
        .perform(get("/api/v1/accounts").with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].currency").value("EUR"))
        .andExpect(jsonPath("$[0].balance").value(1000.00));
  }

  @Test
  void getAccountById_returns200WhenFound() throws Exception {
    FinancialAccountId id = testAccount.id();
    when(useCase.getAccountById(eq(id), any())).thenReturn(testAccount);

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}", id.value().toString()).with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.value().toString()));
  }

  @Test
  void getAccountById_returns404WhenNotFound() throws Exception {
    FinancialAccountId id = FinancialAccountId.generate();
    when(useCase.getAccountById(eq(id), any())).thenThrow(new AccountNotFoundException(id));

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}", id.value().toString()).with(authentication(mockAuth())))
        .andExpect(status().isNotFound());
  }

  @Test
  void createAccount_returns201WithId() throws Exception {
    FinancialAccountId newId = FinancialAccountId.generate();
    when(useCase.createAccount(any())).thenReturn(newId);

    mockMvc
        .perform(
            post("/api/v1/accounts")
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "My PEA", "accountType": "PEA",
                     "initialBalance": 1000, "currency": "EUR", "broker": "Fortuneo"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(newId.value().toString()));
  }

  @Test
  void createAccount_with_subType_returns_201() throws Exception {
    FinancialAccountId newId = FinancialAccountId.generate();
    when(useCase.createAccount(any())).thenReturn(newId);

    mockMvc
        .perform(
            post("/api/v1/accounts")
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Mon Livret A", "accountType": "SAVINGS_ACCOUNT",
                     "subType": "LIVRET_A", "broker": "BNP",
                     "initialBalance": 0, "currency": "EUR"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  void recordTransaction_returns204() throws Exception {
    when(useCase.getAccountById(any(), any())).thenReturn(testAccount);

    mockMvc
        .perform(
            post("/api/v1/accounts/{id}/transactions", testAccount.id().value().toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"type": "DEPOSIT", "totalAmount": 500,
                     "date": "2026-05-24", "fees": 0}
                    """))
        .andExpect(status().isNoContent());
  }

  @Test
  void recordTransaction_returns422OnInsufficientFunds() throws Exception {
    when(useCase.getAccountById(any(), any())).thenReturn(testAccount);
    doThrow(
            new InsufficientFundsException(
                new Money(BigDecimal.valueOf(999999), Currency.getInstance("EUR")),
                new Money(BigDecimal.valueOf(1000), Currency.getInstance("EUR"))))
        .when(useCase)
        .recordTransaction(any());

    mockMvc
        .perform(
            post("/api/v1/accounts/{id}/transactions", testAccount.id().value().toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"type": "WITHDRAWAL", "totalAmount": 999999,
                     "date": "2026-05-24", "fees": 0}
                    """))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void getTransactions_returns_list_with_buy_transaction() throws Exception {
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            "Fortuneo",
            testUserId,
            null,
            LocalDate.of(2024, 1, 1));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(2000), Currency.getInstance("EUR")),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null));
    Transaction buyTx =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            BigDecimal.valueOf(10),
            new Money(BigDecimal.valueOf(150), Currency.getInstance("EUR")),
            new Money(BigDecimal.valueOf(1500), Currency.getInstance("EUR")),
            LocalDate.of(2026, 1, 15),
            BigDecimal.ZERO,
            "DCA janvier");
    account.recordTransaction(buyTx);
    when(useCase.getAccountById(any(), any())).thenReturn(account);

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}/transactions", account.id().value().toString())
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].type").value("BUY"))
        .andExpect(jsonPath("$[0].ticker").value("AAPL"))
        .andExpect(jsonPath("$[0].quantity").value(10))
        .andExpect(jsonPath("$[0].pricePerUnit").value(150))
        .andExpect(jsonPath("$[0].totalAmount").value(1500))
        .andExpect(jsonPath("$[0].fees").value(0))
        .andExpect(jsonPath("$[0].description").value("DCA janvier"));
  }

  @Test
  void getTransactions_returns_list_with_deposit_transaction_no_ticker() throws Exception {
    Transaction depositTx =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(1000), Currency.getInstance("EUR")),
            LocalDate.of(2026, 1, 15),
            null,
            null);
    testAccount.recordTransaction(depositTx);
    when(useCase.getAccountById(any(), any())).thenReturn(testAccount);

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}/transactions", testAccount.id().value().toString())
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].type").value("DEPOSIT"))
        .andExpect(jsonPath("$[0].ticker").isEmpty())
        .andExpect(jsonPath("$[0].pricePerUnit").isEmpty());
  }

  @Test
  void getTransactions_converts_amounts_to_target_currency() throws Exception {
    FinancialAccount account =
        FinancialAccount.open(
            "Mon CTO",
            AccountType.COMPTE_TITRES,
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            "IBKR",
            testUserId,
            null,
            LocalDate.of(2024, 1, 1));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), Currency.getInstance("EUR")),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            new BigDecimal("10"),
            new Money(new BigDecimal("150.00"), Currency.getInstance("EUR")),
            new Money(new BigDecimal("1500.00"), Currency.getInstance("EUR")),
            LocalDate.of(2026, 1, 15),
            new BigDecimal("4.99"),
            null));
    when(useCase.getAccountById(any(), any())).thenReturn(account);
    when(fxRatePort.getRate("EUR", "USD")).thenReturn(Optional.of(new BigDecimal("1.10")));

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}/transactions", account.id().value().toString())
                .param("currency", "USD")
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        // BUY is newest (2026-01-15), DEPOSIT is older (2026-01-01) — sorted desc
        .andExpect(jsonPath("$[0].type").value("BUY"))
        .andExpect(jsonPath("$[0].nativeCurrency").value("EUR"))
        .andExpect(jsonPath("$[0].totalAmountNative").value(1500.00))
        .andExpect(jsonPath("$[0].pricePerUnit").value(165.00))
        .andExpect(jsonPath("$[0].totalAmount").value(1650.00))
        .andExpect(jsonPath("$[0].fees").value(5.49));
  }

  @Test
  void recordTransaction_buy_with_ticker_and_price_returns_204() throws Exception {
    when(useCase.getAccountById(any(), any())).thenReturn(testAccount);

    mockMvc
        .perform(
            post("/api/v1/accounts/{id}/transactions", testAccount.id().value().toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
{"type": "BUY", "ticker": "AAPL", "quantity": 10,
 "pricePerUnit": 150.00,                     "totalAmount": 1500.00, "date": "2026-01-15",
 "fees": 4.99}
"""))
        .andExpect(status().isNoContent());
  }

  @Test
  void recordTransaction_returns_422_on_invalid_holding() throws Exception {
    doThrow(new InvalidHoldingException("AAPL", new BigDecimal("6"), new BigDecimal("5")))
        .when(useCase)
        .recordTransaction(any());

    mockMvc
        .perform(
            post("/api/v1/accounts/{id}/transactions", testAccount.id().value().toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
{"type": "SELL", "ticker": "AAPL", "quantity": 6,
 "pricePerUnit": 110.00, "totalAmount": 660.00, "date": "2026-05-24", "fees": 0}
"""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string("Cannot sell 6 AAPL — you only hold 5"));
  }

  @Test
  void recordTransaction_rejects_date_year_999999() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/accounts/{id}/transactions", testAccount.id().value().toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"type": "DEPOSIT", "totalAmount": 100,
                     "date": "9999-12-31", "fees": 0}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.date").value("Transaction date must be between 1900-01-01 and tomorrow"));
  }

  @Test
  void recordTransaction_accepts_date_tomorrow() throws Exception {
    when(useCase.getAccountById(any(), any())).thenReturn(testAccount);
    String tomorrow = LocalDate.now().plusDays(1).toString();

    mockMvc
        .perform(
            post("/api/v1/accounts/{id}/transactions", testAccount.id().value().toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                        {"type": "DEPOSIT", "totalAmount": 100,
                         "date": "%s", "fees": 0}
                        """,
                        tomorrow)))
        .andExpect(status().isNoContent());
  }

  @Test
  void recordTransaction_throws_InvalidTransactionException_returns_400() throws Exception {
    when(useCase.getAccountById(any(), any())).thenReturn(testAccount);
    doThrow(new InvalidTransactionException("bad tx")).when(useCase).recordTransaction(any());

    mockMvc
        .perform(
            post("/api/v1/accounts/{id}/transactions", testAccount.id().value().toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"type": "DEPOSIT", "totalAmount": 500,
                     "date": "2026-05-24", "fees": 0}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("bad tx"));
  }

  @Test
  void getAccountById_invalid_uuid_returns_400() throws Exception {
    mockMvc
        .perform(get("/api/v1/accounts/not-a-valid-uuid").with(authentication(mockAuth())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getHoldings_returns_200_with_holdings_list() throws Exception {
    when(useCase.getEnrichedHoldings(any(), any(), any())).thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}/holdings/enriched", UUID.randomUUID().toString())
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  void getHoldings_returns_404_when_account_not_found() throws Exception {
    when(useCase.getEnrichedHoldings(any(), any(), any()))
        .thenThrow(new AccountNotFoundException(FinancialAccountId.generate()));

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}/holdings/enriched", UUID.randomUUID().toString())
                .with(authentication(mockAuth())))
        .andExpect(status().isNotFound());
  }

  @Test
  void getHoldings_returns_200_with_enriched_holding_fields_with_price() throws Exception {
    Holding holding =
        new Holding(
            new Ticker("AAPL"),
            AssetType.STOCK,
            new AssetMetadata("Apple Inc.", "US0378331005", new Country("US")),
            new BigDecimal("10"),
            new Money(new BigDecimal("150.00"), Currency.getInstance("EUR")),
            new Money(new BigDecimal("1500.00"), Currency.getInstance("EUR")),
            new Money(new BigDecimal("4.99"), Currency.getInstance("EUR")));
    Quote quote =
        new Quote(
            "AAPL",
            new BigDecimal("160.00"),
            "EUR",
            "Apple Inc.",
            Instant.now(),
            new BigDecimal("2.50"));
    EnrichedHolding enriched =
        EnrichedHolding.withPrice(holding, quote, "EUR", BigDecimal.ONE, BigDecimal.ONE);
    when(useCase.getEnrichedHoldings(any(), any(), any())).thenReturn(List.of(enriched));

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}/holdings/enriched", UUID.randomUUID().toString())
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].ticker").value("AAPL"))
        .andExpect(jsonPath("$[0].quantity").value(10))
        .andExpect(jsonPath("$[0].averageCostPrice").value(150.00))
        .andExpect(jsonPath("$[0].totalInvested").value(1500.00))
        .andExpect(jsonPath("$[0].totalFeesPaid").value(4.99))
        .andExpect(jsonPath("$[0].priceAvailable").value(true))
        .andExpect(jsonPath("$[0].currentPrice").value(160.00))
        .andExpect(jsonPath("$[0].currency").value("EUR"))
        .andExpect(jsonPath("$[0].marketValue").value(1600.00))
        .andExpect(jsonPath("$[0].unrealizedPnl").value(100.00))
        .andExpect(jsonPath("$[0].dayChangePercent").value(2.50));
  }

  @Test
  void getHoldings_returns_200_with_enriched_holding_without_price() throws Exception {
    Holding holding =
        new Holding(
            new Ticker("OBSCURE"),
            AssetType.STOCK,
            new AssetMetadata("Obscure Corp", null, new Country("US")),
            new BigDecimal("5"),
            new Money(new BigDecimal("200.00"), Currency.getInstance("EUR")),
            new Money(new BigDecimal("1000.00"), Currency.getInstance("EUR")),
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")));
    EnrichedHolding enriched = EnrichedHolding.withoutPrice(holding);
    when(useCase.getEnrichedHoldings(any(), any(), any())).thenReturn(List.of(enriched));

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}/holdings/enriched", UUID.randomUUID().toString())
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].ticker").value("OBSCURE"))
        .andExpect(jsonPath("$[0].priceAvailable").value(false))
        .andExpect(jsonPath("$[0].ticker").value("OBSCURE"))
        .andExpect(jsonPath("$[0].totalInvested").value(1000.00));
  }

  @Test
  void getHoldings_invalid_uuid_returns_400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/accounts/not-a-valid-uuid/holdings/enriched")
                .with(authentication(mockAuth())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void importCsv_returns_200_with_summary() throws Exception {
    CsvImportResult parseResult = new CsvImportResult(3, 0, 0, List.of(), List.of());
    CsvImportResult importResult = new CsvImportResult(3, 0, 0, List.of(), List.of());
    when(parserPort.parse(any(), eq("BOURSOBANK"), eq("OPERATIONS"))).thenReturn(parseResult);
    when(useCase.importCsv(any(), any(), any())).thenReturn(importResult);

    mockMvc
        .perform(
            multipart("/api/v1/accounts/{id}/import/csv", UUID.randomUUID().toString())
                .file(
                    new MockMultipartFile(
                        "file", "ops.csv", "text/csv", "Date opération;...\n".getBytes()))
                .param("broker", "BOURSOBANK")
                .param("mode", "OPERATIONS")
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imported").value(3))
        .andExpect(jsonPath("$.skipped").value(0))
        .andExpect(jsonPath("$.errors").value(0));
  }

  @Test
  void importCsv_returns_400_on_empty_file() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/accounts/{id}/import/csv", UUID.randomUUID().toString())
                .file(new MockMultipartFile("file", "", "text/csv", new byte[0]))
                .param("broker", "BOURSOBANK")
                .param("mode", "OPERATIONS")
                .with(authentication(mockAuth())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value(1));
  }

  @Test
  void importCsv_returns_400_on_unsupported_broker() throws Exception {
    when(parserPort.parse(any(), eq("UNKNOWN"), eq("OPERATIONS")))
        .thenThrow(new IllegalArgumentException("Unsupported broker/mode: UNKNOWN/OPERATIONS"));

    mockMvc
        .perform(
            multipart("/api/v1/accounts/{id}/import/csv", UUID.randomUUID().toString())
                .file(new MockMultipartFile("file", "f.csv", "text/csv", "data".getBytes()))
                .param("broker", "UNKNOWN")
                .param("mode", "OPERATIONS")
                .with(authentication(mockAuth())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value(1));
  }

  @Test
  void importCsv_returns_400_when_use_case_throws_csv_parsing_exception() throws Exception {
    CsvImportResult parseResult = new CsvImportResult(0, 0, 0, List.of(), List.of());
    when(parserPort.parse(any(), any(), any())).thenReturn(parseResult);
    when(useCase.importCsv(any(), any(), any()))
        .thenThrow(new CsvParsingException("Use case parse failure"));

    mockMvc
        .perform(
            multipart("/api/v1/accounts/{id}/import/csv", UUID.randomUUID().toString())
                .file(new MockMultipartFile("file", "f.csv", "text/csv", "bad".getBytes()))
                .param("broker", "BOURSOBANK")
                .param("mode", "OPERATIONS")
                .with(authentication(mockAuth())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value(1));
  }

  @Test
  void importCsv_returns_400_on_csv_parsing_exception() throws Exception {
    when(parserPort.parse(any(), any(), any()))
        .thenThrow(new CsvParsingException("Failed to parse"));

    mockMvc
        .perform(
            multipart("/api/v1/accounts/{id}/import/csv", UUID.randomUUID().toString())
                .file(new MockMultipartFile("file", "f.csv", "text/csv", "bad".getBytes()))
                .param("broker", "BOURSOBANK")
                .param("mode", "OPERATIONS")
                .with(authentication(mockAuth())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value(1));
  }

  @Test
  void recordTransaction_returns_422_when_deposit_limit_exceeded() throws Exception {
    when(useCase.getAccountById(any(), any())).thenReturn(testAccount);
    Money limit = new Money(new BigDecimal("22950"), Currency.getInstance("EUR"));
    Money current = new Money(new BigDecimal("22000"), Currency.getInstance("EUR"));
    Money attempted = new Money(new BigDecimal("1000"), Currency.getInstance("EUR"));
    doThrow(new DepositLimitExceededException(AccountSubType.LIVRET_A, limit, current, attempted))
        .when(useCase)
        .recordTransaction(any());

    mockMvc
        .perform(
            post("/api/v1/accounts/{id}/transactions", testAccount.id().value().toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"type": "DEPOSIT", "totalAmount": 1000,
                     "date": "2026-05-30", "fees": 0}
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("DEPOSIT_LIMIT_EXCEEDED"))
        .andExpect(jsonPath("$.subType").value("LIVRET_A"))
        .andExpect(jsonPath("$.remaining").value(950));
  }

  @Test
  void getAccountById_returns_deposit_fields_for_account_with_subtype() throws Exception {
    UserId userId = UserId.generate();
    FinancialAccount livretA =
        FinancialAccount.open(
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            "La Banque Postale",
            userId,
            AccountSubType.LIVRET_A,
            LocalDate.of(2020, 3, 1));
    Transaction deposit =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), Currency.getInstance("EUR")),
            LocalDate.of(2026, 1, 15),
            null,
            null);
    livretA.recordTransaction(deposit);

    FinancialAccountId id = livretA.id();
    when(useCase.getAccountById(eq(id), any())).thenReturn(livretA);
    when(useCase.getAllAccounts(any())).thenReturn(List.of(livretA));

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}", id.value().toString())
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subType").value("LIVRET_A"))
        .andExpect(jsonPath("$.depositLimit").value(22950))
        .andExpect(jsonPath("$.totalDeposits").value(10000))
        .andExpect(jsonPath("$.remainingCapacity").value(12950));
  }

  @Test
  void updateTransaction_buy_computes_total_from_qty_and_price() throws Exception {
    UUID txId = UUID.randomUUID();
    when(useCase.getTransactionType(any(), any(), any())).thenReturn(TransactionType.BUY);

    // qty=10, pricePerUnit=150, fees=4.99 → total = 10×150 + 4.99 = 1504.99
    mockMvc
        .perform(
            put(
                    "/api/v1/accounts/{id}/transactions/{txId}",
                    testAccount.id().value().toString(),
                    txId.toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"quantity": 10, "pricePerUnit": 150.00,
                     "date": "2026-01-15", "fees": 4.99}
                    """))
        .andExpect(status().isNoContent());
  }

  @Test
  void updateTransaction_deposit_uses_total_amount_directly() throws Exception {
    UUID txId = UUID.randomUUID();
    when(useCase.getTransactionType(any(), any(), any())).thenReturn(TransactionType.DEPOSIT);

    mockMvc
        .perform(
            put(
                    "/api/v1/accounts/{id}/transactions/{txId}",
                    testAccount.id().value().toString(),
                    txId.toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"totalAmount": 500, "date": "2026-05-24", "fees": 0}
                    """))
        .andExpect(status().isNoContent());
  }

  @Test
  void updateTransaction_rejects_zero_total_amount() throws Exception {
    UUID txId = UUID.randomUUID();

    mockMvc
        .perform(
            put(
                    "/api/v1/accounts/{id}/transactions/{txId}",
                    testAccount.id().value().toString(),
                    txId.toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"totalAmount": 0, "date": "2026-05-24", "fees": 0}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateTransaction_returns_204() throws Exception {
    UUID txId = UUID.randomUUID();

    mockMvc
        .perform(
            put(
                    "/api/v1/accounts/{id}/transactions/{txId}",
                    testAccount.id().value().toString(),
                    txId.toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"totalAmount": 500, "date": "2026-05-24", "fees": 0}
                    """))
        .andExpect(status().isNoContent());
  }

  @Test
  void updateTransaction_returns_404_when_transaction_not_found() throws Exception {
    UUID txId = UUID.randomUUID();
    doThrow(new TransactionNotFoundException(new TransactionId(txId)))
        .when(useCase)
        .updateTransaction(any());

    mockMvc
        .perform(
            put(
                    "/api/v1/accounts/{id}/transactions/{txId}",
                    testAccount.id().value().toString(),
                    txId.toString())
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"totalAmount": 500, "date": "2026-05-24", "fees": 0}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void getPeaSummary_returns_pea_data() throws Exception {
    UserId userId = UserId.generate();
    FinancialAccount pea =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            "Fortuneo",
            userId,
            AccountSubType.PEA,
            LocalDate.of(2020, 1, 1));
    Transaction deposit =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("50000"), Currency.getInstance("EUR")),
            LocalDate.of(2020, 6, 1),
            BigDecimal.ZERO,
            null);
    pea.recordTransaction(deposit);
    when(useCase.getAllAccounts(any())).thenReturn(List.of(pea));

    mockMvc
        .perform(
            get("/api/v1/accounts/summary/pea")
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasPea").value(true))
        .andExpect(jsonPath("$.hasPeaPme").value(false))
        .andExpect(jsonPath("$.peaDeposits").value(50000))
        .andExpect(jsonPath("$.peaLimit").value(150000))
        .andExpect(jsonPath("$.peaRemaining").value(100000))
        .andExpect(jsonPath("$.combinedLimit").value(225000))
        .andExpect(jsonPath("$.peaAccountId").value(pea.id().value().toString()));
  }

  @Test
  void toAccountResponse_returns_portfolioValue_for_pea_account() throws Exception {
    FinancialAccount pea =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            "Fortuneo",
            testUserId,
            null,
            LocalDate.of(2024, 1, 1));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("1000"), Currency.getInstance("EUR")),
            LocalDate.of(2024, 1, 10),
            BigDecimal.ZERO,
            null));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            new BigDecimal("10"),
            new Money(new BigDecimal("100"), Currency.getInstance("EUR")),
            new Money(new BigDecimal("1000"), Currency.getInstance("EUR")),
            LocalDate.of(2024, 2, 1),
            BigDecimal.ZERO,
            null));

    FinancialAccountId id = pea.id();
    when(useCase.getAccountById(eq(id), any())).thenReturn(pea);
    when(useCase.getAllAccounts(any())).thenReturn(List.of(pea));

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}", id.value().toString()).with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.portfolioValue").value(1000.00));
  }

  @Test
  void toAccountResponse_returns_null_portfolioValue_for_savings_account() throws Exception {
    FinancialAccount savings =
        FinancialAccount.open(
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            "Boursobank",
            testUserId,
            null,
            LocalDate.of(2024, 1, 1));

    FinancialAccountId id = savings.id();
    when(useCase.getAccountById(eq(id), any())).thenReturn(savings);
    when(useCase.getAllAccounts(any())).thenReturn(List.of(savings));

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}", id.value().toString()).with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.portfolioValue").doesNotExist());
  }

  @Test
  void getAccountById_returns_null_deposit_fields_for_account_without_subtype() throws Exception {
    FinancialAccountId id = testAccount.id();
    when(useCase.getAccountById(eq(id), any())).thenReturn(testAccount);
    when(useCase.getAllAccounts(any())).thenReturn(List.of(testAccount));

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}", id.value().toString()).with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subType").doesNotExist())
        .andExpect(jsonPath("$.depositLimit").doesNotExist())
        .andExpect(jsonPath("$.remainingCapacity").doesNotExist());
  }

  @Test
  void getPortfolioSummaries_returns_200_with_list() throws Exception {
    when(useCase.getPortfolioSummaries(any(), any()))
        .thenReturn(
            List.of(
                new AccountPortfolioSummary(
                    new FinancialAccountId(UUID.randomUUID()),
                    new BigDecimal("300.00"),
                    new BigDecimal("150.00"),
                    new BigDecimal("150.00"),
                    new BigDecimal("100.00"),
                    true)));

    mockMvc
        .perform(get("/api/v1/accounts/portfolio-summary").with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].livePortfolioValue").value(300.00))
        .andExpect(jsonPath("$[0].costPortfolioValue").value(150.00))
        .andExpect(jsonPath("$[0].unrealizedPnl").value(150.00))
        .andExpect(jsonPath("$[0].unrealizedPnlPct").value(100.00))
        .andExpect(jsonPath("$[0].priceAvailable").value(true));
  }

  @Test
  void getAccounts_converts_totalDeposits_and_remainingCapacity_to_target_currency()
      throws Exception {
    FinancialAccount livretA =
        FinancialAccount.open(
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            "BNP",
            testUserId,
            AccountSubType.LIVRET_A,
            LocalDate.of(2024, 1, 1));
    livretA.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), Currency.getInstance("EUR")),
            LocalDate.of(2024, 6, 1),
            BigDecimal.ZERO,
            null));
    when(useCase.getAllAccounts(any())).thenReturn(List.of(livretA));
    when(fxRatePort.getRate("EUR", "USD")).thenReturn(Optional.of(new BigDecimal("1.10")));

    mockMvc
        .perform(get("/api/v1/accounts").param("currency", "USD").with(authentication(mockAuth())))
        .andExpect(status().isOk())
        // totalDeposits: 10 000 EUR × 1.10 = 11 000.00 USD
        .andExpect(jsonPath("$[0].totalDeposits").value(11000.00))
        // remainingCapacity: (22 950 - 10 000) EUR × 1.10 = 14 245.00 USD
        .andExpect(jsonPath("$[0].remainingCapacity").value(14245.00));
  }

  @Test
  void getAccounts_converts_depositLimit_to_target_currency() throws Exception {
    FinancialAccount livretA =
        FinancialAccount.open(
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            "BNP",
            testUserId,
            AccountSubType.LIVRET_A,
            LocalDate.of(2024, 1, 1));
    when(useCase.getAllAccounts(any())).thenReturn(List.of(livretA));
    when(fxRatePort.getRate("EUR", "USD")).thenReturn(Optional.of(new BigDecimal("1.10")));

    mockMvc
        .perform(get("/api/v1/accounts").param("currency", "USD").with(authentication(mockAuth())))
        .andExpect(status().isOk())
        // depositLimit: 22 950 EUR × 1.10 = 25 245.00 USD
        .andExpect(jsonPath("$[0].depositLimit").value(25245.00));
  }

  @Test
  void getAllAccounts_returns401_when_principal_is_not_UserId() throws Exception {
    Authentication wrongPrincipal =
        new UsernamePasswordAuthenticationToken("not-a-userid", null, List.of());

    mockMvc
        .perform(get("/api/v1/accounts").with(authentication(wrongPrincipal)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getPeaClosureSimulation_returns200_with_simulation() throws Exception {
    FinancialAccountId id = testAccount.id();
    // totalDeposits=8000, liquidationValue=10000, gain=2000 (<5y)
    // irTax=256 (2000×12.8%), psTax=372 (2000×18.6%), totalTax=628, netAmount=9372
    PeaWithdrawalSimulation sim =
        new PeaWithdrawalSimulation(
            new BigDecimal("10000.00"), // liquidationValue
            new BigDecimal("8000.00"), // totalDeposits
            new BigDecimal("2000.00"), // netGain
            new BigDecimal("0.200000"), // gainRatio
            false, // atLoss
            false, // peaOlderThan5Years
            new BigDecimal("10000.00"), // withdrawalAmount
            new BigDecimal("2000.00"), // taxableGain
            new BigDecimal("256.00"), // irTax
            new BigDecimal("372.00"), // psTax
            new BigDecimal("628.00"), // totalTax
            new BigDecimal("9372.00")); // netAmount

    when(useCase.simulatePeaClosure(eq(id), any(), any())).thenReturn(sim);

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}/pea-closure-simulation", id.value().toString())
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taxableGain").value(2000.00))
        .andExpect(jsonPath("$.irTax").value(256.00))
        .andExpect(jsonPath("$.psTax").value(372.00))
        .andExpect(jsonPath("$.totalTax").value(628.00))
        .andExpect(jsonPath("$.netAmount").value(9372.00))
        .andExpect(jsonPath("$.atLoss").value(false))
        .andExpect(jsonPath("$.peaOlderThan5Years").value(false));
  }

  @Test
  void closePea_returns204() throws Exception {
    FinancialAccountId id = testAccount.id();

    mockMvc
        .perform(
            post("/api/v1/accounts/{id}/close", id.value().toString())
                .with(authentication(mockAuth())))
        .andExpect(status().isNoContent());
  }
}
