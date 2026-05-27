-- Brokerage fees for BUY/SELL transactions
-- Stored separately for fee transparency analytics
-- Default 0: DEPOSIT/WITHDRAWAL/DIVIDEND have no fees
ALTER TABLE portfolio.transaction
  ADD COLUMN fees NUMERIC(19,2) NOT NULL DEFAULT 0;
