package com.equily.portfolio.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.application.BrokerCsvParserPort;
import com.equily.portfolio.application.FinancialAccountUseCase;
import com.equily.portfolio.application.exception.CsvParsingException;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.AssetMetadata;
import com.equily.portfolio.domain.AssetType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.Holding;
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
import com.equily.shared.Country;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
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
                     "totalCurrency": "EUR", "date": "2026-05-24", "fees": 0}
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
                     "totalCurrency": "EUR", "date": "2026-05-24", "fees": 0}
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
        Transaction.of(
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
        Transaction.of(
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
        .andExpect(jsonPath("$[1].type").value("BUY"))
        .andExpect(jsonPath("$[1].ticker").value("AAPL"))
        .andExpect(jsonPath("$[1].quantity").value(10))
        .andExpect(jsonPath("$[1].pricePerUnit").value(150))
        .andExpect(jsonPath("$[1].totalAmount").value(1500))
        .andExpect(jsonPath("$[1].fees").value(0))
        .andExpect(jsonPath("$[1].description").value("DCA janvier"));
  }

  @Test
  void getTransactions_returns_list_with_deposit_transaction_no_ticker() throws Exception {
    Transaction depositTx =
        Transaction.of(
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
                     "pricePerUnit": 150.00, "priceCurrency": "EUR",
                     "totalAmount": 1500.00, "totalCurrency": "EUR", "date": "2026-01-15",
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
 "pricePerUnit": 110.00, "priceCurrency": "EUR",
 "totalAmount": 660.00, "totalCurrency": "EUR", "date": "2026-05-24", "fees": 0}
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
                     "totalCurrency": "EUR", "date": "9999-12-31", "fees": 0}
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
                         "totalCurrency": "EUR", "date": "%s", "fees": 0}
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
                     "totalCurrency": "EUR", "date": "2026-05-24", "fees": 0}
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
    List<Holding> holdings = List.of();
    when(useCase.getHoldings(any(), any())).thenReturn(holdings);

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}/holdings", UUID.randomUUID().toString())
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  void getHoldings_returns_404_when_account_not_found() throws Exception {
    when(useCase.getHoldings(any(), any()))
        .thenThrow(new AccountNotFoundException(FinancialAccountId.generate()));

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}/holdings", UUID.randomUUID().toString())
                .with(authentication(mockAuth())))
        .andExpect(status().isNotFound());
  }

  @Test
  void getHoldings_returns_200_with_mapped_holding_fields() throws Exception {
    Holding holding =
        new Holding(
            new Ticker("AAPL"),
            AssetType.STOCK,
            new AssetMetadata("Apple Inc.", "US0378331005", new Country("US")),
            new BigDecimal("10"),
            new Money(new BigDecimal("150.00"), Currency.getInstance("EUR")),
            new Money(new BigDecimal("1500.00"), Currency.getInstance("EUR")),
            new Money(new BigDecimal("4.99"), Currency.getInstance("EUR")));
    when(useCase.getHoldings(any(), any())).thenReturn(List.of(holding));

    mockMvc
        .perform(
            get("/api/v1/accounts/{id}/holdings", UUID.randomUUID().toString())
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].ticker").value("AAPL"))
        .andExpect(jsonPath("$[0].quantity").value(10))
        .andExpect(jsonPath("$[0].averageCostPrice").value(150.00))
        .andExpect(jsonPath("$[0].currency").value("EUR"))
        .andExpect(jsonPath("$[0].totalInvested").value(1500.00))
        .andExpect(jsonPath("$[0].totalFeesPaid").value(4.99));
  }

  @Test
  void getHoldings_invalid_uuid_returns_400() throws Exception {
    mockMvc
        .perform(get("/api/v1/accounts/not-a-valid-uuid/holdings").with(authentication(mockAuth())))
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
                     "totalCurrency": "EUR", "date": "2026-05-30", "fees": 0}
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
        Transaction.of(
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
}
