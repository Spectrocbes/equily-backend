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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses Boursobank "export-operations-*.csv" files.
 *
 * <p>File format (semicolon-separated, UTF-8 BOM, French locale): Date opération;Date
 * valeur;Opération;Valeur;Code ISIN;Montant;Quantité;Cours
 *
 * <p>Operation type mapping:
 *
 * <ul>
 *   <li>"VIR*" / "VIREMENT" → DEPOSIT
 *   <li>"ACHAT*" → BUY
 *   <li>"VENTE*" → SELL
 *   <li>"DIVIDENDE*" → DIVIDEND
 *   <li>"REMBOURSEMENT*" → WITHDRAWAL
 * </ul>
 *
 * <p>Ticker strategy (Phase 1): use ISIN as ticker symbol when available.
 */
class BoursobankOperationParser extends AbstractBoursobankParser {

  private static final Logger log = LoggerFactory.getLogger(BoursobankOperationParser.class);

  CsvImportResult parse(InputStream inputStream) {
    List<Transaction> transactions = new ArrayList<>();
    List<String> errorDetails = new ArrayList<>();
    int skipped = 0;

    try (CSVParser parser = buildParser(inputStream)) {
      for (CSVRecord record : parser) {
        if (isEmptyRow(record)) {
          skipped++;
          continue;
        }
        try {
          Transaction tx = parseRecord(record);
          if (tx != null) {
            transactions.add(tx);
          } else {
            skipped++;
          }
        } catch (Exception e) {
          errorDetails.add("Row " + record.getRecordNumber() + ": " + e.getMessage());
          log.warn("Failed to parse row {}: {}", record.getRecordNumber(), e.getMessage());
        }
      }
    } catch (IOException e) {
      throw new CsvParsingException("Failed to read CSV file", e);
    }

    if (transactions.isEmpty() && errorDetails.isEmpty()) {
      throw new CsvParsingException(
          "No valid transactions found in file. The file may be empty or contain only headers.");
    }

    return new CsvImportResult(
        transactions.size(), skipped, errorDetails.size(), errorDetails, transactions);
  }

  private Transaction parseRecord(CSVRecord record) {
    String operationType = record.get("Opération").trim();
    TransactionType type = mapOperationType(operationType);
    if (type == null) {
      log.debug("Skipping unknown operation type: {}", operationType);
      return null;
    }

    LocalDate date = LocalDate.parse(record.get("Date opération").trim(), DATE_FORMAT);

    BigDecimal rawAmount = parseAmount(record.get("Montant"));
    BigDecimal totalAmount = rawAmount.abs();
    Currency eur = Currency.getInstance("EUR");
    Money totalMoney = new Money(totalAmount, eur);

    String isin = record.get("Code ISIN").trim();
    Ticker ticker = isin.isBlank() ? null : new Ticker(isin);

    BigDecimal quantity = null;
    Money pricePerUnit = null;
    BigDecimal fees = BigDecimal.ZERO;

    if (type == TransactionType.BUY || type == TransactionType.SELL) {
      String qtyStr = record.get("Quantité").trim();
      if (!qtyStr.isBlank()) {
        quantity = parseAmount(qtyStr);
      }
      String coursStr = record.get("Cours").trim();
      if (!coursStr.isBlank()) {
        BigDecimal cours = parseAmount(coursStr);
        if (cours.compareTo(BigDecimal.ZERO) > 0) {
          pricePerUnit = new Money(cours, eur);
        }
      }

      // Boursobank has no fees column — fees are implicit in the total amount
      if (quantity != null && pricePerUnit != null) {
        BigDecimal expectedAmount =
            quantity.multiply(pricePerUnit.amount()).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal implicitFees =
            totalAmount.subtract(expectedAmount).setScale(2, RoundingMode.HALF_EVEN);
        fees = implicitFees.compareTo(BigDecimal.ZERO) > 0 ? implicitFees : BigDecimal.ZERO;
      }
    }

    return Transaction.of(
        TransactionId.generate(),
        type,
        ticker,
        quantity,
        pricePerUnit,
        totalMoney,
        date,
        fees,
        "Imported from Boursobank");
  }

  private TransactionType mapOperationType(String operation) {
    String upper = operation.toUpperCase();
    if (upper.contains("VIR") || upper.contains("VIREMENT")) return TransactionType.DEPOSIT;
    if (upper.contains("ACHAT")) return TransactionType.BUY;
    if (upper.contains("VENTE")) return TransactionType.SELL;
    if (upper.contains("DIVIDENDE")) return TransactionType.DIVIDEND;
    if (upper.contains("REMBOURSEMENT")) return TransactionType.WITHDRAWAL;
    return null;
  }
}
