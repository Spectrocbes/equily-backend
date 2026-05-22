# Project Progress

## 2026-05-22 — Multi-module restructure + Money value object

- Docker Compose configured for local PostgreSQL 16-alpine
- Spring profiles: base (JPA + Flyway config), local (Docker datasource), prod (Supabase via env vars)
- Flyway migrations: V1 (create schema portfolio), V2 (create financial_account table)
- EquilyBackendApplicationTests re-enabled with @ActiveProfiles("local") + Testcontainers
- Full reactor BUILD SUCCESS confirmed (7 modules, 21s)
- Next: implement FinancialAccount aggregate root in portfolio-domain
