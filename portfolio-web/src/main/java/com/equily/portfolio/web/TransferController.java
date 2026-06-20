package com.equily.portfolio.web;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.application.TransferCommand;
import com.equily.portfolio.application.TransferUseCase;
import com.equily.portfolio.domain.FinancialAccountId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/transfers")
class TransferController {

  private final TransferUseCase transferUseCase;

  TransferController(TransferUseCase transferUseCase) {
    this.transferUseCase = transferUseCase;
  }

  @PostMapping
  ResponseEntity<TransferResponse> executeTransfer(
      @RequestBody @Valid TransferRequest request, Authentication authentication) {
    UserId userId = extractUserId(authentication);
    UUID transferId =
        transferUseCase.executeTransfer(
            new TransferCommand(
                new FinancialAccountId(UUID.fromString(request.fromAccountId())),
                request.toAccountId() != null
                    ? new FinancialAccountId(UUID.fromString(request.toAccountId()))
                    : null,
                userId,
                request.amount(),
                request.currency() != null ? request.currency() : "EUR",
                request.date(),
                request.description(),
                request.externalAddress()));
    return ResponseEntity.ok(new TransferResponse(transferId.toString()));
  }

  private UserId extractUserId(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof UserId userId)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return userId;
  }

  record TransferRequest(
      @NotBlank String fromAccountId,
      String toAccountId,
      @NotNull @Positive BigDecimal amount,
      String currency,
      @NotNull LocalDate date,
      String description,
      String externalAddress) {}

  record TransferResponse(String transferId) {}
}
