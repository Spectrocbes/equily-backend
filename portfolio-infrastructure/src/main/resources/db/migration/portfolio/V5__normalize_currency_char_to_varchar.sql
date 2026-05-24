-- CHAR(3) in V2 maps to bpchar in PostgreSQL (JDBC Types#CHAR), but Hibernate maps
-- Java String to varchar (JDBC Types#VARCHAR). Change the column type to avoid
-- schema-validation failures with ddl-auto: validate.
ALTER TABLE portfolio.financial_account ALTER COLUMN currency TYPE VARCHAR(3);
