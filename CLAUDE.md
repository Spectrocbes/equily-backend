# Equily — Wealth Management Tracker

## Project Overview
Equily is a personal wealth management tracker (custom alternative to Finary), designed for personal and family use. The project is built with a strong focus on software quality, DevOps principles, Domain-Driven Design (DDD), and Hexagonal Architecture.

## Tech Stack
- **Backend**: Java 21, Spring Boot 3.5.14, Maven (multi-module)
- **Database**: Supabase (PostgreSQL) in production, Docker PostgreSQL for local dev
- **Frontend**: Angular (later)
- **CI/CD**: GitLab CI/CD (compilation, tests, Docker build, quality gates, AWS deployment)

## Architecture Principles (NON-NEGOTIABLE)

### Hexagonal Architecture per Bounded Context
Each Bounded Context is split into 4 Maven modules:
- `*-domain/` → Pure business logic. **ZERO framework dependencies** (no Spring, no JPA annotations, no Lombok, no Jackson). Contains aggregates, entities, value objects, and repository **interfaces** (ports).
- `*-application/` → Use cases, commands, queries. May use `spring-context` and `spring-tx` for `@Service` and `@Transactional` only.
- `*-infrastructure/` → Framework implementations: Spring Data JPA repositories (adapters), external API clients, Supabase config.
- `*-web/` → REST Controllers and DTOs for the Angular frontend.

### Persistence rules
- JPA entities are **never** the same classes as domain entities. Always map between `XxxJpaEntity` (in infrastructure) and `Xxx` (in domain) inside repository adapters.
- Use Flyway for SQL migrations from day 1.
- Tests against DB use Testcontainers with real PostgreSQL. **Never H2.**

### Transaction model
- `Transaction` entities are **append-only / event-sourcing lite**. Holdings are derived projections recomputable from the transaction log.

## Bounded Contexts (planned)
1. **Portfolio** (start here): `FinancialAccount`, `Holding`, `Transaction`
2. **Market Data**: `Asset`, `AssetMetadata`, `Ticker`, `Quote`
3. **Analytics**: `NetWorth`, `NetNetWorth`, `CurrentAllocation`, `GeographicalExposure` (read models)
4. **Rebalancing**: `TargetAllocation`, `RebalancingOrder`
5. **Identity & Household**: `User`, `Household`, `Membership`

Only the **Portfolio** context is implemented at the start. Other contexts are added incrementally.

## Ubiquitous Language (use these names EXACTLY in code)
- **WealthReport**: consolidated global net worth (read model, lives in Analytics later)
- **FinancialAccount** (Aggregate Root in Portfolio): a tax wrapper or account type (PEA, Crypto Wallet, Cash Account, Real Estate)
- **AccountType** (Enum), **AssetType** (Enum)
- **Holding** (Entity inside FinancialAccount): shares or quantity of an Asset in an Account
- **Transaction** (Entity inside FinancialAccount), **TransactionType** (Enum: BUY, SELL, DIVIDEND, DEPOSIT, WITHDRAWAL)
- **Ticker** (Value Object), **Quote** (Value Object)
- **Money** (Value Object): immutable, `BigDecimal` amount + `Currency`
- **Country** (Value Object): ISO code
- **AssetMetadata** (Value Object): full name, ISIN, Country
- **NetWorth**, **NetNetWorth** (Value Objects, net of simulated local taxes)
- **TargetAllocation**, **CurrentAllocation** (Value Objects)
- **RebalancingOrder** (Value Object)
- **GeographicalExposure** (Value Object)

## Exclusive Features (later phases)
1. Actionable Rebalancing Engine (what to buy with monthly cash to hit target allocation)
2. Geographical Exposure Analytics
3. Granular Multi-User/Household Contexts via Spring Security

## Module Structure (initial)
```
equily-backend/
├── pom.xml                              (parent POM, packaging=pom)
├── shared-kernel/                       (Money, Currency, Country — no framework)
├── portfolio-domain/                    (ZERO framework deps)
├── portfolio-application/               (spring-context + spring-tx only)
├── portfolio-infrastructure/            (Spring Data JPA, Flyway, PostgreSQL)
├── portfolio-web/                       (Spring Web, REST controllers)
└── bootstrap/                           (@SpringBootApplication, application.yml)
```

Maven groupId: `com.equily`
Base Java package: `com.equily.<module>` (e.g. `com.equily.shared`, `com.equily.portfolio.domain`)

## Quality Guardrails
- **ArchUnit** tests in `portfolio-domain` enforcing: no class in `*.domain.*` imports `org.springframework.*`, `jakarta.persistence.*`, or `lombok.*`.
- All `Money` arithmetic uses `BigDecimal` with explicit `RoundingMode`. Never `double`.
- `Money.equals` uses `BigDecimal.compareTo`, not `BigDecimal.equals` (which compares scale).
- Money cannot be added across different currencies without an explicit `ExchangeRate`.

## Testing strategy
- Unit tests in `*-domain` (pure JUnit + AssertJ, no Spring context)
- Application tests with mocked ports (Mockito)
- Infrastructure tests with Testcontainers (real PostgreSQL)
- Web tests with `@WebMvcTest` (no DB)

## Development workflow
- Each feature follows: failing test → minimal implementation → refactor.
- Commit messages follow Conventional Commits (feat, fix, refactor, test, chore, docs).
- A feature is "done" only when: tests pass, ArchUnit passes, no warnings in build.