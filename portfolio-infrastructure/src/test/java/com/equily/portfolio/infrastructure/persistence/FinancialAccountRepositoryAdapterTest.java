package com.equily.portfolio.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("local")
class FinancialAccountRepositoryAdapterTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private FinancialAccountJpaRepository jpaRepository;

  @Autowired private TestEntityManager testEntityManager;

  private FinancialAccountRepositoryAdapter adapter;

  private static final Currency EUR = Currency.getInstance("EUR");

  // One user shared across all tests in a single test run; UUID stable per container lifecycle.
  private UserId testUserId;

  @BeforeEach
  void setUp() {
    adapter = new FinancialAccountRepositoryAdapter(jpaRepository);
    testUserId = insertTestUser();
  }

  // identity.users FK requires a real user row. Use native SQL because UserJpaEntity
  // is in identity-infrastructure which is not on this module's test classpath.
  private UserId insertTestUser() {
    UUID id = UUID.randomUUID();
    testEntityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO identity.users (id, email, password_hash, display_name)"
                + " VALUES (:id, :email, :pw, :name)"
                + " ON CONFLICT DO NOTHING")
        .setParameter("id", id)
        .setParameter("email", id + "@test.local")
        .setParameter("pw", "hashed")
        .setParameter("name", "Test User")
        .executeUpdate();
    return new UserId(id);
  }

  @Test
  void save_and_findById_roundtrip() {
    FinancialAccount account =
        FinancialAccount.open(
            "PEA Account",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            testUserId,
            null);

    Transaction deposit =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("1000.00"), EUR),
            LocalDate.of(2024, 1, 1),
            BigDecimal.ZERO,
            null);
    account.recordTransaction(deposit);

    Transaction buy =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            new BigDecimal("2"),
            new Money(new BigDecimal("150.00"), EUR),
            new Money(new BigDecimal("300.00"), EUR),
            LocalDate.of(2024, 1, 15),
            BigDecimal.ZERO,
            null);
    account.recordTransaction(buy);

    adapter.save(account);
    testEntityManager.flush();
    testEntityManager.clear();

    Optional<FinancialAccount> found = adapter.findById(account.id());

    assertThat(found).isPresent();
    assertThat(found.get().name()).isEqualTo("PEA Account");
    assertThat(found.get().accountType()).isEqualTo(AccountType.PEA);
    assertThat(found.get().balance()).isEqualTo(new Money(new BigDecimal("700.00"), EUR));
    assertThat(found.get().transactions()).hasSize(2);
    assertThat(found.get().transactions().get(0).type()).isEqualTo(TransactionType.DEPOSIT);
    assertThat(found.get().transactions().get(1).type()).isEqualTo(TransactionType.BUY);
    assertThat(found.get().transactions().get(1).ticker()).isEqualTo(new Ticker("AAPL"));
    assertThat(found.get().ownerId()).isEqualTo(testUserId);
  }

  @Test
  void findAll_returns_all_saved_accounts() {
    FinancialAccount pea =
        FinancialAccount.open(
            "PEA",
            AccountType.PEA,
            new Money(new BigDecimal("500.00"), EUR),
            "Fortuneo",
            testUserId,
            null);
    FinancialAccount crypto =
        FinancialAccount.open(
            "Crypto Wallet",
            AccountType.CRYPTO_WALLET,
            new Money(new BigDecimal("200.00"), EUR),
            "Fortuneo",
            testUserId,
            null);

    adapter.save(pea);
    adapter.save(crypto);
    testEntityManager.flush();
    testEntityManager.clear();

    List<FinancialAccount> all = adapter.findAll();

    assertThat(all).hasSize(2);
  }

  @Test
  void findAllByOwnerId_returns_only_accounts_for_that_user() {
    // Second user — also needs a real row in identity.users
    UserId user2 = insertTestUser();

    FinancialAccount acc1 =
        FinancialAccount.open(
            "User1 PEA",
            AccountType.PEA,
            new Money(new BigDecimal("500.00"), EUR),
            "Fortuneo",
            testUserId,
            null);
    FinancialAccount acc2 =
        FinancialAccount.open(
            "User2 PEA",
            AccountType.PEA,
            new Money(new BigDecimal("200.00"), EUR),
            "Fortuneo",
            user2,
            null);

    adapter.save(acc1);
    adapter.save(acc2);
    testEntityManager.flush();
    testEntityManager.clear();

    List<FinancialAccount> user1Accounts = adapter.findAllByOwnerId(testUserId);

    assertThat(user1Accounts).hasSize(1);
    assertThat(user1Accounts.get(0).name()).isEqualTo("User1 PEA");
    assertThat(user1Accounts.get(0).ownerId()).isEqualTo(testUserId);
  }

  @Test
  void save_with_broker_preserves_broker_in_roundtrip() {
    FinancialAccount account =
        FinancialAccount.open(
            "Fortuneo PEA",
            AccountType.PEA,
            new Money(new BigDecimal("500.00"), EUR),
            "Fortuneo",
            testUserId,
            null);

    adapter.save(account);
    testEntityManager.flush();
    testEntityManager.clear();

    Optional<FinancialAccount> found = adapter.findById(account.id());

    assertThat(found).isPresent();
    assertThat(found.get().broker()).isEqualTo("Fortuneo");
  }

  @Test
  void save_account_with_multiple_transactions_preserves_order() {
    FinancialAccount account =
        FinancialAccount.open(
            "Compte-Titres",
            AccountType.COMPTE_TITRES,
            new Money(new BigDecimal("2000.00"), EUR),
            "Fortuneo",
            testUserId,
            null);

    Transaction deposit =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("500.00"), EUR),
            LocalDate.of(2024, 1, 1),
            null,
            null);
    account.recordTransaction(deposit);

    Transaction buy =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("MSFT"),
            new BigDecimal("1"),
            new Money(new BigDecimal("300.00"), EUR),
            new Money(new BigDecimal("300.00"), EUR),
            LocalDate.of(2024, 1, 15),
            null,
            null);
    account.recordTransaction(buy);

    adapter.save(account);
    testEntityManager.flush();
    testEntityManager.clear();

    Optional<FinancialAccount> found = adapter.findById(account.id());

    assertThat(found).isPresent();
    assertThat(found.get().transactions()).hasSize(2);
    // DEPOSIT is on 2024-01-01, BUY on 2024-01-15 → ordered by date ASC
    assertThat(found.get().transactions().get(0).type()).isEqualTo(TransactionType.DEPOSIT);
    assertThat(found.get().transactions().get(1).type()).isEqualTo(TransactionType.BUY);
  }
}
