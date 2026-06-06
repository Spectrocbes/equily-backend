package com.equily.portfolio.web.validation;

import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.web.RecordTransactionRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class TransactionAmountValidator
    implements ConstraintValidator<ValidTransactionAmount, RecordTransactionRequest> {

  @Override
  public boolean isValid(RecordTransactionRequest req, ConstraintValidatorContext ctx) {
    if (req == null || req.totalAmount() == null) return true;
    TransactionType type;
    try {
      type = TransactionType.valueOf(req.type());
    } catch (IllegalArgumentException | NullPointerException e) {
      return true; // invalid/null type is caught by @NotBlank and controller enum parsing
    }
    return switch (type) {
      case INTEREST, DIVIDEND, DEPOSIT, WITHDRAWAL ->
          req.totalAmount().compareTo(BigDecimal.ZERO) > 0;
      default -> true;
    };
  }
}
