# Project Progress

## 2026-05-22 — Multi-module restructure + Money value object

- Multi-module Maven restructure completed (6 modules: shared-kernel, portfolio-{domain,application,infrastructure,web}, bootstrap)
- Lombok dropped project-wide; Java 21 records used instead
- ArchUnit rules in place for shared-kernel and portfolio-domain (no Spring/JPA/Lombok in domain packages)
- Money value object implemented in shared-kernel (record, stripTrailingZeros normalization, InvalidMoneyException, CurrencyMismatchException, HALF_EVEN rounding)
- EquilyBackendApplicationTests disabled pending Docker + datasource config
- Next: Docker Compose for local PostgreSQL + application.yml config
