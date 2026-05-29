package com.equily.portfolio.infrastructure.csv;

import com.equily.portfolio.application.BrokerCsvParserPort;
import com.equily.portfolio.domain.csv.CsvImportResult;
import java.io.InputStream;
import org.springframework.stereotype.Component;

@Component
public class BrokerCsvParserAdapter implements BrokerCsvParserPort {

  private final BoursobankOperationParser operationParser = new BoursobankOperationParser();
  private final BoursobankPositionParser positionParser = new BoursobankPositionParser();

  @Override
  public CsvImportResult parse(InputStream inputStream, String broker, String mode) {
    return switch (broker.toUpperCase() + "_" + mode.toUpperCase()) {
      case "BOURSOBANK_OPERATIONS" -> operationParser.parse(inputStream);
      case "BOURSOBANK_POSITIONS" -> positionParser.parse(inputStream);
      default ->
          throw new IllegalArgumentException("Unsupported broker/mode: " + broker + "/" + mode);
    };
  }
}
