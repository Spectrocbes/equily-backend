package com.equily.portfolio.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = TransactionAmountValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTransactionAmount {
  String message() default "Transaction amount must be greater than zero";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
