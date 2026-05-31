package com.equily.identity.infrastructure.email;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private final Resend resend;
  private final String fromAddress;
  private final String appBaseUrl;

  @Autowired
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

  private void sendActionEmail(
      String toEmail,
      String displayName,
      String token,
      String path,
      String title,
      String intro,
      String buttonText,
      String expirationText,
      String ignoreText,
      String subject) {

    String link = appBaseUrl + path + "?token=" + token;

    String html =
        """
            <div style="font-family: Inter, sans-serif; max-width: 480px; margin: 0 auto;">
              <h2 style="color: #6366f1;">%s, %s</h2>
              <p>%s</p>
              <a href="%s"
                 style="display: inline-block; background: #6366f1; color: white;
                        padding: 12px 24px; border-radius: 8px;
                        text-decoration: none; font-weight: 600; margin: 16px 0;">
                %s
              </a>
              <p style="color: #94a3b8; font-size: 14px;">
                %s
                %s
              </p>
            </div>
            """
            .formatted(title, displayName, intro, link, buttonText, expirationText, ignoreText);

    sendEmail(toEmail, subject, html);
  }

  public void sendVerificationEmail(String toEmail, String displayName, String token) {
    sendActionEmail(
        toEmail,
        displayName,
        token,
        "/verify-email",
        "Welcome to Equily",
        "Please verify your email address to activate your account.",
        "Verify Email Address",
        "This link expires in 24 hours.",
        "If you didn't create an account, you can ignore this email.",
        "Verify your Equily account");
  }

  public void sendPasswordResetEmail(String toEmail, String displayName, String token) {
    sendActionEmail(
        toEmail,
        displayName,
        token,
        "/reset-password",
        "Reset your password",
        "We received a request to reset your Equily password.",
        "Reset Password",
        "This link expires in 1 hour.",
        "If you didn't request this, you can safely ignore this email.",
        "Reset your Equily password");
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
