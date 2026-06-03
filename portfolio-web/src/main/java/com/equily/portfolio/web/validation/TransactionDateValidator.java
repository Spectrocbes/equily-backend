package com.equily.portfolio.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;

public class TransactionDateValidator
    implements ConstraintValidator<ValidTransactionDate, LocalDate> {

  @Override
  public boolean isValid(LocalDate date, ConstraintValidatorContext ctx) {
    if (date == null) return true; // @NotNull handles null
    LocalDate minDate = LocalDate.of(1900, 1, 1);
    LocalDate maxDate = LocalDate.now().plusDays(1);
    return !date.isBefore(minDate) && !date.isAfter(maxDate);
  }
}
