-- Add currency fields to transaction table
ALTER TABLE portfolio.transaction
    ADD COLUMN currency      VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    ADD COLUMN amount_eur    NUMERIC(19,4)  NOT NULL DEFAULT 0,
    ADD COLUMN eur_fx_rate   NUMERIC(10,6)  NOT NULL DEFAULT 1.000000;

-- Backfill existing transactions:
-- All existing transactions were entered in EUR, so amount_eur = total_amount
UPDATE portfolio.transaction
    SET amount_eur  = total_amount,
        eur_fx_rate = 1.000000,
        currency    = 'EUR'
    WHERE currency = 'EUR';

-- Remove default constraint after backfill
ALTER TABLE portfolio.transaction
    ALTER COLUMN amount_eur  DROP DEFAULT,
    ALTER COLUMN eur_fx_rate DROP DEFAULT;
