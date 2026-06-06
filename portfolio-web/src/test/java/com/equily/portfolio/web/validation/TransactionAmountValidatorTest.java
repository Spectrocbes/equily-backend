package com.equily.portfolio.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.equily.portfolio.web.RecordTransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionAmountValidatorTest {

  private final TransactionAmountValidator validator = new TransactionAmountValidator();
  private static final LocalDate VALID_DATE = LocalDate.of(2026, 1, 1);

  private RecordTransactionRequest req(String type, BigDecimal amount) {
    return new RecordTransactionRequest(
        type, null, null, null, null, amount, "EUR", VALID_DATE, BigDecimal.ZERO, null);
  }

  @Test
  void interest_with_zero_amount_is_invalid() {
    assertThat(validator.isValid(req("INTEREST", BigDecimal.ZERO), null)).isFalse();
  }

  @Test
  void interest_with_negative_amount_is_invalid() {
    assertThat(validator.isValid(req("INTEREST", new BigDecimal("-1")), null)).isFalse();
  }

  @Test
  void interest_with_positive_amount_is_valid() {
    assertThat(validator.isValid(req("INTEREST", new BigDecimal("0.01")), null)).isTrue();
  }

  @Test
  void dividend_with_zero_amount_is_invalid() {
    assertThat(validator.isValid(req("DIVIDEND", BigDecimal.ZERO), null)).isFalse();
  }

  @Test
  void buy_with_zero_amount_is_valid() {
    assertThat(validator.isValid(req("BUY", BigDecimal.ZERO), null)).isTrue();
  }

  @Test
  void null_request_is_valid() {
    assertThat(validator.isValid(null, null)).isTrue();
  }

  @Test
  void null_total_amount_is_valid() {
    assertThat(validator.isValid(req("INTEREST", null), null)).isTrue();
  }
}
