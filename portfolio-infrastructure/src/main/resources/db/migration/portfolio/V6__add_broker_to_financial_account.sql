-- Broker/platform associated with the account (e.g. Fortuneo, BoursoBank, Binance)
ALTER TABLE portfolio.financial_account
  ADD COLUMN broker VARCHAR(100) NOT NULL;
