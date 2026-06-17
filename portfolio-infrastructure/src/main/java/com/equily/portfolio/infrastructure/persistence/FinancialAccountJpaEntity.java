package com.equily.portfolio.infrastructure.persistence;

import com.equily.portfolio.domain.account.AccountStatus;
import com.equily.portfolio.domain.account.AccountSubType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

// Implements Persistable so Spring Data calls persist() (INSERT) for new entities
// rather than merge() (which Hibernate cannot route to INSERT when IDs are externally managed).
@Entity
@Table(schema = "portfolio", name = "financial_account")
class FinancialAccountJpaEntity implements Persistable<UUID> {

  @Id UUID id;

  @Column(nullable = false)
  String name;

  @Column(name = "account_type", nullable = false)
  String accountType;

  // Serves as both the account's base currency and the currency for the balance amount.
  @Column(name = "currency", nullable = false, length = 3)
  String currency;

  @Column(name = "balance", nullable = false, precision = 19, scale = 2)
  BigDecimal balance;

  @Column(name = "broker", length = 100, nullable = false)
  String broker;

  @Column(name = "user_id", nullable = false)
  UUID userId;

  @Column(name = "sub_type", length = 50)
  @Enumerated(EnumType.STRING)
  AccountSubType subType;

  @Column(name = "opened_at", nullable = false)
  LocalDate openedAt;

  @Column(name = "status", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  AccountStatus status;

  @Column(name = "closed_at")
  LocalDate closedAt;

  @OneToMany(
      mappedBy = "account",
      cascade = CascadeType.ALL,
      fetch = FetchType.EAGER,
      orphanRemoval = true)
  @OrderBy("date ASC")
  List<TransactionJpaEntity> transactions = new ArrayList<>();

  @Transient private boolean isNew = true;

  protected FinancialAccountJpaEntity() {}

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    this.isNew = false;
  }

  void markAsExisting() {
    this.isNew = false;
  }
}
