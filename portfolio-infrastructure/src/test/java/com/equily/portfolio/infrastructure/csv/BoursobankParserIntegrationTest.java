package com.equily.portfolio.infrastructure.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.equily.portfolio.domain.csv.CsvImportResult;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class BoursobankParserIntegrationTest {

  private final BoursobankOperationParser operationParser = new BoursobankOperationParser();
  private final BoursobankPositionParser positionParser = new BoursobankPositionParser();

  @Test
  void parses_real_operations_file() {
    InputStream is = getClass().getResourceAsStream("/csv/boursobank-operations-sample.csv");
    assertThat(is).isNotNull();
    CsvImportResult result = operationParser.parse(is);
    assertThat(result.errors()).isZero();
    assertThat(result.imported() + result.skipped()).isGreaterThan(0);
  }

  @Test
  void parses_real_positions_file() {
    InputStream is = getClass().getResourceAsStream("/csv/boursobank-positions-sample.csv");
    assertThat(is).isNotNull();
    CsvImportResult result = positionParser.parse(is);
    assertThat(result.errors()).isZero();
    assertThat(result.imported()).isGreaterThan(0);
  }
}
