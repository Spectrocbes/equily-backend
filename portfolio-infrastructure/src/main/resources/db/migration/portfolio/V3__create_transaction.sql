-- Transaction is the append-only event log of a FinancialAccount.
-- It is never updated or deleted — only inserted.
CREATE TABLE portfolio.transaction (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id       UUID        NOT NULL REFERENCES portfolio.financial_account(id),
    type             VARCHAR(20) NOT NULL,
    ticker           VARCHAR(20),
    quantity         NUMERIC(19,8),
    price_per_unit   NUMERIC(19,2),
    price_currency   VARCHAR(3),
    total_amount     NUMERIC(19,2) NOT NULL,
    total_currency   VARCHAR(3)   NOT NULL,
    date             DATE         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Index for fetching all transactions of an account (used in Holding computation)
CREATE INDEX idx_transaction_account_id ON portfolio.transaction(account_id);
