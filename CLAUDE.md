# Equily — Wealth Management Tracker

## Project Overview

Personal wealth management tracker (alternative to Finary), built for personal/family use. Focus: DDD, Hexagonal
Architecture, software quality.

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.5.14, Maven (multi-module)
- **Database**: Supabase (PostgreSQL) in prod, Docker PostgreSQL for local dev
- **Frontend**: Angular (later)
- **CI/CD**: GitLab CI/CD (compilation, tests, Docker build, quality gates, AWS deployment)

## Module Structure

```
equily-backend/
├── pom.xml                              (parent POM, packaging=pom)
├── shared-kernel/                       (Money, Country — no framework)
├── portfolio-domain/                    (ZERO framework deps)
├── portfolio-application/               (spring-context + spring-tx only)
├── portfolio-infrastructure/            (Spring Data JPA, Flyway, PostgreSQL)
├── portfolio-web/                       (Spring Web, REST controllers)
└── bootstrap/                           (@SpringBootApplication, application.yml)
```

Maven groupId: `com.equily` · Base package: `com.equily.<module>`

## Architecture Rules (NON-NEGOTIABLE)

### Hexagonal Architecture per Bounded Context

Each Bounded Context: 4 modules — `*-domain`, `*-application`, `*-infrastructure`, `*-web`.

- `*-domain/` → **ZERO framework deps** (no Spring, no JPA, no Lombok, no Jackson). Aggregates, entities, value objects,
  repository interfaces (ports).
- `*-application/` → Use cases. `spring-context` + `spring-tx` only (`@Service`, `@Transactional`).
- `*-infrastructure/` → Spring Data JPA adapters, external clients, Flyway, Supabase config.
- `*-web/` → REST controllers + DTOs.

### Persistence

- JPA entities ≠ domain entities. Always map `XxxJpaEntity` ↔ `Xxx` in repository adapters.
- Flyway owns all DDL. `ddl-auto: validate` — Hibernate never modifies schema.
- Migrations: `portfolio-infrastructure/src/main/resources/db/migration/portfolio/`, named `V{n}__{description}.sql`.
- Schema per Bounded Context: `portfolio` schema for Portfolio. Future contexts get their own schema.
- Tests against DB: Testcontainers + real PostgreSQL. **Never H2.**

### Transaction Model

`Transaction` is append-only. Holdings are derived projections recomputed from the transaction log.

### Spring Profiles

`local` (Docker dev) · `prod` (Supabase via env vars). No other profiles.

## Coding Conventions

- **No Lombok anywhere.** Use Java 21 records and explicit methods.
- **No `double` or `float`.** All monetary/quantity arithmetic: `BigDecimal` with explicit `RoundingMode`.
- `Money.equals` uses `BigDecimal.compareTo` (not `.equals`) — scale-insensitive comparison. This is a footgun.
- Static factory methods (not public constructors) on aggregates and entities.
- ArchUnit enforces domain purity: no `org.springframework.*`, `jakarta.persistence.*`, or `lombok.*` in `*.domain.*`.
- **French fiscal rule (Holding)**: SELL reduces quantity but never changes `averageCostPrice`.
  `averageCostPrice = weightedCostSum / totalBoughtQty` (not net quantity).

## Testing Strategy

- `*-domain`: pure JUnit 5 + AssertJ, no Spring context.
- `*-application`: Mockito mocks for ports.
- `*-infrastructure`: Testcontainers + real PostgreSQL.
- `*-web`: `@WebMvcTest`, no DB.
- Done means: tests pass + ArchUnit passes + zero build warnings.
- **Coverage gate**: every PR must maintain ≥ 80% instruction coverage on new code.
  SonarCloud enforces this automatically — a PR with < 80% coverage on new code will
  not be mergeable. When adding new classes, always write tests that cover:
    - All public methods (happy path + error cases)
    - All nullable branches (null/non-null paths in mappers and controllers)
    - All exception handler methods in `GlobalExceptionHandler`
      JaCoCo reports are available locally at `*/target/site/jacoco/index.html` after `.\mvnw.cmd verify`.

## Development Workflow

- Red → Green → Refactor.
- Conventional Commits: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`.

## Ubiquitous Language (use these names EXACTLY in code)

- **FinancialAccount** — Aggregate Root (Portfolio). Tax wrapper/account type (PEA, Crypto Wallet, etc.)
- **AccountType** (Enum), **AssetType** (Enum), **TransactionType** (Enum: BUY, SELL, DIVIDEND, DEPOSIT, WITHDRAWAL)
- **Holding** — Entity inside FinancialAccount. Shares/quantity of an asset in an account.
- **Transaction** — Entity inside FinancialAccount. Append-only.
- **Ticker**, **Quote** — Value Objects
- **Money** — Value Object: immutable, `BigDecimal` amount + `Currency`
- **Country** — Value Object: ISO 3166-1 alpha-2 code
- **AssetMetadata** — Value Object: fullName, ISIN (nullable for crypto), Country
- **WealthReport** — Read model (Analytics context, later)
- **NetWorth**, **NetNetWorth** — Value Objects (net of simulated local taxes)
- **TargetAllocation**, **CurrentAllocation**, **RebalancingOrder**, **GeographicalExposure** — Value Objects (later
  contexts)

## Bounded Contexts

1. **Portfolio** (active): `FinancialAccount`, `Holding`, `Transaction`
2. **Market Data**: `Asset`, `AssetMetadata`, `Ticker`, `Quote`
3. **Analytics**: `NetWorth`, `NetNetWorth`, read models
4. **Rebalancing**: `TargetAllocation`, `RebalancingOrder`
5. **Identity & Household**: `User`, `Household`, `Membership`

## Data Entry Strategy

Equily uses a **manual + CSV import** approach. No bank aggregator (DSP2/AISP) integration.

- **Manual entry**: users record transactions one by one via the UI
- **CSV import**: users export their transaction history from their broker
  (Degiro, Boursorama, Fortuneo, Binance, Coinbase, etc.) and import it into Equily.
  Each broker CSV format will have its own adapter in `portfolio-infrastructure`.

This means `Transaction` is the source of truth for all portfolio calculations.
Holdings, net worth, performance, and rebalancing are always derived from the transaction log.
