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

## 2026-05-24 — Security + end-to-end validation

- `SecurityConfig` added in `bootstrap` under `@Profile("local")` — disables CSRF and permits all requests; `spring-boot-starter-security` was already declared in bootstrap's pom
- Full API tested manually with Postman: `POST /api/v1/accounts`, `POST /api/v1/accounts/{id}/transactions` (DEPOSIT and BUY), `GET /api/v1/accounts`, `GET /api/v1/accounts/{id}/transactions` — all working correctly
- Error cases validated end-to-end: invalid UUID → 400, invalid enum → 400, insufficient funds → 422
- End-to-end stack confirmed: HTTP → Controller → Service → Repository → PostgreSQL (Docker)
- Next: Angular frontend initialisation

## 2026-05-26 — broker field added to FinancialAccount

- `broker` field added to `FinancialAccount` aggregate (mandatory, NOT NULL throughout the stack)
- Flyway V6: `ADD COLUMN broker VARCHAR(100) NOT NULL`
- `FinancialAccountJpaEntity`: `@Column(name = "broker", length = 100, nullable = false)`
- `FinancialAccount.open()`: validates broker not null/blank — throws `InvalidFinancialAccountException`
- `CreateAccountRequest`: `@NotBlank String broker` — enforced at REST layer
- `CreateFinancialAccountCommand`: `String broker` record component
- `FinancialAccountResponse`: `String broker` field exposed in API response
- `POST /api/v1/accounts` returns `{"id": "..."}` JSON (was plain string)
- Tests updated: `broker = "Fortuneo"` in all fixtures across domain, application, infrastructure, and web layers
- 2 new domain tests: `open_with_null_broker_throws`, `open_with_blank_broker_throws`
- 71 tests total, 0 failures

## 2026-05-27 — fees field added to Transaction

- `fees` field added to `Transaction` record (`BigDecimal`, between `date` and `description`)
- Flyway V8: `ADD COLUMN fees NUMERIC(19,2) NOT NULL DEFAULT 0` on `portfolio.transaction`
- `Transaction.of()`: nullable fees input defaults to `BigDecimal.ZERO`; negative fees throw `InvalidTransactionException`
- `Holding.computeFrom()`: BUY cost basis now includes fees — `weightedCostSum += (qty × price) + fees`
- `RecordTransactionCommand`, `RecordTransactionRequest`, `TransactionResponse`: `fees` field added (nullable in request — defaults to 0 in domain)
- `TransactionJpaEntity`: `@Column(name = "fees", nullable = false, precision = 19, scale = 2)`
- `FinancialAccountMapper`: `toJpaTransaction()` maps `t.fees()` → `tx.fees`; `toDomainTransaction()` passes `tx.fees` to canonical constructor
- 78 tests, 0 failures — 7 new tests including `buy_with_fees_includes_fees_in_average_cost` and `negative_fees_throws_InvalidTransactionException`

## 2026-05-27 — GET /accounts/{id}/holdings endpoint

- `HoldingResponse` DTO added to `portfolio-web` (ticker, quantity, averageCostPrice, currency, totalInvested)
- `FinancialAccountUseCase.getHoldings(FinancialAccountId)` added to input port
- `FinancialAccountService.getHoldings()`: builds `Map<Ticker, AssetInfo>` from transaction log (`AssetType.STOCK` default, `Country("US")` placeholder — TODO: wire MarketDataContext in Phase 2)
- `GET /api/v1/accounts/{id}/holdings` endpoint added to `FinancialAccountController`
- 82 tests, 0 failures — 4 new tests: service happy path + 404, controller 200 + 404

## 2026-05-27 — Holding.totalFeesPaid — fees separated from averageCostPrice

