-- Nullable first; will be enforced NOT NULL after auth is wired end-to-end.
ALTER TABLE portfolio.financial_account
    ADD COLUMN user_id UUID REFERENCES identity.users(id);
