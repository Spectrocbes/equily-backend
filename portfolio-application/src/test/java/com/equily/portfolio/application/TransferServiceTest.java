package com.equily.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.TransferDirection;
import com.equily.portfolio.domain.account.AccountStatus;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.portfolio.domain.exception.InvalidTransactionException;
import com.equily.portfolio.domain.exception.TransferRoutingException;
import com.equily.portfolio.domain.marketdata.FxRatePort;
import com.equily.portfolio.domain.marketdata.MarketDataPort;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

  @Mock private FinancialAccountRepository repository;
  @Mock private FxRatePort fxRatePort;
  @Mock private MarketDataPort marketDataPort;

  @InjectMocks private TransferService service;

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final LocalDate TODAY = LocalDate.of(2026, 1, 1);

  private FinancialAccount cashAccount(UserId owner) {
    FinancialAccount acc =
        FinancialAccount.reconstruct(
            FinancialAccountId.generate(),
            "Checking",
            AccountType.CASH_ACCOUNT,
            new Money(new BigDecimal("5000"), EUR),
            List.of(),
            "BNP",
            owner,
            AccountSubType.CASH_ACCOUNT,
            TODAY,
            AccountStatus.ACTIVE,
            null,
            null);
    return acc;
  }

  private FinancialAccount savingsAccount(UserId owner) {
    return FinancialAccount.reconstruct(
        FinancialAccountId.generate(),
        "Livret A",
        AccountType.SAVINGS_ACCOUNT,
        new Money(new BigDecimal("1000"), EUR),
        List.of(),
        "BNP",
        owner,
        AccountSubType.LIVRET_A,
        TODAY,
        AccountStatus.ACTIVE,
        null,
        null);
  }

  @Test
  void standard_transfer_creates_two_linked_transactions() {
    UserId userId = UserId.generate();
    FinancialAccount from = cashAccount(userId);
    FinancialAccount to = savingsAccount(userId);

    when(repository.findById(from.id())).thenReturn(Optional.of(from));
    when(repository.findById(to.id())).thenReturn(Optional.of(to));
    when(repository.findAllByOwnerId(userId)).thenReturn(List.of(from, to));

    TransferCommand cmd =
        new TransferCommand(
            from.id(), to.id(), userId, new BigDecimal("500"), "EUR", TODAY, "Test transfer", null);

    UUID transferId = service.executeTransfer(cmd);

    assertThat(transferId).isNotNull();

    // from account: one OUTGOING TRANSFER transaction added
    assertThat(from.transactions()).hasSize(1);
    assertThat(from.transactions().get(0).type()).isEqualTo(TransactionType.TRANSFER);
    assertThat(from.transactions().get(0).transferDirection())
        .isEqualTo(TransferDirection.OUTGOING);
    assertThat(from.transactions().get(0).transferId()).isEqualTo(transferId);

    // to account: one INCOMING TRANSFER transaction added
    assertThat(to.transactions()).hasSize(1);
    assertThat(to.transactions().get(0).type()).isEqualTo(TransactionType.TRANSFER);
    assertThat(to.transactions().get(0).transferDirection()).isEqualTo(TransferDirection.INCOMING);
    assertThat(to.transactions().get(0).transferId()).isEqualTo(transferId);

    // both legs share the same transferId
    assertThat(from.transactions().get(0).transferId())
        .isEqualTo(to.transactions().get(0).transferId());

    verify(repository, times(2)).save(any(FinancialAccount.class));
  }

  @Test
  void executeTransfer_sets_direction_out_on_source() {
    UserId userId = UserId.generate();
    FinancialAccount from = cashAccount(userId);
    FinancialAccount to = savingsAccount(userId);

    when(repository.findById(from.id())).thenReturn(Optional.of(from));
    when(repository.findById(to.id())).thenReturn(Optional.of(to));
    when(repository.findAllByOwnerId(userId)).thenReturn(List.of(from, to));

    TransferCommand cmd =
        new TransferCommand(
            from.id(), to.id(), userId, new BigDecimal("200"), "EUR", TODAY, null, null);
    service.executeTransfer(cmd);

    assertThat(from.transactions()).hasSize(1);
    assertThat(from.transactions().get(0).transferDirection())
        .isEqualTo(TransferDirection.OUTGOING);
  }

  @Test
  void executeTransfer_sets_direction_in_on_destination() {
    UserId userId = UserId.generate();
    FinancialAccount from = cashAccount(userId);
    FinancialAccount to = savingsAccount(userId);

    when(repository.findById(from.id())).thenReturn(Optional.of(from));
    when(repository.findById(to.id())).thenReturn(Optional.of(to));
    when(repository.findAllByOwnerId(userId)).thenReturn(List.of(from, to));

    TransferCommand cmd =
        new TransferCommand(
            from.id(), to.id(), userId, new BigDecimal("200"), "EUR", TODAY, null, null);
    service.executeTransfer(cmd);

    assertThat(to.transactions()).hasSize(1);
    assertThat(to.transactions().get(0).transferDirection()).isEqualTo(TransferDirection.INCOMING);
  }

  @Test
  void external_transfer_creates_single_outgoing_transaction() {
    UserId userId = UserId.generate();
    FinancialAccount from = cashAccount(userId);

    when(repository.findById(from.id())).thenReturn(Optional.of(from));

    TransferCommand cmd =
        new TransferCommand(
            from.id(), null, userId, new BigDecimal("100"), "EUR", TODAY, null, "0xABCDEF");

    UUID transferId = service.executeTransfer(cmd);

    assertThat(transferId).isNotNull();
    assertThat(from.transactions()).hasSize(1);
    assertThat(from.transactions().get(0).transferDirection())
        .isEqualTo(TransferDirection.OUTGOING);
    assertThat(from.transactions().get(0).externalAddress()).isEqualTo("0xABCDEF");

    verify(repository, times(1)).save(any(FinancialAccount.class));
  }

  @Test
  void pea_over5y_transfer_creates_withdrawal_for_ps_tax() {
    UserId userId = UserId.generate();
    LocalDate openedFarBack = TODAY.minusYears(6);

    // PEA with cash balance of 10 000 and no holdings (portfolio value = 0)
    // totalDeposits = 0 (no DEPOSIT transactions) → gainRatio = 0 → no tax
    // But to test tax: simulate deposits > 0 via a deposit tx
    // To keep it simple: pea with 5000 balance, totalDeposits = 4000 → gain = 1000
    // We'll set up deposits manually
    FinancialAccount pea =
        FinancialAccount.reconstruct(
            FinancialAccountId.generate(),
            "PEA",
            AccountType.PEA,
            new Money(new BigDecimal("5000"), EUR),
            List.of(),
            "Fortuneo",
            userId,
            AccountSubType.PEA,
            openedFarBack,
            AccountStatus.ACTIVE,
            null,
            null);

    FinancialAccount checking = cashAccount(userId);
    pea.linkCheckingAccount(checking.id().value());

    when(repository.findById(pea.id())).thenReturn(Optional.of(pea));
    when(repository.findById(checking.id())).thenReturn(Optional.of(checking));

    TransferCommand cmd =
        new TransferCommand(
            pea.id(), checking.id(), userId, new BigDecimal("1000"), "EUR", TODAY, null, null);

    UUID transferId = service.executeTransfer(cmd);

    assertThat(transferId).isNotNull();
    // PEA: OUTGOING transfer + WITHDRAWAL for tax (when gain > 0)
    // Since totalDeposits=0 and liquidationValue=5000, gainRatio=1.0
    // taxableGain = 1000 * 1.0 = 1000, psTax = 1000 * 0.186 = 186
    long withdrawalCount =
        pea.transactions().stream().filter(t -> t.type() == TransactionType.WITHDRAWAL).count();
    assertThat(withdrawalCount).isEqualTo(1);
    long transferCount =
        pea.transactions().stream().filter(t -> t.type() == TransactionType.TRANSFER).count();
    assertThat(transferCount).isEqualTo(1);
  }

  @Test
  void transfer_throws_when_account_not_found() {
    UserId userId = UserId.generate();
    FinancialAccountId unknownId = FinancialAccountId.generate();

    when(repository.findById(unknownId)).thenReturn(Optional.empty());

    TransferCommand cmd =
        new TransferCommand(
            unknownId, null, userId, new BigDecimal("100"), "EUR", TODAY, null, "external");

    assertThatThrownBy(() -> service.executeTransfer(cmd))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void executeTransfer_throws_when_date_before_from_openedAt() {
    UserId userId = UserId.generate();
    LocalDate fromOpenedAt = LocalDate.of(2026, 1, 10);
    LocalDate transferDate = LocalDate.of(2026, 1, 5);

    FinancialAccount from =
        FinancialAccount.reconstruct(
            FinancialAccountId.generate(),
            "Checking",
            AccountType.CASH_ACCOUNT,
            new Money(new BigDecimal("5000"), EUR),
            List.of(),
            "BNP",
            userId,
            AccountSubType.CASH_ACCOUNT,
            fromOpenedAt,
            AccountStatus.ACTIVE,
            null,
            null);
    FinancialAccount to = savingsAccount(userId);

    when(repository.findById(from.id())).thenReturn(Optional.of(from));
    when(repository.findById(to.id())).thenReturn(Optional.of(to));

    TransferCommand cmd =
        new TransferCommand(
            from.id(), to.id(), userId, new BigDecimal("100"), "EUR", transferDate, null, null);

    assertThatThrownBy(() -> service.executeTransfer(cmd))
        .isInstanceOf(InvalidTransactionException.class)
        .hasMessageContaining(transferDate.toString())
        .hasMessageContaining(fromOpenedAt.toString());
  }

  @Test
  void executeTransfer_throws_when_date_before_to_openedAt() {
    UserId userId = UserId.generate();
    LocalDate toOpenedAt = LocalDate.of(2026, 1, 10);
    LocalDate transferDate = LocalDate.of(2026, 1, 5);

    FinancialAccount from = cashAccount(userId);
    FinancialAccount to =
        FinancialAccount.reconstruct(
            FinancialAccountId.generate(),
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(new BigDecimal("1000"), EUR),
            List.of(),
            "BNP",
            userId,
            AccountSubType.LIVRET_A,
            toOpenedAt,
            AccountStatus.ACTIVE,
            null,
            null);

    when(repository.findById(from.id())).thenReturn(Optional.of(from));
    when(repository.findById(to.id())).thenReturn(Optional.of(to));

    TransferCommand cmd =
        new TransferCommand(
            from.id(), to.id(), userId, new BigDecimal("100"), "EUR", transferDate, null, null);

    assertThatThrownBy(() -> service.executeTransfer(cmd))
        .isInstanceOf(InvalidTransactionException.class)
        .hasMessageContaining(transferDate.toString())
        .hasMessageContaining(toOpenedAt.toString());
  }

  @Test
  void executeTransfer_uses_most_recent_openedAt_as_min_date() {
    UserId userId = UserId.generate();
    LocalDate toOpenedAt = LocalDate.of(2026, 1, 15);
    LocalDate transferDate = LocalDate.of(2026, 1, 10);

    FinancialAccount from = cashAccount(userId);
    FinancialAccount to =
        FinancialAccount.reconstruct(
            FinancialAccountId.generate(),
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(new BigDecimal("1000"), EUR),
            List.of(),
            "BNP",
            userId,
            AccountSubType.LIVRET_A,
            toOpenedAt,
            AccountStatus.ACTIVE,
            null,
            null);

    when(repository.findById(from.id())).thenReturn(Optional.of(from));
    when(repository.findById(to.id())).thenReturn(Optional.of(to));

    TransferCommand cmd =
        new TransferCommand(
            from.id(), to.id(), userId, new BigDecimal("100"), "EUR", transferDate, null, null);

    assertThatThrownBy(() -> service.executeTransfer(cmd))
        .isInstanceOf(InvalidTransactionException.class)
        .hasMessageContaining(toOpenedAt.toString());
  }

  @Test
  void executeTransfer_allows_date_equal_to_max_openedAt() {
    UserId userId = UserId.generate();
    LocalDate toOpenedAt = LocalDate.of(2026, 1, 15);
    LocalDate transferDate = LocalDate.of(2026, 1, 15);

    FinancialAccount from = cashAccount(userId);
    FinancialAccount to =
        FinancialAccount.reconstruct(
            FinancialAccountId.generate(),
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(new BigDecimal("1000"), EUR),
            List.of(),
            "BNP",
            userId,
            AccountSubType.LIVRET_A,
            toOpenedAt,
            AccountStatus.ACTIVE,
            null,
            null);

    when(repository.findById(from.id())).thenReturn(Optional.of(from));
    when(repository.findById(to.id())).thenReturn(Optional.of(to));
    when(repository.findAllByOwnerId(userId)).thenReturn(List.of(from, to));

    TransferCommand cmd =
        new TransferCommand(
            from.id(), to.id(), userId, new BigDecimal("100"), "EUR", transferDate, null, null);

    assertThatNoException().isThrownBy(() -> service.executeTransfer(cmd));
  }

  @Test
  void transfer_throws_on_routing_violation() {
    UserId userId = UserId.generate();
    FinancialAccount savings1 = savingsAccount(userId);
    FinancialAccount savings2 = savingsAccount(userId);

    when(repository.findById(savings1.id())).thenReturn(Optional.of(savings1));
    when(repository.findById(savings2.id())).thenReturn(Optional.of(savings2));

    TransferCommand cmd =
        new TransferCommand(
            savings1.id(), savings2.id(), userId, new BigDecimal("100"), "EUR", TODAY, null, null);

    assertThatThrownBy(() -> service.executeTransfer(cmd))
        .isInstanceOf(TransferRoutingException.class);
  }
}