- `averageCostPrice` is now pure fiscal price (excludes fees) — `weightedCostSum = qty × price` only
- `totalInvested = qty × averageCostPrice` (no fees)
- `totalFeesPaid` new field on `Holding` — cumulative brokerage fees on BUY transactions
- `HoldingResponse` updated with `totalFeesPaid` field
- `buy_with_fees_includes_fees_in_average_cost` test removed (was incorrect); replaced with `buy_with_fees_avgcost_excludes_fees` + `buy_then_sell_preserves_avgcost_and_accumulates_fees`
- 87 tests, 0 failures

## 2026-05-29 — Phase 4 Session 1: CSV import for Boursobank

- `CsvImportResult` domain record (imported/skipped/errors/errorDetails/transactions)
- `BrokerCsvParserPort` application port — hexagonal boundary; controller depends on the interface, never on infrastructure parsers directly
- `BoursobankOperationParser`: parses operations CSV (DEPOSIT/BUY/SELL/DIVIDEND/WITHDRAWAL); maps `Opération` column via keyword matching; ISIN used as ticker (Phase 1)
- `BoursobankPositionParser`: parses positions CSV; each row becomes a synthetic BUY at `buyingPrice` on `lastMovementDate`
- `BrokerCsvParserAdapter` (`@Component`): dispatches to the right parser by `broker_mode` key (e.g. `BOURSOBANK_OPERATIONS`)
- `CsvParsingException` in `portfolio-application` — accessible from both infrastructure (throwers) and web (handlers)
- `POST /api/v1/accounts/{id}/import/csv` multipart endpoint: params `file`, `broker`, `mode`; returns `CsvImportResponse`
- Deduplication in `FinancialAccountService.importCsv()` by `(date|ticker|amount)` key — skips within-file duplicates too
- Sample CSV test resources at `portfolio-infrastructure/src/test/resources/csv/` — replace with real Boursobank exports
- Apache Commons CSV 1.11.0 + Commons IO 2.16.1 added to `portfolio-infrastructure`; managed in root `dependencyManagement`
- 110 tests, 0 failures (was 87)
- Next: frontend CSV import UI

## 2026-05-29 — Phase 5 Session 1: Authentication & Multi-user support (identity bounded context)

- Two new Maven modules: `identity-domain` (pure Java, zero framework deps) and `identity-infrastructure` (Spring Security, JPA, JWT)
- **identity-domain**: `UserId`, `User`, `HouseholdId`, `Household`, `HouseholdMemberRole`; `UserRepository` port; `UserAlreadyExistsException`, `InvalidCredentialsException`, `UserNotFoundException`
- **identity-infrastructure persistence**: `UserJpaEntity`, `RefreshTokenJpaEntity` (both implement `Persistable<UUID>`); `UserRepositoryAdapter`; `RefreshTokenService` (in persistence package — same package as JPA classes it manipulates)
- **identity-infrastructure security**: `JwtService` (RS256, reads PEM files from filesystem); `JwtAuthenticationFilter` (`OncePerRequestFilter`); `SecurityConfig` (replaces old `@Profile("local")` bootstrap config — now global, JWT-stateless, permits `/auth/**`)
- **identity-infrastructure usecase**: `AuthService` (register, login, refresh, logout); `AuthTokenPair` record
- Flyway: 5 new migrations — identity `V10`–`V13` (identity schema, users, households, refresh_tokens) + portfolio `V14` (nullable `user_id` FK on `financial_account`). Identity migrations start at V10 to avoid flat namespace conflict with portfolio V1–V8.
- `POST /auth/register` (201), `POST /auth/login` (200), `POST /auth/refresh` (200), `POST /auth/logout` (204), `GET /auth/me` (200) — wired in `AuthController` in `portfolio-web`
- `GlobalExceptionHandler` extended: `UserAlreadyExistsException` → 409, `InvalidCredentialsException` → 401
- `TestSecurityConfig` added to portfolio-web test sources — permits all + CSRF disabled — imported in both `@WebMvcTest` test classes to isolate from real `SecurityConfig`
- `FinancialAccountControllerTest` updated with `@Import(TestSecurityConfig.class)` to survive security now being on the test classpath
- JWT key paths: local profile → `src/main/resources/jwt-{private,public}.pem` (PEM files already existed in bootstrap); prod profile → `${JWT_PRIVATE_KEY_PATH}` / `${JWT_PUBLIC_KEY_PATH}` env vars
- 130 tests, 0 failures (was 110): shared-kernel 24, portfolio-domain 33, portfolio-application 16, portfolio-infrastructure 21, identity-domain 4, identity-infrastructure 6, portfolio-web 25, bootstrap 1
- Next: frontend CSV import UI or wire `user_id` onto `FinancialAccount` aggregate

