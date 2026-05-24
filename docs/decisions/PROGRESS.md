# Project Progress

## 2026-05-22 — Multi-module restructure + Money value object

- Docker Compose configured for local PostgreSQL 16-alpine
- Spring profiles: base (JPA + Flyway config), local (Docker datasource), prod (Supabase via env vars)
- Flyway migrations: V1 (create schema portfolio), V2 (create financial_account table)
- EquilyBackendApplicationTests re-enabled with @ActiveProfiles("local") + Testcontainers
- Full reactor BUILD SUCCESS confirmed (7 modules, 21s)
- Next: implement FinancialAccount aggregate root in portfolio-domain

## 2026-05-23 — FinancialAccount aggregate root

- Implemented full Portfolio domain: AccountType, AssetType, TransactionType enums; Ticker, AssetMetadata, Country value
  objects; Transaction (append-only, static factory), Holding (computed projection, French fiscal rule),
  FinancialAccount aggregate root
- 26 tests passing (TickerTest, TransactionTest, HoldingTest, FinancialAccountTest, DomainArchitectureTest)
- ArchUnit confirms zero Spring/JPA/Lombok deps in domain

## 2026-05-23 — CI Phase 1 pipeline

- CI Phase 1 pipeline configured: Spotless (Google Java Format) + JaCoCo + SonarCloud + Dependabot + GitHub Actions
  workflow
- `spotless:apply` reformatted 37 files to Google Java Format — one-time normalization
- `GITHUB_TOKEN` is auto-injected by GitHub Actions, no setup needed
- `SONAR_TOKEN` added to GitHub repository secrets
- Full reactor BUILD SUCCESS (7 modules, 25.9s) including Spotless check
- Next: FinancialAccountRepository port in portfolio-domain + JPA adapter in portfolio-infrastructure

## 2026-05-23 — CI pipeline fully operational

- CI pipeline fully operational after fixing `.mvn/maven.config` Windows-only SSL config
- JaCoCo aggregate report module `coverage-report` added — single `jacoco.xml` at
  `coverage-report/target/site/jacoco-aggregate/jacoco.xml`
- SonarCloud now receives real coverage data (39KB report, shared-kernel + portfolio-domain covered)
- Root cause of SSL failure: `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` in `.mvn/maven.config` breaks Linux CI
- Next: merge CI branch, then FinancialAccountRepository port + JPA adapter

## 2026-05-24 — CI coverage reporting stabilised

- Reverted JaCoCo aggregate report approach (`coverage-report` module deleted) — SonarCloud cross-module file warnings
  made it unusable
- Restored per-module JaCoCo reports: `portfolio-domain`, `portfolio-application`, `portfolio-infrastructure`,
  `portfolio-web` each generate `target/site/jacoco/jacoco.xml`
- `sonar.coverage.jacoco.xmlReportPaths` set to relative `target/site/jacoco/jacoco.xml` — SonarCloud resolves it
  per-module correctly
- `sonar.organization` corrected to lowercase `spectrocbes` (was `Spectrocbes`)
- `.mvn/maven.config` deleted — contained `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT`, breaking all Linux CI steps
- Next: merge CI branch, then FinancialAccountRepository port + JPA adapter

## 2026-05-24 — FinancialAccountRepository port + JPA adapter

- `FinancialAccountRepository` port added to `portfolio-domain` (pure Java interface, zero framework deps)
- Flyway V3 (transaction table), V4 (balance column on financial_account), V5 (CHAR→VARCHAR normalization for Hibernate schema validation)
- JPA entities: `FinancialAccountJpaEntity` + `TransactionJpaEntity` (package-private, infrastructure only)
- `FinancialAccountJpaEntity` implements `Persistable<UUID>` — externally-managed UUIDs require this to prevent Hibernate treating new entities as detached (StaleObjectStateException)
- `FinancialAccountMapper`: static `toJpa()` / `toDomain()` anti-corruption layer; `toDomain()` calls `reconstruct()` not `open()` to reload persisted state without replaying transactions
- `FinancialAccountRepositoryAdapter`: implements port; calls `existsById()` before save to set `isNew` flag correctly
- `CrossLayerArchitectureTest`: ArchUnit rule (scans all `com.equily`) enforcing `reconstruct()` only callable from `portfolio-infrastructure` or `portfolio-domain` — no `.allowEmptyShould(true)`
- `@EntityScan("com.equily")` + `@EnableJpaRepositories("com.equily")` added to `EquilyBackendApplication` — `@AutoConfigurationPackage` only covers the declaring class's package, not the full multi-module tree
- 55 tests, 0 failures
- Next: application use cases (GetAllAccounts, RecordTransaction) + REST endpoints

