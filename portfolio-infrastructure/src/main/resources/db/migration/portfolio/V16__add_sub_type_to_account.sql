-- Sub-type for regulatory rule enforcement
-- Nullable: existing accounts have no sub-type set
ALTER TABLE portfolio.financial_account
    ADD COLUMN sub_type VARCHAR(50);
