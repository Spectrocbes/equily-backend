package com.equily.identity.infrastructure.email;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private final Resend resend;
  private final String fromAddress;
  private final String appBaseUrl;

  public EmailService(
      @Value("${email.resend.api-key}") String apiKey,
      @Value("${email.from-address}") String fromAddress,
      @Value("${email.app-base-url}") String appBaseUrl) {
    this(new Resend(apiKey), fromAddress, appBaseUrl);
  }

  EmailService(Resend resend, String fromAddress, String appBaseUrl) {
    this.resend = resend;
    this.fromAddress = fromAddress;
    this.appBaseUrl = appBaseUrl;
  }

  public void sendVerificationEmail(String toEmail, String displayName, String token) {
    String link = appBaseUrl + "/verify-email?token=" + token;
    String html =
        """
        <div style="font-family: Inter, sans-serif; max-width: 480px; margin: 0 auto;">
          <h2 style="color: #6366f1;">Welcome to Equily, %s!</h2>
          <p>Please verify your email address to activate your account.</p>
          <a href="%s"
             style="display: inline-block; background: #6366f1; color: white;
                    padding: 12px 24px; border-radius: 8px;
                    text-decoration: none; font-weight: 600; margin: 16px 0;">
            Verify Email Address
          </a>
          <p style="color: #94a3b8; font-size: 14px;">
            This link expires in 24 hours.
            If you didn't create an account, you can ignore this email.
          </p>
        </div>
        """
            .formatted(displayName, link);

    sendEmail(toEmail, "Verify your Equily account", html);
  }

  public void sendPasswordResetEmail(String toEmail, String displayName, String token) {
    String link = appBaseUrl + "/reset-password?token=" + token;
    String html =
        """
        <div style="font-family: Inter, sans-serif; max-width: 480px; margin: 0 auto;">
          <h2 style="color: #6366f1;">Reset your password, %s</h2>
          <p>We received a request to reset your Equily password.</p>
          <a href="%s"
             style="display: inline-block; background: #6366f1; color: white;
                    padding: 12px 24px; border-radius: 8px;
                    text-decoration: none; font-weight: 600; margin: 16px 0;">
            Reset Password
          </a>
          <p style="color: #94a3b8; font-size: 14px;">
            This link expires in 1 hour.
            If you didn't request this, you can safely ignore this email.
          </p>
        </div>
        """
            .formatted(displayName, link);

    sendEmail(toEmail, "Reset your Equily password", html);
  }

  private void sendEmail(String to, String subject, String html) {
    try {
      CreateEmailOptions params =
          CreateEmailOptions.builder().from(fromAddress).to(to).subject(subject).html(html).build();
      resend.emails().send(params);
      log.info("Email sent to {} — subject: {}", to, subject);
    } catch (Exception e) {
      log.error("Failed to send email to {}: {}", to, e.getMessage());
      // Don't throw — email failure should not block the auth flow
    }
  }
}
