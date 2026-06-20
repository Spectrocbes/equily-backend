ALTER TABLE portfolio.transaction
    ADD COLUMN transfer_id         UUID,
    ADD COLUMN linked_account_id   UUID
        REFERENCES portfolio.financial_account(id)
        ON DELETE SET NULL,
    ADD COLUMN external_address    VARCHAR(255),
    ADD COLUMN transfer_direction  VARCHAR(10);
