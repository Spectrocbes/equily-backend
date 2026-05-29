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
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;

/**
 * Parses Boursobank "export-positions-comptables-*.csv" files.
 *
 * <p>File format: name;isin;quantity;buyingPrice;lastPrice;...;lastMovementDate;compensation
 *
 * <p>Each row becomes a synthetic BUY transaction representing the current holding.
 */
class BoursobankPositionParser {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private static final CSVFormat CSV_FORMAT =
      CSVFormat.DEFAULT
          .builder()
          .setDelimiter(';')
          .setHeader()
          .setSkipHeaderRecord(true)
          .setIgnoreEmptyLines(true)
          .setTrim(true)
          .build();

  CsvImportResult parse(InputStream inputStream) {
    List<Transaction> transactions = new ArrayList<>();
    List<String> errorDetails = new ArrayList<>();
    int skipped = 0;

    try (Reader reader =
            new InputStreamReader(
                BOMInputStream.builder().setInputStream(inputStream).get(),
                StandardCharsets.UTF_8);
        CSVParser parser = CSV_FORMAT.parse(reader)) {

      for (CSVRecord record : parser) {
        if (isEmptyRow(record)) {
          skipped++;
          continue;
        }
        try {
          String isin = record.get("isin").trim();
          if (isin.isBlank()) {
            skipped++;
            continue;
          }

          BigDecimal quantity = parseAmount(record.get("quantity"));
          BigDecimal buyingPrice = parseAmount(record.get("buyingPrice"));
          BigDecimal totalAmount =
              quantity.multiply(buyingPrice).setScale(2, RoundingMode.HALF_EVEN);

          LocalDate date = LocalDate.parse(record.get("lastMovementDate").trim(), DATE_FORMAT);
          Currency eur = Currency.getInstance("EUR");

          Transaction tx =
              Transaction.of(
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

    return new CsvImportResult(
        transactions.size(), skipped, errorDetails.size(), errorDetails, transactions);
  }

  private BigDecimal parseAmount(String value) {
    if (value == null || value.isBlank()) return BigDecimal.ZERO;
    String cleaned =
        value.replace("€", "").replace(" ", "").replace(" ", "").replace(",", ".").trim();
    if (cleaned.isEmpty()) return BigDecimal.ZERO;
    try {
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      throw new CsvParsingException("Cannot parse amount: '" + value + "'");
    }
  }

  private boolean isEmptyRow(CSVRecord record) {
    for (String value : record) {
      if (value != null && !value.isBlank()) return false;
    }
    return true;
  }
}
