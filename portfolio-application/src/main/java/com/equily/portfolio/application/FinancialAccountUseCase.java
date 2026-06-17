package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.PeaWithdrawalSimulation;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.csv.CsvImportResult;
import com.equily.portfolio.domain.marketdata.EnrichedHolding;
import java.math.BigDecimal;
import java.util.List;

/**
 * Input port: defines all operations available on FinancialAccounts. Implementations live in the
 * same package (application layer). Web layer depends only on this interface.
 */
public interface FinancialAccountUseCase {

  /** Creates a new FinancialAccount and persists it. Returns the generated FinancialAccountId. */
  FinancialAccountId createAccount(CreateFinancialAccountCommand command);

  /**
   * Records a transaction on an existing account. Throws AccountNotFoundException if the account
   * does not exist.
   */
  void recordTransaction(RecordTransactionCommand command);

  /** Returns all accounts owned by the given user. */
  List<FinancialAccount> getAllAccounts(UserId ownerId);

  /**
   * Returns a single account by ID. Throws AccountNotFoundException if not found or if the account
   * does not belong to the requesting user (ownership not revealed).
   */
  FinancialAccount getAccountById(FinancialAccountId id, UserId ownerId);

  /**
   * Returns computed holdings for a given account. Throws AccountNotFoundException if not found or
   * not owned by the requesting user.
   */
  List<Holding> getHoldings(FinancialAccountId id, UserId ownerId);

  /**
   * Imports transactions from an already-parsed CSV result into an account. Skips transactions that
   * duplicate an existing one by (date, ticker, totalAmount). Throws AccountNotFoundException if
   * the account does not exist or is not owned by the requesting user.
   */
  CsvImportResult importCsv(FinancialAccountId accountId, CsvImportResult parsed, UserId ownerId);

  /**
   * Returns enriched holdings for a given account, fetching live prices from the market data
   * context and converting them to targetCurrency. Holdings without an available quote are returned
   * with priceAvailable=false.
   */
  List<EnrichedHolding> getEnrichedHoldings(
      FinancialAccountId id, UserId ownerId, String targetCurrency);

  /**
   * Returns a live portfolio summary for every investment account owned by the user. All holdings
   * across all accounts are fetched in a single batch market-data call. Live values are converted
   * to targetCurrency using FX rates.
   */
  List<AccountPortfolioSummary> getPortfolioSummaries(UserId userId, String targetCurrency);

  /**
   * Deletes a transaction from an account. Validates that the account is not closed, the
   * transaction exists and belongs to the account, and that removal does not leave the account with
   * a negative balance at any point in the remaining chronology.
   */
  void deleteTransaction(FinancialAccountId accountId, TransactionId transactionId, UserId userId);

  /**
   * Updates an existing transaction's mutable fields. Type and ticker cannot be changed. Throws
   * AccountNotFoundException if the account does not exist or is not owned by the requesting user.
   * Throws TransactionNotFoundException if no transaction with the given id exists.
   */
  void updateTransaction(UpdateTransactionCommand command);

  /**
   * Returns the type of an existing transaction. Throws AccountNotFoundException if the account
   * does not exist or is not owned by the requesting user. Throws TransactionNotFoundException if
   * no transaction with the given id exists.
   */
  TransactionType getTransactionType(
      FinancialAccountId accountId, TransactionId transactionId, UserId userId);

  /**
   * Simulates the tax impact of a PEA withdrawal. Fetches live portfolio value via MarketDataPort
   * then delegates to PeaClosureUseCase. Read-only.
   *
   * @param withdrawalAmount requested withdrawal amount (null → full closure / liquidationValue)
   */
  PeaWithdrawalSimulation simulatePeaClosure(
      FinancialAccountId id, UserId userId, BigDecimal withdrawalAmount);

  /**
   * Closes a PEA account. Fetches live portfolio value via MarketDataPort, creates withdrawal
   * transactions (net amount + flat tax), then marks the account CLOSED.
   */
  void closePea(FinancialAccountId id, UserId userId);
}