## 2026-05-30 — Phase 5 Session 2: CI fix + coverage gap closed

- CI fix: `JwtService` now loads PEM files from classpath first (via `getResourceAsStream`), filesystem fallback — makes
  CI pass without PEM files on the runner
- `TestJwtConfig`: `@TestConfiguration` in bootstrap test sources generating an in-memory RSA-2048 key pair via
  `@Primary JwtService` — no PEM files needed in CI
- `application.yml` local profile: JWT paths changed from `src/main/resources/jwt-*.pem` → `jwt-*.pem`
  (classpath-relative)
- `SecurityConfig`: `@SuppressWarnings("java:S4502")` on class + `// NOSONAR — stateless JWT API, CSRF not applicable`
  on the csrf line — suppresses SonarCloud security hotspot for intentional CSRF disable
- Coverage gap fixed: identity-domain **97%**, identity-infrastructure **88%**, portfolio-web **97%** — all ≥ 80% gate
- Tests added:
  - `identity-domain`: `UserIdTest`, `HouseholdIdTest`, `HouseholdTest`, `DomainExceptionsTest`
  - `identity-infrastructure/security`: `JwtServiceTest` (generateAccessToken, parseToken, isTokenValid,
    extractUserId), `JwtAuthenticationFilterTest` (no header, non-bearer, valid token, invalid token),
    `JwtServicePemLoadingTest` (production constructor + filesystem PEM loading via `@TempDir`)
  - `identity-infrastructure/persistence`: `UserRepositoryAdapterTest` + `RefreshTokenServiceTest`
    (`@DataJpaTest` + Testcontainers)
  - `identity-infrastructure/usecase`: `AuthServiceTest` extended — refresh happy path, refresh invalid token, logout
  - `portfolio-web`: `AuthControllerTest` extended — `GET /auth/me` (200)
- `IdentityInfrastructureTestApplication`: minimal `@SpringBootApplication` for `@DataJpaTest` entity scanning in
  `identity-infrastructure` (same pattern as `InfrastructureTestApplication` in portfolio-infrastructure)
- Identity SQL migrations copied to `identity-infrastructure/src/test/resources` — needed because
  `portfolio-infrastructure` is not on the identity module's test classpath
- All 9 modules green, 0 test failures, coverage ≥ 80% on all new code

## 2026-05-30 — Phase 5 Session 3: User data isolation

- V15 migration: `DELETE FROM portfolio.financial_account WHERE user_id IS NULL` + `ALTER COLUMN user_id SET NOT NULL`
- `FinancialAccount.open()` requires `UserId ownerId` (5th parameter); `reconstruct()` updated to match
- `FinancialAccountRepository.findAllByOwnerId(UserId)` — primary query method; `findAll()` retained for tests only
- All use cases scoped by `UserId`: `getAllAccounts(UserId)`, `getAccountById(id, UserId)`, `getHoldings(id, UserId)`,
  `importCsv(id, parsed, UserId)`
- `getAccountById`: ownership mismatch returns `AccountNotFoundException` — no existence reveal to other users
- All controller endpoints extract `UserId` from `Authentication.getPrincipal()`
- `FinancialAccountRepositoryAdapterTest`: inserts a real `identity.users` row via native SQL in `@BeforeEach` to satisfy
  the `financial_account.user_id` FK (UserJpaEntity not on portfolio-infrastructure classpath)
