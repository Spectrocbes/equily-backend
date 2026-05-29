package com.equily.portfolio.infrastructure.csv;

import com.equily.portfolio.application.exception.CsvParsingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;

/** Shared utilities for Boursobank CSV parsers. */
abstract class AbstractBoursobankParser {

  protected static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private static final CSVFormat CSV_FORMAT =
      CSVFormat.DEFAULT
          .builder()
          .setDelimiter(';')
          .setHeader()
          .setSkipHeaderRecord(true)
          .setIgnoreEmptyLines(true)
          .setTrim(true)
          .build();

  protected CSVParser buildParser(InputStream inputStream) throws IOException {
    Reader reader =
        new InputStreamReader(
            BOMInputStream.builder().setInputStream(inputStream).get(), StandardCharsets.UTF_8);
    return CSV_FORMAT.parse(reader);
  }

  protected BigDecimal parseAmount(String value) {
    if (value == null || value.isBlank()) return BigDecimal.ZERO;
    String cleaned =
        value.replace("€", "").replace(" ", "").replace(" ", "").replace(",", ".").trim();
    if (cleaned.isEmpty()) return BigDecimal.ZERO;
    try {
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      throw new CsvParsingException("Cannot parse amount: '" + value + "'");
    }
  }

  protected boolean isEmptyRow(CSVRecord record) {
    for (String value : record) {
      if (value != null && !value.isBlank()) return false;
    }
    return true;
  }
}
