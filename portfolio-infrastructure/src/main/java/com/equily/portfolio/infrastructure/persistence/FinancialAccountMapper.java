package com.equily.portfolio.infrastructure.persistence;

import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.shared.Money;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Maps between FinancialAccount (domain) and FinancialAccountJpaEntity (JPA). This is the
 * anti-corruption layer: the domain never knows about JPA. Static methods only — no state, no
 * Spring bean.
 *
 * <p>Mapping decisions:
 *
 * <ul>
 *   <li>AccountType enum ↔ String: AccountType.name() / AccountType.valueOf()
 *   <li>Money ↔ two columns: NUMERIC amount + CHAR(3) currency code
 *   <li>Ticker ↔ String (nullable — null for DEPOSIT / WITHDRAWAL / DIVIDEND)
 *   <li>TransactionType enum ↔ String: TransactionType.name() / TransactionType.valueOf()
 *   <li>FinancialAccountId / TransactionId ↔ UUID: via .value() and new XxxId(uuid)
 * </ul>
 */
class FinancialAccountMapper {

  static FinancialAccountJpaEntity toJpa(FinancialAccount account) {
    FinancialAccountJpaEntity entity = new FinancialAccountJpaEntity();
    entity.id = account.id().value();
    entity.name = account.name();
    entity.accountType = account.accountType().name();
    entity.currency = account.balance().currency().getCurrencyCode();
    entity.balance = account.balance().amount();
    entity.broker = account.broker();

    List<TransactionJpaEntity> txEntities =
        account.transactions().stream().map(t -> toJpaTransaction(t, entity)).toList();
    entity.transactions = new ArrayList<>(txEntities);

    return entity;
  }

  static FinancialAccount toDomain(FinancialAccountJpaEntity entity) {
    FinancialAccountId id = new FinancialAccountId(entity.id);
    AccountType accountType = AccountType.valueOf(entity.accountType);
    Currency currency = Currency.getInstance(entity.currency);
    Money balance = new Money(entity.balance, currency);

    List<Transaction> transactions =
        entity.transactions.stream().map(FinancialAccountMapper::toDomainTransaction).toList();

    // reconstruct() not open(): open() generates a new random ID and ignores any prior
    // transactions. reconstruct() bypasses recordTransaction() so the persisted balance
    // is used directly without re-deriving it from the transaction log.
    return FinancialAccount.reconstruct(
        id, entity.name, accountType, balance, transactions, entity.broker);
  }

  private static TransactionJpaEntity toJpaTransaction(
      Transaction t, FinancialAccountJpaEntity accountEntity) {
    TransactionJpaEntity tx = new TransactionJpaEntity();
    tx.id = t.id().value();
    tx.account = accountEntity;
    tx.type = t.type().name();
    tx.ticker = t.ticker() != null ? t.ticker().symbol() : null;
    tx.quantity = t.quantity();
    tx.pricePerUnit = t.pricePerUnit() != null ? t.pricePerUnit().amount() : null;
    tx.priceCurrency =
        t.pricePerUnit() != null ? t.pricePerUnit().currency().getCurrencyCode() : null;
    tx.totalAmount = t.totalAmount().amount();
    tx.totalCurrency = t.totalAmount().currency().getCurrencyCode();
    tx.date = t.date();
    tx.description = t.description();
    return tx;
  }

  private static Transaction toDomainTransaction(TransactionJpaEntity tx) {
    TransactionId id = new TransactionId(tx.id);
    TransactionType type = TransactionType.valueOf(tx.type);
    Ticker ticker = tx.ticker != null ? new Ticker(tx.ticker) : null;
    Money pricePerUnit =
        (tx.pricePerUnit != null && tx.priceCurrency != null)
            ? new Money(tx.pricePerUnit, Currency.getInstance(tx.priceCurrency))
            : null;
    Money totalAmount = new Money(tx.totalAmount, Currency.getInstance(tx.totalCurrency));
    // Canonical record constructor: data comes from DB and was validated on write.
    return new Transaction(
        id, type, ticker, tx.quantity, pricePerUnit, totalAmount, tx.date, tx.description);
  }

  private FinancialAccountMapper() {}
}
