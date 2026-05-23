# Project Progress

## 2026-05-22 — Multi-module restructure + Money value object

- Docker Compose configured for local PostgreSQL 16-alpine
- Spring profiles: base (JPA + Flyway config), local (Docker datasource), prod (Supabase via env vars)
- Flyway migrations: V1 (create schema portfolio), V2 (create financial_account table)
- EquilyBackendApplicationTests re-enabled with @ActiveProfiles("local") + Testcontainers
- Full reactor BUILD SUCCESS confirmed (7 modules, 21s)
- Next: implement FinancialAccount aggregate root in portfolio-domain

## 2026-05-23 — FinancialAccount aggregate root

- Implemented full Portfolio domain: AccountType, AssetType, TransactionType enums; Ticker, AssetMetadata, Country value objects; Transaction (append-only, static factory), Holding (computed projection, French fiscal rule), FinancialAccount aggregate root
- 26 tests passing (TickerTest, TransactionTest, HoldingTest, FinancialAccountTest, DomainArchitectureTest)
- ArchUnit confirms zero Spring/JPA/Lombok deps in domain

## Architecture Decisions

- Lombok is forbidden everywhere. Java 21 records replace POJOs; explicit methods replace generated ones.
- `Money` is a record. Null constructor args throw `InvalidMoneyException`, not `NullPointerException`. Cross-currency arithmetic throws `CurrencyMismatchException` (RuntimeException).
- `EquilyBackendApplicationTests` was `@Disabled` until Docker Compose was wired; re-enabled 2026-05-22 with `@ActiveProfiles("local")` + Testcontainers.
- `ddl-auto: validate` — Hibernate never modifies schema. Flyway owns all DDL from day 1.
- Spring profiles: `local` (Docker dev datasource), `prod` (Supabase via env vars). No other profiles.
- Flyway migrations in `portfolio-infrastructure/src/main/resources/db/migration/portfolio/`. Naming: `V{n}__{description}.sql`.
- French fiscal rule: on SELL, `averageCostPrice` is not recalculated — only quantity decreases. `Holding` is a computed projection, never stored directly.
- `AssetInfo` (AssetType + AssetMetadata) is passed as a `Map<Ticker, AssetInfo>` parameter to `getHoldings()` as a deliberate temporary coupling until the Market Data bounded context exists.
