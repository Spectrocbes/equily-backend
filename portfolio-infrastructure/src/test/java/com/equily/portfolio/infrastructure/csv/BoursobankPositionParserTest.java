package com.equily.portfolio.infrastructure.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.equily.portfolio.application.exception.CsvParsingException;
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
    // 1 position + 1 auto-DEPOSIT prepended
    assertThat(result.imported()).isEqualTo(2);
    assertThat(result.transactions().get(0).type()).isEqualTo(TransactionType.DEPOSIT);
    assertThat(result.transactions().get(1).type()).isEqualTo(TransactionType.BUY);
    assertThat(result.transactions().get(1).ticker().symbol()).isEqualTo("FR0010342592");
    assertThat(result.transactions().get(1).quantity()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(result.transactions().get(1).pricePerUnit().amount())
        .isEqualByComparingTo(new BigDecimal("1100.00"));
    assertThat(result.transactions().get(1).totalAmount().amount())
        .isEqualByComparingTo(new BigDecimal("1100.00"));
    assertThat(result.transactions().get(1).date()).isEqualTo(LocalDate.of(2026, 1, 29));
  }

  @Test
  void skips_rows_with_blank_isin() {
    String content =
        """
name;isin;quantity;buyingPrice;lastPrice;intradayVariation;amount;amountVariation;variation;lastMovementDate;compensation
CASH;;0;0;0;0;500;0;0;29/01/2026;
""";
    // blank ISIN → skipped, no valid positions → throws
    assertThatThrownBy(() -> parser.parse(csv(content))).isInstanceOf(CsvParsingException.class);
  }

  @Test
  void skips_empty_rows() {
    String content =
        """
name;isin;quantity;buyingPrice;lastPrice;intradayVariation;amount;amountVariation;variation;lastMovementDate;compensation
;;;;;;;;;;
""";
    // all rows empty → throws
    assertThatThrownBy(() -> parser.parse(csv(content))).isInstanceOf(CsvParsingException.class);
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
    // 2 positions + 1 auto-DEPOSIT = 3
    assertThat(result.imported()).isEqualTo(3);
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
    // index 0 is the auto-DEPOSIT, index 1 is the BUY position
    assertThat(result.transactions().get(0).description())
        .isEqualTo("Initial import — positions snapshot");
    assertThat(result.transactions().get(1).description())
        .isEqualTo("Imported from Boursobank positions");
  }

  @Test
  void parse_prepends_deposit_transaction_before_buy_transactions() {
    String content =
        """
name;isin;quantity;buyingPrice;lastPrice;intradayVariation;amount;amountVariation;variation;lastMovementDate;compensation
AMUNDI NASDAQ-100;FR0010342592;1;1100,00;2042,00;0,56;2042,00;932;36,7;29/01/2026;
""";
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.transactions().get(0).type()).isEqualTo(TransactionType.DEPOSIT);
    assertThat(result.transactions().get(1).type()).isEqualTo(TransactionType.BUY);
    assertThat(result.transactions().get(0).date()).isEqualTo(LocalDate.of(2026, 1, 29));
  }

  @Test
  void empty_trailing_rows_are_not_counted_as_skipped() {
    String content =
        """
name;isin;quantity;buyingPrice;lastPrice;intradayVariation;amount;amountVariation;variation;lastMovementDate;compensation
AMUNDI NASDAQ;FR0010342592;1;1100,00;2042,00;0,56;2042,00;932;36,7;29/01/2026;
;;;;;;;;;;
;;;;;;;;;;
""";
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.skipped()).isZero();
    assertThat(result.imported()).isEqualTo(2); // 1 position + 1 auto-deposit
    assertThat(result.errors()).isZero();
  }

  @Test
  void parse_deposit_amount_equals_sum_of_positions() {
    String content =
        """
name;isin;quantity;buyingPrice;lastPrice;intradayVariation;amount;amountVariation;variation;lastMovementDate;compensation
AMUNDI NASDAQ-100;FR0010342592;2;1100,00;2042,00;0,56;2042,00;932;36,7;29/01/2026;
SOME ETF;IE00B4L5Y983;3;50,00;55,00;0,10;110,00;10;10,0;15/03/2026;
""";
    // position 1: 2 × 1100 = 2200, position 2: 3 × 50 = 150, total = 2350
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.transactions().get(0).type()).isEqualTo(TransactionType.DEPOSIT);
    assertThat(result.transactions().get(0).totalAmount().amount())
        .isEqualByComparingTo(new BigDecimal("2350.00"));
  }
}
