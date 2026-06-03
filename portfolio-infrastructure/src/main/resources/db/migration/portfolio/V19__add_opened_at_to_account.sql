-- Date the account was opened (used for PEA 5-year rule)
-- Defaults to created_at for existing accounts
ALTER TABLE portfolio.financial_account
    ADD COLUMN opened_at DATE;

-- Backfill existing accounts with their creation date
UPDATE portfolio.financial_account
    SET opened_at = created_at
    WHERE opened_at IS NULL;

-- Enforce NOT NULL after backfill
ALTER TABLE portfolio.financial_account
    ALTER COLUMN opened_at SET NOT NULL;
