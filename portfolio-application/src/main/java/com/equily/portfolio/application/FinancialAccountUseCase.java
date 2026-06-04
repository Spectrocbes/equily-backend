package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.csv.CsvImportResult;
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
   * Updates an existing transaction's mutable fields. Type and ticker cannot be changed. Throws
   * AccountNotFoundException if the account does not exist or is not owned by the requesting user.
   * Throws TransactionNotFoundException if no transaction with the given id exists.
   */
  void updateTransaction(UpdateTransactionCommand command);
}