## 2026-05-24 — Application layer + REST endpoints

- `CreateFinancialAccountCommand`, `RecordTransactionCommand` records in `portfolio-application`
- `FinancialAccountUseCase` input port (interface); `FinancialAccountService` implementation (`@Service @Transactional`, package-private)
- `AccountNotFoundException` added to `portfolio-domain/exception`
- `FinancialAccountController`: 5 endpoints — `GET /api/v1/accounts`, `GET /{id}`, `POST /`, `POST /{id}/transactions`, `GET /{id}/transactions`
- DTOs: `CreateAccountRequest`, `RecordTransactionRequest`, `FinancialAccountResponse`, `TransactionResponse` (all records, no domain types exposed)
- `GlobalExceptionHandler`: maps `AccountNotFoundException` → 404, `InsufficientFundsException` → 422, `InvalidTransactionException` → 400
- `PortfolioWebTestApplication` minimal bootstrap for `@WebMvcTest` (same pattern as infrastructure module)
- `@MockBean` → `@MockitoBean` (Spring Boot 3.4 deprecation)
- 67 tests total, 0 failures — shared-kernel: 24, portfolio-domain: 27, portfolio-application: 6, portfolio-infrastructure: 3, portfolio-web: 6, bootstrap: 1
- Next: commit + push, then wire Angular or add remaining use cases

## 2026-05-24 — Application layer + REST layer (with validation hardening)

- `FinancialAccountUseCase` input port (interface); `FinancialAccountService` implementation (`@Service @Transactional`, package-private)
- Commands: `CreateFinancialAccountCommand`, `RecordTransactionCommand` records in `portfolio-application`
- `AccountNotFoundException` added to `portfolio-domain/exception`
- `FinancialAccountController`: 5 endpoints — `GET /api/v1/accounts`, `GET /{id}`, `POST /`, `POST /{id}/transactions`, `GET /{id}/transactions`
- DTOs: `CreateAccountRequest`, `RecordTransactionRequest`, `FinancialAccountResponse`, `TransactionResponse` (all records, no domain types exposed)
- Jakarta validation on request DTOs: `@NotBlank` on `name`, `accountType`, `currency`, `type`, `totalCurrency`; `@NotNull` on `initialBalance`, `totalAmount`, `date`; `ticker`/`quantity`/`pricePerUnit`/`priceCurrency` intentionally nullable (DEPOSIT/WITHDRAWAL have no asset)
- `GlobalExceptionHandler`: `AccountNotFoundException` → 404, `InsufficientFundsException` → 422, `InvalidTransactionException` → 400, `IllegalArgumentException` → 400 (catches invalid UUID path variables and invalid enum values)
- `PortfolioWebTestApplication` minimal bootstrap for `@WebMvcTest`; `@MockitoBean` (Spring Boot 3.4 deprecation of `@MockBean`)
- 67 tests total, 0 failures — shared-kernel: 24, portfolio-domain: 27, portfolio-application: 6, portfolio-infrastructure: 3, portfolio-web: 6, bootstrap: 1
- Next: test the API manually with Docker running, then Angular frontend

## Architecture Decisions

- Lombok is forbidden everywhere. Java 21 records replace POJOs; explicit methods replace generated ones.
- `Money` is a record. Null constructor args throw `InvalidMoneyException`, not `NullPointerException`. Cross-currency
  arithmetic throws `CurrencyMismatchException` (RuntimeException).
- `EquilyBackendApplicationTests` was `@Disabled` until Docker Compose was wired; re-enabled 2026-05-22 with
  `@ActiveProfiles("local")` + Testcontainers.
- `ddl-auto: validate` — Hibernate never modifies schema. Flyway owns all DDL from day 1.
- Spring profiles: `local` (Docker dev datasource), `prod` (Supabase via env vars). No other profiles.
- Flyway migrations in `portfolio-infrastructure/src/main/resources/db/migration/portfolio/`. Naming:
  `V{n}__{description}.sql`.
- French fiscal rule: on SELL, `averageCostPrice` is not recalculated — only quantity decreases. `Holding` is a computed
  projection, never stored directly.
- `AssetInfo` (AssetType + AssetMetadata) is passed as a `Map<Ticker, AssetInfo>` parameter to `getHoldings()` as a
  deliberate temporary coupling until the Market Data bounded context exists.
