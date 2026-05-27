-- Optional free-text description for a transaction
-- Examples: "DCA janvier", "Renforcement mensuel", "Arbitrage ETF World"
ALTER TABLE portfolio.transaction ADD COLUMN description VARCHAR(255);
