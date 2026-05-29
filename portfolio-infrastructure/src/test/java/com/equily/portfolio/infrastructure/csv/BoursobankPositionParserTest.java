package com.equily.portfolio.infrastructure.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.csv.CsvImportResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BoursobankPositionParserTest {

  private final BoursobankPositionParser parser = new BoursobankPositionParser();

  private InputStream csv(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void parses_position_as_synthetic_buy() {
    String content =
        """
name;isin;quantity;buyingPrice;lastPrice;intradayVariation;amount;amountVariation;variation;lastMovementDate;compensation
AMUNDI NASDAQ-100 DAILY;FR0010342592;1;1 100,00;2 042,00;0,56;2 042,00;932;36,7;29/01/2026;
""";
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.transactions().get(0).type()).isEqualTo(TransactionType.BUY);
    assertThat(result.transactions().get(0).ticker().symbol()).isEqualTo("FR0010342592");
    assertThat(result.transactions().get(0).quantity()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(result.transactions().get(0).pricePerUnit().amount())
        .isEqualByComparingTo(new BigDecimal("1100.00"));
    assertThat(result.transactions().get(0).totalAmount().amount())
        .isEqualByComparingTo(new BigDecimal("1100.00"));
    assertThat(result.transactions().get(0).date()).isEqualTo(LocalDate.of(2026, 1, 29));
  }

  @Test
  void skips_rows_with_blank_isin() {
    String content =
        """
name;isin;quantity;buyingPrice;lastPrice;intradayVariation;amount;amountVariation;variation;lastMovementDate;compensation
CASH;;0;0;0;0;500;0;0;29/01/2026;
""";
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.imported()).isZero();
    assertThat(result.skipped()).isEqualTo(1);
  }

  @Test
  void skips_empty_rows() {
    String content =
        """
name;isin;quantity;buyingPrice;lastPrice;intradayVariation;amount;amountVariation;variation;lastMovementDate;compensation
;;;;;;;;;;
""";
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.imported()).isZero();
  }

  @Test
  void parses_multiple_positions() {
    String content =
        """
name;isin;quantity;buyingPrice;lastPrice;intradayVariation;amount;amountVariation;variation;lastMovementDate;compensation
AMUNDI NASDAQ-100;FR0010342592;1;1100,00;2042,00;0,56;2042,00;932;36,7;29/01/2026;
SOME ETF;IE00B4L5Y983;2;50,00;55,00;0,10;110,00;10;10,0;15/03/2026;
""";
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.imported()).isEqualTo(2);
    assertThat(result.errors()).isZero();
  }

  @Test
  void description_is_set_to_imported_from_boursobank_positions() {
    String content =
        """
name;isin;quantity;buyingPrice;lastPrice;intradayVariation;amount;amountVariation;variation;lastMovementDate;compensation
AMUNDI NASDAQ-100;FR0010342592;1;1100,00;2042,00;0,56;2042,00;932;36,7;29/01/2026;
""";
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.transactions().get(0).description())
        .isEqualTo("Imported from Boursobank positions");
  }
}
