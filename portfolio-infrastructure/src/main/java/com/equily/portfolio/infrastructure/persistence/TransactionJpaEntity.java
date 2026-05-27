package com.equily.portfolio.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "portfolio", name = "transaction")
class TransactionJpaEntity {

  @Id UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  FinancialAccountJpaEntity account;

  @Column(nullable = false, length = 20)
  String type;

  // Null for cash-only transaction types: DEPOSIT, WITHDRAWAL.
  @Column(length = 20)
  String ticker;

  // Null for cash-only transaction types.
  @Column(precision = 19, scale = 8)
  BigDecimal quantity;

  // Null for cash-only transaction types.
  @Column(name = "price_per_unit", precision = 19, scale = 2)
  BigDecimal pricePerUnit;

  @Column(name = "price_currency", length = 3)
  String priceCurrency;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
  BigDecimal totalAmount;

  @Column(name = "total_currency", nullable = false, length = 3)
  String totalCurrency;

  @Column(nullable = false)
  LocalDate date;

  @Column(name = "fees", nullable = false, precision = 19, scale = 2)
  BigDecimal fees;

  @Column(length = 255)
  String description;

  protected TransactionJpaEntity() {}
}
