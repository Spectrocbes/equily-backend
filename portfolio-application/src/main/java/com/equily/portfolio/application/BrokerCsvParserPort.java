package com.equily.portfolio.application;

import com.equily.portfolio.domain.csv.CsvImportResult;
import java.io.InputStream;

public interface BrokerCsvParserPort {
  CsvImportResult parse(InputStream inputStream, String broker, String mode);
}
