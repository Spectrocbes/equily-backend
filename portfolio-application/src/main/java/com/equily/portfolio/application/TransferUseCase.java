package com.equily.portfolio.application;

import java.util.UUID;

/** Input port for account-to-account and external transfer operations. */
public interface TransferUseCase {

  /**
   * Executes a transfer according to the command. Returns the transferId linking the two
   * transaction legs (or the single outgoing leg for external transfers).
   *
   * @throws com.equily.portfolio.domain.exception.AccountNotFoundException if either account does
   *     not exist or is not owned by the requesting user
   * @throws com.equily.portfolio.domain.exception.TransferRoutingException if the transfer is not
   *     permitted between the given account types
   * @throws com.equily.portfolio.domain.exception.InsufficientFundsException if the source account
   *     has insufficient balance
   */
  UUID executeTransfer(TransferCommand command);
}
