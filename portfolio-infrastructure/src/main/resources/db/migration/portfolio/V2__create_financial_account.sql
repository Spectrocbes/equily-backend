-- FinancialAccount is the Aggregate Root of the Portfolio context.
-- account_type: PEA, CRYPTO_WALLET, CASH_ACCOUNT, REAL_ESTATE
CREATE TABLE portfolio.financial_account (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    currency    CHAR(3)      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);