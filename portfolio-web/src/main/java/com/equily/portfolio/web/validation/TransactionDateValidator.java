package com.equily.portfolio.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;

public class TransactionDateValidator
    implements ConstraintValidator<ValidTransactionDate, LocalDate> {

  @Override
  public boolean isValid(LocalDate date, ConstraintValidatorContext ctx) {
    if (date == null) return true; // @NotNull handles null
    LocalDate minDate = LocalDate.of(1900, Month.JANUARY, 1);
    LocalDate maxDate = LocalDate.now(ZoneId.of("Europe/Paris")).plusDays(1);
    return !date.isBefore(minDate) && !date.isAfter(maxDate);
  }
}
