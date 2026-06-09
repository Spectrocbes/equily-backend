-- Remove legacy currency columns replaced by the new multi-currency system
-- currency: native transaction currency (EUR/USD/GBP/CHF)
-- amount_eur: EUR equivalent at time of recording
-- eur_fx_rate: historical FX rate used
ALTER TABLE portfolio.transaction
    DROP COLUMN IF EXISTS price_currency,
    DROP COLUMN IF EXISTS total_currency;
