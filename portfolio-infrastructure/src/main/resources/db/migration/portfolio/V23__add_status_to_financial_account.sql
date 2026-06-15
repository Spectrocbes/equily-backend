ALTER TABLE portfolio.financial_account
    ADD COLUMN status    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN closed_at DATE;

UPDATE portfolio.financial_account SET status = 'ACTIVE';