- `CrossLayerArchitectureTest`: `reconstruct()` ArchUnit rule updated for new `UserId` parameter
- New tests: `open_preserves_ownerId`, `open_with_null_ownerId_throws`, `getAccountById_throwsAccountNotFoundWhenOwnerMismatch`,
  `findAllByOwnerId_returns_only_accounts_for_that_user`; controller tests use `.with(authentication(mockAuth()))`
- 169 tests (was 130), 0 failures, all 9 modules green
- **Phase 5 complete** — app is now multi-user with full data isolation

## 2026-05-30 — Phase 5.5a: French regulatory deposit limits

- `AccountSubType` enum (14 values): `LIVRET_A`, `LDDS`, `LEP`, `LIVRET_JEUNE`, `CEL`, `PEL`, `PEA`, `PEA_PME`,
  `COMPTE_TITRE_ORDINAIRE`, `ASSURANCE_VIE`, `PER`, `CRYPTO_WALLET`, `CHECKING`, `SAVINGS` — each carrying its legal
  reference (Code Monétaire et Financier article or decree)
- `DepositLimits` constants class: Livret A 22 950 €, LDDS 12 000 €, LEP 10 000 €, PEA 150 000 €,
  PEA + PEA-PME combined ceiling 225 000 €
- `DepositLimitExceededException` in `portfolio-domain/exception` — carries `limit`, `currentTotal`, `attempted`,
  `remaining`; used by the domain service and mapped at the REST layer
- `AccountBusinessRules` pure domain service (zero framework deps): `validateDeposit`, `remainingCapacity`,
  `isApproachingLimit`; combined PEA + PEA-PME rule checks all user accounts
- Flyway V16: `sub_type VARCHAR(50)` nullable column on `portfolio.financial_account`
- `FinancialAccountResponse` extended: `subType`, `depositLimit`, `totalDeposits`, `remainingCapacity` fields
- `GlobalExceptionHandler`: `DepositLimitExceededException` → 422 with `DepositLimitErrorResponse` body (limit,
  currentTotal, attempted, remaining)
- All 9 modules green, BUILD SUCCESS

## 2026-05-31 — Phase 5.5b: Email verification + password reset

- Flyway V17: `email_verified BOOLEAN NOT NULL DEFAULT FALSE` on `identity.users` + `email_verification_tokens` table
  (token_hash VARCHAR(64), expires_at, used_at); existing users back-filled to verified
- Flyway V18: `password_reset_tokens` table — same structure as email verification tokens
- `User` domain: `emailVerified` field added; `register()` sets it `false`; `reconstruct()` gains 6th parameter;
  `withEmailVerified()` and `withNewPassword()` wither methods added (immutable domain object)
- `EmailNotVerifiedException` → 403, `InvalidTokenException` → 400 — both in `identity-domain/exception`
- `EmailService` in `identity-infrastructure/email`: Resend SDK (`resend-java 3.1.0`); fire-and-forget (`sendEmail`
  catches all exceptions, logs, never re-throws); HTML templates for verification and password reset emails
- `EmailVerificationService` + `PasswordResetService` in `identity-infrastructure/persistence`: SHA-256 token hashing
  (64-char hex), 24h / 1h TTL, single-use enforcement via `used_at`; old tokens deleted on new request;
  same `@DataJpaTest` + Testcontainers pattern as `RefreshTokenService`
- `AuthService`: `login()` throws `EmailNotVerifiedException` when `emailVerified = false`; `register()` creates
  verification token and sends email; 4 new methods: `verifyEmail`, `resendVerificationEmail`,
  `requestPasswordReset` (always succeeds — security), `resetPassword`
- 4 new `AuthController` endpoints: `POST /auth/verify-email`, `/resend-verification`, `/forgot-password`,
  `/reset-password`; 4 new request DTOs in `portfolio-web/auth`
