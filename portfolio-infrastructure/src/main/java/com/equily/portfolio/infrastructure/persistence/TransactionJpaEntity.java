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

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
  BigDecimal totalAmount;

  @Column(nullable = false)
  LocalDate date;

  @Column(name = "fees", nullable = false, precision = 19, scale = 2)
  BigDecimal fees;

  @Column(length = 255)
  String description;

  @Column(name = "currency", nullable = false, length = 3)
  String currency;

  @Column(name = "amount_eur", nullable = false, precision = 19, scale = 4)
  BigDecimal amountEur;

  @Column(name = "eur_fx_rate", nullable = false, precision = 10, scale = 6)
  BigDecimal eurFxRate;

  @Column(name = "liquidation_value_at_withdrawal", precision = 19, scale = 4)
  BigDecimal liquidationValueAtWithdrawal;

  @Column(name = "gross_withdrawal_amount", precision = 19, scale = 4)
  BigDecimal grossWithdrawalAmount;

  @Column(name = "transfer_id")
  UUID transferId;

  @Column(name = "linked_account_id")
  UUID linkedAccountId;

  @Column(name = "external_address", length = 255)
  String externalAddress;

  @Column(name = "transfer_direction", length = 10)
  String transferDirection;

  protected TransactionJpaEntity() {}
}
