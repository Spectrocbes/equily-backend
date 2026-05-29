package com.equily.portfolio.domain.csv;

import com.equily.portfolio.domain.Transaction;
import java.util.List;

public record CsvImportResult(
    int imported,
    int skipped,
    int errors,
    List<String> errorDetails,
    List<Transaction> transactions) {

  public static CsvImportResult empty() {
    return new CsvImportResult(0, 0, 0, List.of(), List.of());
  }

  public boolean hasErrors() {
    return errors > 0;
  }
}
