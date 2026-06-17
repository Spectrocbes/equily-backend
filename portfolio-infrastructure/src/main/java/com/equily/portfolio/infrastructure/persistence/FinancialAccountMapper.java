package com.equily.portfolio.infrastructure.persistence;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.account.AccountStatus;
import com.equily.shared.Money;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

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
 *   <li>UserId ↔ UUID: via .value() and new UserId(uuid)
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
    entity.userId = account.ownerId().value();
    entity.subType = account.subType();
    entity.openedAt = account.openedAt();
    entity.status = account.status() != null ? account.status() : AccountStatus.ACTIVE;
    entity.closedAt = account.closedAt();

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
    UserId ownerId = new UserId(entity.userId);

    List<Transaction> transactions =
        entity.transactions.stream().map(FinancialAccountMapper::toDomainTransaction).toList();

    // reconstruct() not open(): open() generates a new random ID and ignores any prior
    // transactions. reconstruct() bypasses recordTransaction() so the persisted balance
    // is used directly without re-deriving it from the transaction log.
    AccountStatus status = entity.status != null ? entity.status : AccountStatus.ACTIVE;
    return FinancialAccount.reconstruct(
        id,
        entity.name,
        accountType,
        balance,
        transactions,
        entity.broker,
        ownerId,
        entity.subType,
        entity.openedAt,
        status,
        entity.closedAt);
  }

  static void updateJpaEntity(FinancialAccountJpaEntity entity, FinancialAccount account) {
    entity.name = account.name();
    entity.accountType = account.accountType().name();
    entity.currency = account.balance().currency().getCurrencyCode();
    entity.balance = account.balance().amount();
    entity.broker = account.broker();
    entity.userId = account.ownerId().value();
    entity.subType = account.subType();
    entity.openedAt = account.openedAt();
    entity.status = account.status() != null ? account.status() : AccountStatus.ACTIVE;
    entity.closedAt = account.closedAt();

    entity.transactions.removeIf(
        existingTx ->
            account.transactions().stream()
                .noneMatch(domainTx -> domainTx.id().value().equals(existingTx.id)));

    for (Transaction domainTx : account.transactions()) {
      Optional<TransactionJpaEntity> existing =
          entity.transactions.stream().filter(t -> t.id.equals(domainTx.id().value())).findFirst();
      if (existing.isPresent()) {
        updateJpaTransaction(existing.get(), domainTx, entity);
      } else {
        entity.transactions.add(toJpaTransaction(domainTx, entity));
      }
    }
  }

  private static void applyTransactionFields(
      TransactionJpaEntity tx, Transaction domain, FinancialAccountJpaEntity accountEntity) {
    tx.account = accountEntity;
    tx.type = domain.type().name();
    tx.ticker = domain.ticker() != null ? domain.ticker().symbol() : null;
    tx.quantity = domain.quantity();
    tx.pricePerUnit = domain.pricePerUnit() != null ? domain.pricePerUnit().amount() : null;
    tx.totalAmount = domain.totalAmount().amount();
    tx.date = domain.date();
    tx.fees = domain.fees();
    tx.description = domain.description();
    tx.currency = domain.currency();
    tx.amountEur = domain.amountEur();
    tx.eurFxRate = domain.eurFxRate();
    tx.liquidationValueAtWithdrawal = domain.liquidationValueAtWithdrawal();
    tx.grossWithdrawalAmount = domain.grossWithdrawalAmount();
  }

  private static void updateJpaTransaction(
      TransactionJpaEntity tx, Transaction domain, FinancialAccountJpaEntity accountEntity) {
    applyTransactionFields(tx, domain, accountEntity);
  }

  private static TransactionJpaEntity toJpaTransaction(
      Transaction t, FinancialAccountJpaEntity accountEntity) {
    TransactionJpaEntity tx = new TransactionJpaEntity();
    tx.id = t.id().value();
    applyTransactionFields(tx, t, accountEntity);
    return tx;
  }

  private static Transaction toDomainTransaction(TransactionJpaEntity tx) {
    TransactionId id = new TransactionId(tx.id);
    TransactionType type = TransactionType.valueOf(tx.type);
    Ticker ticker = tx.ticker != null ? new Ticker(tx.ticker) : null;
    Money pricePerUnit =
        tx.pricePerUnit != null ? new Money(tx.pricePerUnit, Currency.getInstance("EUR")) : null;
    Money totalAmount = new Money(tx.totalAmount, Currency.getInstance("EUR"));
    // Canonical record constructor: data comes from DB and was validated on write.
    return new Transaction(
        id,
        type,
        ticker,
        tx.quantity,
        pricePerUnit,
        totalAmount,
        tx.date,
        tx.fees,
        tx.description,
        tx.currency,
        tx.amountEur,
        tx.eurFxRate,
        tx.liquidationValueAtWithdrawal,
        tx.grossWithdrawalAmount);
  }

  private FinancialAccountMapper() {}
}
