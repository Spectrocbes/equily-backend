ALTER TABLE portfolio.financial_account
    ADD COLUMN linked_checking_account_id UUID
        REFERENCES portfolio.financial_account(id)
        ON DELETE SET NULL;
