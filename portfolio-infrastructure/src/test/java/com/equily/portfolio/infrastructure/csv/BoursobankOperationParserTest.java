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

class BoursobankOperationParserTest {

  private final BoursobankOperationParser parser = new BoursobankOperationParser();

  private InputStream csv(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void parses_deposit_correctly() {
    String content =
        """
        Date opération;Date valeur;Opération;Valeur;Code ISIN;Montant;Quantité;Cours
        29/01/2026;29/01/2026;VIR Investissement;;;3700;0;0,00 €
        """;
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.transactions().get(0).type()).isEqualTo(TransactionType.DEPOSIT);
    assertThat(result.transactions().get(0).totalAmount().amount())
        .isEqualByComparingTo(new BigDecimal("3700"));
    assertThat(result.transactions().get(0).date()).isEqualTo(LocalDate.of(2026, 1, 29));
    assertThat(result.transactions().get(0).ticker()).isNull();
  }

  @Test
  void parses_buy_with_isin_as_ticker() {
    String content =
        """
        Date opération;Date valeur;Opération;Valeur;Code ISIN;Montant;Quantité;Cours
        29/01/2026;02/02/2026;ACHAT COMPTANT;AM.P.NASD.100 D.2X;FR0010342592;-1100;1;1 100,00 €
        """;
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.transactions().get(0).type()).isEqualTo(TransactionType.BUY);
    assertThat(result.transactions().get(0).ticker().symbol()).isEqualTo("FR0010342592");
    assertThat(result.transactions().get(0).totalAmount().amount())
        .isEqualByComparingTo(new BigDecimal("1100"));
    assertThat(result.transactions().get(0).quantity()).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void parses_sell_correctly() {
    String content =
        """
        Date opération;Date valeur;Opération;Valeur;Code ISIN;Montant;Quantité;Cours
        15/03/2026;15/03/2026;VENTE COMPTANT;AM.P.NASD.100;FR0010342592;2042;1;2 042,00 €
        """;
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.transactions().get(0).type()).isEqualTo(TransactionType.SELL);
    assertThat(result.transactions().get(0).ticker().symbol()).isEqualTo("FR0010342592");
    assertThat(result.transactions().get(0).totalAmount().amount())
        .isEqualByComparingTo(new BigDecimal("2042"));
  }

  @Test
  void skips_empty_rows() {
    String content =
        """
        Date opération;Date valeur;Opération;Valeur;Code ISIN;Montant;Quantité;Cours
        29/01/2026;29/01/2026;VIR Investissement;;;3700;0;0,00 €
        ;;;;;;;
        ;;;;;;;
        """;
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.skipped()).isEqualTo(2);
  }

  @Test
  void skips_unknown_operation_type() {
    String content =
        """
        Date opération;Date valeur;Opération;Valeur;Code ISIN;Montant;Quantité;Cours
        29/01/2026;29/01/2026;OPERATION INCONNUE;;;100;0;0,00 €
        """;
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.imported()).isZero();
    assertThat(result.skipped()).isEqualTo(1);
  }

  @Test
  void parses_multiple_rows_correctly() {
    String content =
        """
        Date opération;Date valeur;Opération;Valeur;Code ISIN;Montant;Quantité;Cours
        29/01/2026;29/01/2026;VIR Investissement;;;3700;0;0,00 €
        29/01/2026;02/02/2026;ACHAT COMPTANT;AM.P.NASD.100 D.2X;FR0010342592;-1100;1;1 100,00 €
        15/03/2026;15/03/2026;VENTE COMPTANT;AM.P.NASD.100;FR0010342592;2042;1;2 042,00 €
        """;
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.imported()).isEqualTo(3);
    assertThat(result.errors()).isZero();
    assertThat(result.skipped()).isZero();
  }

  @Test
  void buy_amount_is_absolute_value_of_negative_montant() {
    String content =
        """
        Date opération;Date valeur;Opération;Valeur;Code ISIN;Montant;Quantité;Cours
        29/01/2026;02/02/2026;ACHAT COMPTANT;SOME FUND;FR0010342592;-500;2;250,00 €
        """;
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.transactions().get(0).totalAmount().amount())
        .isEqualByComparingTo(new BigDecimal("500"));
  }

  @Test
  void description_is_set_to_imported_from_boursobank() {
    String content =
        """
        Date opération;Date valeur;Opération;Valeur;Code ISIN;Montant;Quantité;Cours
        29/01/2026;29/01/2026;VIR Investissement;;;3700;0;0,00 €
        """;
    CsvImportResult result = parser.parse(csv(content));
    assertThat(result.transactions().get(0).description()).isEqualTo("Imported from Boursobank");
  }
}