- `TestJwtConfig` (bootstrap tests): added `@Bean @Primary EmailService emailService()` returning a Mockito mock —
  prevents real Resend API calls in CI; `local` profile provides a placeholder API key so bean construction succeeds
- `UserJpaEntity`: `email_verified` column + `setEmailVerified(boolean)` + `setPasswordHash(String)` setters
- 213 tests, 0 failures, all 9 modules green, Spotless clean

## 2026-06-03 — Bug fixes from functional testing session

- CRITICAL: SELL quantity validated eagerly in `recordTransaction()` before persisting — `InvalidHoldingException`
  thrown with professional message format (`Cannot sell {qty} {ticker} — you only hold {held}`)
- `InvalidHoldingException` → 422 Unprocessable Entity (was missing handler); `InsufficientFundsException` →
  professional message format (`Insufficient funds — available: {symbol} {amount}, required: {symbol} {amount}`)
- Fees validation annotation message updated: `"Brokerage fees cannot be negative"` (was generic)
- `@ValidTransactionDate` custom constraint: rejects dates before `1900-01-01` and after tomorrow (+1 day timezone
  tolerance); `TransactionDateValidator` + `ValidTransactionDate` annotation in `portfolio-web/validation`
- `logout()` uses `instanceof UserId` pattern match — handles `null` principal and anonymous authentication tokens
  without `ClassCastException`
- `POST /auth/validate-reset-token` endpoint added + `PasswordResetService.validateToken()` — allows frontend to
  check token validity before showing the reset-password form
- Cash/Savings accounts: transactions accessible for all account types via `GET /api/v1/accounts/{id}/transactions`
  — no account-type restriction in controller or domain
- BUILD SUCCESS, 9/9 modules green

## 2026-06-03 — Session A: savings deposit rules fix + openedAt field

- Flyway V19: `opened_at DATE` column added to `portfolio.financial_account`; backfilled from `created_at`,
  then `NOT NULL` enforced. Used `DATE` (not `TIMESTAMPTZ`) so Hibernate `LocalDate` mapping passes `ddl-auto: validate`.
- `FinancialAccount`: `openedAt LocalDate` field added; required in both `open()` (new 7th parameter) and
  `reconstruct()` (new 9th parameter); `Objects.requireNonNull` guard on both.
- `AccountBusinessRules`: savings accounts (Livret A, LDDS, LDD, LEP, Livret Jeune) now use **current balance**
  for deposit limit calculation — withdrawals correctly free up deposit capacity up to the legal cap; interest
  credited as DIVIDEND can push balance above cap and correctly blocks further deposits. PEA / PEA-PME still use
  cumulative deposits (no change).
- All layers updated end-to-end: `CreateFinancialAccountCommand`, `CreateAccountRequest` (nullable `openedAt`,
  defaults to `LocalDate.now()` in controller), `FinancialAccountResponse` (new `openedAt` field),
  `FinancialAccountJpaEntity`, `FinancialAccountMapper`, `FinancialAccountController`, `FinancialAccountService`.
- `CrossLayerArchitectureTest` ArchUnit rule updated for new `reconstruct()` signature (`LocalDate` parameter added).
- 7 new tests: `validateDeposit_livretA_withdrawal_frees_up_capacity`,
  `validateDeposit_livretA_uses_current_balance_not_cumulative_deposits`,
  `validateDeposit_livretA_interest_above_cap_blocks_further_deposits`,
  `validateDeposit_pea_uses_cumulative_deposits_not_balance`,
  `remainingCapacity_livretA_reflects_withdrawal`, `open_preserves_openedAt`, `open_with_null_openedAt_throws`.
- 256 tests, 0 failures (was 213), 9/9 modules green, Spotless clean.

## 2026-06-04 — INTEREST type, transaction edit, PEA summary, CSV fixes

- `TransactionType.INTEREST`: new enum value for interest credited by bank on savings accounts (Livret A,
  LDDS, LEP, Livret Jeune). Behaves like DIVIDEND for balance calculation. Does NOT count toward deposit
  limit calculations (limits are based on cumulative DEPOSIT transactions only).
