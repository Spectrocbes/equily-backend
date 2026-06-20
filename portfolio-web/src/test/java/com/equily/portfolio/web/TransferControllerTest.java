package com.equily.portfolio.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.application.TransferUseCase;
import com.equily.portfolio.domain.exception.TransferRoutingException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransferController.class)
@Import(TestSecurityConfig.class)
class TransferControllerTest {

  @MockitoBean private TransferUseCase transferUseCase;

  @Autowired private MockMvc mockMvc;

  private Authentication mockAuth() {
    UserId userId = UserId.generate();
    return new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
  }

  @Test
  void executeTransfer_returns_200_with_transferId() throws Exception {
    UUID transferId = UUID.randomUUID();
    when(transferUseCase.executeTransfer(any())).thenReturn(transferId);

    String fromId = UUID.randomUUID().toString();
    String toId = UUID.randomUUID().toString();

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fromAccountId": "%s",
                      "toAccountId": "%s",
                      "amount": 500.00,
                      "currency": "EUR",
                      "date": "2026-01-01"
                    }
                    """
                        .formatted(fromId, toId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transferId").value(transferId.toString()));
  }

  @Test
  void executeTransfer_returns_422_on_routing_violation() throws Exception {
    doThrow(new TransferRoutingException("Direct transfer between savings accounts is forbidden."))
        .when(transferUseCase)
        .executeTransfer(any());

    String fromId = UUID.randomUUID().toString();
    String toId = UUID.randomUUID().toString();

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fromAccountId": "%s",
                      "toAccountId": "%s",
                      "amount": 100.00,
                      "currency": "EUR",
                      "date": "2026-01-01"
                    }
                    """
                        .formatted(fromId, toId)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void executeTransfer_returns_400_on_missing_fromAccountId() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transfers")
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "amount": 100.00,
                      "currency": "EUR",
                      "date": "2026-01-01"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }
}
