package com.equily.portfolio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.equily.portfolio.application.exception.CsvParsingException;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.exception.TransactionNotFoundException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleTransactionNotFound_returns_404_with_message() {
    TransactionNotFoundException ex = new TransactionNotFoundException(TransactionId.generate());
    ResponseEntity<String> response = handler.handleTransactionNotFound(ex);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("Transaction not found");
  }

  @Test
  void handleCsvParsing_returns_400_with_message() {
    CsvParsingException ex = new CsvParsingException("bad file format");
    ResponseEntity<String> response = handler.handleCsvParsing(ex);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isEqualTo("bad file format");
  }

  @Test
  void handleValidation_uses_invalid_value_fallback_when_message_is_null() {
    BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "target");
    br.addError(new FieldError("target", "myField", null, false, null, null, null));
    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException(mock(MethodParameter.class), br);

    ResponseEntity<Map<String, String>> response = handler.handleValidation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("myField", "Invalid value");
  }

  @Test
  void handleValidation_first_error_wins_for_duplicate_field() {
    BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "target");
    br.addError(new FieldError("target", "myField", null, false, null, null, "first error"));
    br.addError(new FieldError("target", "myField", null, false, null, null, "second error"));
    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException(mock(MethodParameter.class), br);

    ResponseEntity<Map<String, String>> response = handler.handleValidation(ex);

    assertThat(response.getBody()).containsEntry("myField", "first error");
  }
}
