-- Set user_id NOT NULL now that auth is in place
-- Existing accounts without user_id will need to be deleted first
-- (dev environment only — prod would require a data migration)
DELETE FROM portfolio.financial_account WHERE user_id IS NULL;
ALTER TABLE portfolio.financial_account ALTER COLUMN user_id SET NOT NULL;
