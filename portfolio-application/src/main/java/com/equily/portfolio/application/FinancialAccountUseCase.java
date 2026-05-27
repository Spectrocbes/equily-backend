package com.equily.portfolio.application;

import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.Holding;
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

  /**
   * Returns all accounts. Holdings are NOT computed here — that requires market data. Returns raw
   * accounts with transaction logs.
   */
  List<FinancialAccount> getAllAccounts();

  /** Returns a single account by ID. Throws AccountNotFoundException if not found. */
  FinancialAccount getAccountById(FinancialAccountId id);

  /**
   * Returns computed holdings for a given account. Holdings are derived from the transaction log —
   * no market data required. AssetType defaults to STOCK for all holdings (Phase 1 — no Market Data
   * context yet).
   */
  List<Holding> getHoldings(FinancialAccountId id);
}
