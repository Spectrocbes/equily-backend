package com.equily.portfolio.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionDateValidatorTest {

  private final TransactionDateValidator validator = new TransactionDateValidator();

  @Test
  void isValid_returns_true_for_null_date() {
    assertThat(validator.isValid(null, null)).isTrue();
  }

  @Test
  void isValid_returns_false_for_date_before_1900() {
    assertThat(validator.isValid(LocalDate.of(1899, 12, 31), null)).isFalse();
  }
}
