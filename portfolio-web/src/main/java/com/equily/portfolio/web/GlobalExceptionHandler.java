package com.equily.portfolio.web;

import com.equily.identity.domain.exception.EmailNotVerifiedException;
import com.equily.identity.domain.exception.InvalidCredentialsException;
import com.equily.identity.domain.exception.InvalidTokenException;
import com.equily.identity.domain.exception.UserAlreadyExistsException;
import com.equily.portfolio.application.exception.CsvParsingException;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.portfolio.domain.exception.DepositLimitExceededException;
import com.equily.portfolio.domain.exception.InsufficientFundsException;
import com.equily.portfolio.domain.exception.InvalidHoldingException;
import com.equily.portfolio.domain.exception.InvalidTransactionException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                    (first, second) -> first));
    return ResponseEntity.badRequest().body(errors);
  }

  @ExceptionHandler(AccountNotFoundException.class)
  ResponseEntity<String> handleNotFound(AccountNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
  }

  @ExceptionHandler(InsufficientFundsException.class)
  ResponseEntity<String> handleInsufficientFunds(InsufficientFundsException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
  }

  @ExceptionHandler(InvalidHoldingException.class)
  ResponseEntity<String> handleInvalidHolding(InvalidHoldingException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
  }

  @ExceptionHandler(InvalidTransactionException.class)
  ResponseEntity<String> handleInvalidTransaction(InvalidTransactionException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }

  @ExceptionHandler(CsvParsingException.class)
  ResponseEntity<String> handleCsvParsing(CsvParsingException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }

  @ExceptionHandler(UserAlreadyExistsException.class)
  ResponseEntity<String> handleUserAlreadyExists(UserAlreadyExistsException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ResponseEntity<String> handleInvalidCredentials(InvalidCredentialsException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
  }

  @ExceptionHandler(EmailNotVerifiedException.class)
  ResponseEntity<String> handleEmailNotVerified(EmailNotVerifiedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
  }

  @ExceptionHandler(InvalidTokenException.class)
  ResponseEntity<String> handleInvalidToken(InvalidTokenException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }

  @ExceptionHandler(DepositLimitExceededException.class)
  ResponseEntity<DepositLimitErrorResponse> handleDepositLimit(DepositLimitExceededException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(
            new DepositLimitErrorResponse(
                "DEPOSIT_LIMIT_EXCEEDED",
                ex.subType().name(),
                ex.limit().amount(),
                ex.currentTotal().amount(),
                ex.attempted().amount(),
                ex.remaining().amount()));
  }

  record DepositLimitErrorResponse(
      String code,
      String subType,
      BigDecimal limit,
      BigDecimal currentTotal,
      BigDecimal attempted,
      BigDecimal remaining) {}
}
