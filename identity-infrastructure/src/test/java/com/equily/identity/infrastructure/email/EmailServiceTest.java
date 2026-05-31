package com.equily.identity.infrastructure.email;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailServiceTest {

  private final Resend resend = mock(Resend.class);
  private final Emails emails = mock(Emails.class);
  private final EmailService emailService =
      new EmailService(resend, "from@test.com", "http://localhost:4200");

  @BeforeEach
  void setUp() {
    when(resend.emails()).thenReturn(emails);
  }

  @Test
  void sendVerificationEmail_sends_via_resend() throws ResendException {
    emailService.sendVerificationEmail("alice@example.com", "Alice", "raw-token");

    verify(emails).send(any(CreateEmailOptions.class));
  }

  @Test
  void sendPasswordResetEmail_sends_via_resend() throws ResendException {
    emailService.sendPasswordResetEmail("alice@example.com", "Alice", "raw-token");

    verify(emails).send(any(CreateEmailOptions.class));
  }

  @Test
  void sendEmail_does_not_rethrow_when_resend_fails() throws ResendException {
    doThrow(new ResendException("API error")).when(emails).send(any());

    emailService.sendVerificationEmail("alice@example.com", "Alice", "token");
    // no exception expected — fire-and-forget
  }
}
