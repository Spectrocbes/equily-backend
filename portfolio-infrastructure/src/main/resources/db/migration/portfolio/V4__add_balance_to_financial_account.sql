-- Balance is the running cash balance of a FinancialAccount after all transactions.
-- DEFAULT 0 is a safe sentinel; the application always writes the correct value.
ALTER TABLE portfolio.financial_account
  ADD COLUMN balance NUMERIC(19,2) NOT NULL DEFAULT 0;
