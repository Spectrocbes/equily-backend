package com.equily.portfolio.infrastructure.csv;

import com.equily.portfolio.application.exception.CsvParsingException;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.csv.CsvImportResult;
import com.equily.shared.Money;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Parses Boursobank "export-positions-comptables-*.csv" files.
 *
 * <p>File format: name;isin;quantity;buyingPrice;lastPrice;...;lastMovementDate;compensation
 *
 * <p>Each row becomes a synthetic BUY transaction representing the current holding.
 */
class BoursobankPositionParser extends AbstractBoursobankParser {

  CsvImportResult parse(InputStream inputStream) {
    List<Transaction> transactions = new ArrayList<>();
    List<String> errorDetails = new ArrayList<>();
    int skipped = 0;

    try (CSVParser parser = buildParser(inputStream)) {
      for (CSVRecord record : parser) {
        if (isEmptyRow(record)) {
          continue;
        }
        try {
          String isin = record.get("isin").trim();
          if (isin.isBlank()) {
            continue;
          }

          BigDecimal quantity = parseAmount(record.get("quantity"));
          BigDecimal buyingPrice = parseAmount(record.get("buyingPrice"));
          BigDecimal totalAmount =
              quantity.multiply(buyingPrice).setScale(2, RoundingMode.HALF_EVEN);

          LocalDate date = LocalDate.parse(record.get("lastMovementDate").trim(), DATE_FORMAT);
          Currency eur = Currency.getInstance("EUR");

          Transaction tx =
              Transaction.ofEur(
                  TransactionId.generate(),
                  TransactionType.BUY,
                  new Ticker(isin),
                  quantity,
                  new Money(buyingPrice, eur),
                  new Money(totalAmount, eur),
                  date,
                  BigDecimal.ZERO,
                  "Imported from Boursobank positions");
          transactions.add(tx);

        } catch (Exception e) {
          errorDetails.add("Row " + record.getRecordNumber() + ": " + e.getMessage());
        }
      }
    } catch (IOException e) {
      throw new CsvParsingException("Failed to read positions CSV", e);
    }

    if (transactions.isEmpty() && errorDetails.isEmpty()) {
      throw new CsvParsingException(
          "No valid transactions found in file. The file may be empty or contain only headers.");
    }

    if (!transactions.isEmpty()) {
      BigDecimal totalAmount =
          transactions.stream()
              .map(t -> t.totalAmount().amount())
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      Transaction initialDeposit =
          Transaction.ofEur(
              TransactionId.generate(),
              TransactionType.DEPOSIT,
              null,
              null,
              null,
              new Money(totalAmount, Currency.getInstance("EUR")),
              transactions.get(0).date(),
              BigDecimal.ZERO,
              "Initial import — positions snapshot");

      transactions.add(0, initialDeposit);
    }

    return new CsvImportResult(
        transactions.size(), skipped, errorDetails.size(), errorDetails, transactions);
  }
}
