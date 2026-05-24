package com.equily.portfolio.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.equily.portfolio.application.FinancialAccountUseCase;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.portfolio.domain.exception.InsufficientFundsException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FinancialAccountController.class)
class FinancialAccountControllerTest {

  @MockitoBean private FinancialAccountUseCase useCase;

  @Autowired private MockMvc mockMvc;

  private FinancialAccount testAccount;

  @BeforeEach
  void setUp() {
    testAccount =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.valueOf(1000), Currency.getInstance("EUR")));
  }

  @Test
  void getAllAccounts_returns200WithList() throws Exception {
    when(useCase.getAllAccounts()).thenReturn(List.of(testAccount));

    mockMvc
        .perform(get("/api/v1/accounts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void getAccountById_returns200WhenFound() throws Exception {
    FinancialAccountId id = testAccount.id();
    when(useCase.getAccountById(id)).thenReturn(testAccount);

    mockMvc
        .perform(get("/api/v1/accounts/{id}", id.value().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.value().toString()));
  }

  @Test
  void getAccountById_returns404WhenNotFound() throws Exception {
    FinancialAccountId id = FinancialAccountId.generate();
    when(useCase.getAccountById(id)).thenThrow(new AccountNotFoundException(id));

    mockMvc
        .perform(get("/api/v1/accounts/{id}", id.value().toString()))
        .andExpect(status().isNotFound());
  }

  @Test
  void createAccount_returns201WithId() throws Exception {
    FinancialAccountId newId = FinancialAccountId.generate();
    when(useCase.createAccount(any())).thenReturn(newId);

    mockMvc
        .perform(
            post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "My PEA", "accountType": "PEA",
                     "initialBalance": 1000, "currency": "EUR"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(content().string(newId.value().toString()));
  }

  @Test
  void recordTransaction_returns204() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/accounts/{id}/transactions", testAccount.id().value().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"type": "DEPOSIT", "totalAmount": 500,
                     "totalCurrency": "EUR", "date": "2026-05-24"}
                    """))
        .andExpect(status().isNoContent());
  }

  @Test
  void recordTransaction_returns422OnInsufficientFunds() throws Exception {
    doThrow(new InsufficientFundsException("insufficient funds"))
        .when(useCase)
        .recordTransaction(any());

    mockMvc
        .perform(
            post("/api/v1/accounts/{id}/transactions", testAccount.id().value().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"type": "WITHDRAWAL", "totalAmount": 999999,
                     "totalCurrency": "EUR", "date": "2026-05-24"}
                    """))
        .andExpect(status().isUnprocessableEntity());
  }
}
