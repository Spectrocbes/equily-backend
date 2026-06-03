package com.equily.portfolio.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = TransactionDateValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTransactionDate {
  String message() default "Transaction date must be between 1900-01-01 and tomorrow";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