- `updateTransaction(TransactionId, UpdatedTransactionValues)` on `FinancialAccount`: replaces the
  transaction in-place, replays full chronology in date+type-priority order, throws
  `InsufficientFundsException` if any intermediate balance goes negative. Type and ticker are immutable.
- `TransactionNotFoundException` in `portfolio-domain/exception` — maps to 404 in `GlobalExceptionHandler`.
- `UpdatedTransactionValues` record in `portfolio-domain`: immutable value object carrying editable fields
  (quantity, pricePerUnit, totalAmount, date, fees, description).
- `PUT /api/v1/accounts/{accountId}/transactions/{transactionId}`: edit transaction endpoint — 204 on
  success, 404 if transaction not found, ownership-checked via JWT principal.
- `GET /api/v1/accounts/summary/pea`: returns combined PEA + PEA-PME deposit summary (`PeaSummaryResponse`
  DTO): hasPea, hasPeaPme, peaDeposits, peaPmeDeposits, combinedDeposits, limits, remaining capacities,
  account IDs.
- CSV parsers: both `BoursobankOperationParser` and `BoursobankPositionParser` now throw
  `CsvParsingException("No valid transactions found in file…")` when the parsed result is empty with no
  errors — prevents silent no-ops on wrong/empty files.
- `BoursobankPositionParser`: prepends a synthetic `DEPOSIT` transaction equal to the sum of all parsed
  position totals, dated to the first position's date, with description "Initial import — positions
  snapshot". This ensures the account has sufficient balance before BUY positions are applied.
- `FinancialAccountService.TYPE_PRIORITY` updated: INTEREST gets priority 3 (same as DIVIDEND).
- ~270 tests, 0 failures, 9/9 modules green, Spotless clean.

## 2026-06-05 — portfolioValue added to FinancialAccountResponse

- `Holding.computeFrom(List<Transaction>)` overload added: filters BUY/SELL transactions, groups by ticker
  symbol, delegates to the existing per-ticker overload, returns `List<Holding>`.
- `INVESTMENT_TYPES` constant in `FinancialAccountController`: `Set.of(PEA, PEA_PME, COMPTE_TITRES, PER,
  ASSURANCE_VIE, CRYPTO_WALLET)`.
- `computePortfolioValue(FinancialAccount)` private method: returns `null` for non-investment accounts;
  computes Σ (averageCostPrice × quantity) for investment accounts with BUY history.
- `FinancialAccountResponse`: `BigDecimal portfolioValue` field added (13th field; `null` for
  SAVINGS_ACCOUNT, CASH_ACCOUNT, REAL_ESTATE).
- 2 new controller tests: `toAccountResponse_returns_portfolioValue_for_pea_account`,
  `toAccountResponse_returns_null_portfolioValue_for_savings_account`.
- BUILD SUCCESS, 9/9 modules green.

## 2026-06-06 — fix: deposit limit enforced on transaction edit

- `AccountBusinessRules.validateDepositAfterEdit()`: checks post-edit deposit state — ensures editing a DEPOSIT
  transaction cannot silently push cumulative deposits past the regulatory cap.
- `FinancialAccountService.updateTransaction()`: calls `validateDepositAfterEdit` when the transaction type is DEPOSIT,
  after the in-place replacement and before persisting.
- `TransactionAmountValidator`: custom constraint enforcing `totalAmount > 0` for INTEREST, DIVIDEND, DEPOSIT, and
  WITHDRAWAL transaction types.
- `getTransactionType()` use case added to `FinancialAccountUseCase` + `FinancialAccountService` — allows the web layer
  to resolve the type of an existing transaction without loading the full account.
- BUY/SELL edit: `totalAmount` computed server-side in controller as `qty × pricePerUnit ± fees`, so the client does
  not need to pass a pre-computed total.
- 301 tests, 0 failures, 9/9 modules green.

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
