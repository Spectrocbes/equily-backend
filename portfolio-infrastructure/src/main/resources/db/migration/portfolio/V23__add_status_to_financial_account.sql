ALTER TABLE portfolio.financial_account
    ADD COLUMN status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN closed_at  DATE;

-- Backfill all existing accounts as ACTIVE
-- NOSONAR: intentional full-table update — backfill migration
UPDATE portfolio.financial_account
    SET status = 'ACTIVE'
    WHERE status IS NOT NULL;

ALTER TABLE portfolio.financial_account
    ALTER COLUMN status DROP DEFAULT;
