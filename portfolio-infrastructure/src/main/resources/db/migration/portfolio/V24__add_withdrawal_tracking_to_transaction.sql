-- Store liquidation value and gross withdrawal amount at time of PEA >=5y withdrawal
-- Required for accurate Loi Pacte versements counter replay on subsequent calculations
ALTER TABLE portfolio.transaction
    ADD COLUMN liquidation_value_at_withdrawal NUMERIC(19,4),
    ADD COLUMN gross_withdrawal_amount         NUMERIC(19,4);
-- NULL for all non-PEA-over-5y-withdrawal transactions
