package com.equily.identity.infrastructure.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.equily.identity.domain.User;
import com.equily.identity.domain.UserId;
import com.equily.identity.domain.UserRepository;
import com.equily.identity.domain.exception.EmailNotVerifiedException;
import com.equily.identity.domain.exception.InvalidCredentialsException;
import com.equily.identity.domain.exception.InvalidTokenException;
import com.equily.identity.domain.exception.UserAlreadyExistsException;
import com.equily.identity.domain.exception.UserNotFoundException;
import com.equily.identity.infrastructure.email.EmailService;
import com.equily.identity.infrastructure.persistence.EmailVerificationService;
import com.equily.identity.infrastructure.persistence.PasswordResetService;
import com.equily.identity.infrastructure.persistence.RefreshTokenService;
import com.equily.identity.infrastructure.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock UserRepository userRepository;
  @Mock JwtService jwtService;
  @Mock RefreshTokenService refreshTokenService;
  @Mock PasswordEncoder passwordEncoder;
  @Mock EmailVerificationService emailVerificationService;
  @Mock EmailService emailService;
  @Mock PasswordResetService passwordResetService;
  @InjectMocks AuthService authService;

  private User verifiedUser() {
    return User.reconstruct(
        UserId.generate(), "alice@example.com", "hashed", "Alice", true, Instant.now());
  }

  private User unverifiedUser() {
    return User.reconstruct(
        UserId.generate(), "alice@example.com", "hashed", "Alice", false, Instant.now());
  }

  @Test
  void register_saves_user_and_returns_token_pair() {
    when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
    when(passwordEncoder.encode("password")).thenReturn("hashed");
    when(jwtService.generateAccessToken(any())).thenReturn("access-token");
    when(refreshTokenService.createRefreshToken(any())).thenReturn("refresh-token");
    when(emailVerificationService.createVerificationToken(any())).thenReturn("raw-token");

    AuthTokenPair result = authService.register("alice@example.com", "password", "Alice");

    verify(userRepository).save(any());
    verify(emailVerificationService).createVerificationToken(any());
    verify(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
  }

  @Test
  void register_throws_when_email_already_exists() {
    when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

    assertThatThrownBy(() -> authService.register("alice@example.com", "password", "Alice"))
        .isInstanceOf(UserAlreadyExistsException.class);
  }

  @Test
  void login_returns_token_pair_for_verified_user() {
    User user = verifiedUser();
    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
    when(jwtService.generateAccessToken(user)).thenReturn("access-token");
    when(refreshTokenService.createRefreshToken(user.id())).thenReturn("refresh-token");

    AuthTokenPair result = authService.login("alice@example.com", "password");

    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
  }

  @Test
  void login_throws_EmailNotVerifiedException_when_email_not_verified() {
    User user = unverifiedUser();
    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

    assertThatThrownBy(() -> authService.login("alice@example.com", "password"))
        .isInstanceOf(EmailNotVerifiedException.class);
  }

  @Test
  void login_throws_for_wrong_password() {
    User user = verifiedUser();
    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

    assertThatThrownBy(() -> authService.login("alice@example.com", "wrong"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void login_throws_for_unknown_email() {
    when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login("nobody@example.com", "password"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void refresh_returns_new_token_pair_when_valid() {
    UserId userId = UserId.generate();
    User user = verifiedUser();

    when(refreshTokenService.rotateRefreshToken(any(), any())).thenReturn(Optional.of(userId));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(refreshTokenService.createRefreshToken(userId)).thenReturn("new-refresh");
    when(jwtService.generateAccessToken(user)).thenReturn("new-access");

    AuthTokenPair result = authService.refresh("old-refresh-token");

    assertThat(result.accessToken()).isEqualTo("new-access");
    assertThat(result.refreshToken()).isEqualTo("new-refresh");
  }

  @Test
  void refresh_throws_InvalidCredentialsException_when_token_invalid() {
    when(refreshTokenService.rotateRefreshToken(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.refresh("bad-token"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void logout_revokes_all_refresh_tokens_for_user() {
    UserId userId = UserId.generate();

    authService.logout(userId);

    verify(refreshTokenService).revokeAllForUser(userId);
  }

  @Test
  void verifyEmail_delegates_to_emailVerificationService() {
    authService.verifyEmail("some-token");

    verify(emailVerificationService).verifyEmail("some-token");
  }

  @Test
  void requestPasswordReset_sends_email_when_user_exists() {
    User user = verifiedUser();
    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
    when(passwordResetService.createResetToken(user.id())).thenReturn("reset-token");

    authService.requestPasswordReset("alice@example.com");

    verify(passwordResetService).createResetToken(user.id());
    verify(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString());
  }

  @Test
  void requestPasswordReset_silently_succeeds_when_user_not_found() {
    when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

    authService.requestPasswordReset("nobody@example.com");
  }

  @Test
  void validateResetToken_delegates_to_passwordResetService() {
    doNothing().when(passwordResetService).validateToken("token123");

    assertThatNoException().isThrownBy(() -> authService.validateResetToken("token123"));

    verify(passwordResetService).validateToken("token123");
  }

  @Test
  void validateResetToken_propagates_exception() {
    doThrow(new InvalidTokenException("expired")).when(passwordResetService).validateToken(any());

    assertThatThrownBy(() -> authService.validateResetToken("bad"))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void resendVerificationEmail_throws_when_user_not_found() {
    when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.resendVerificationEmail("nobody@example.com"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void resetPassword_delegates_to_passwordResetService() {
    authService.resetPassword("some-token", "newpassword");

    verify(passwordResetService).resetPassword("some-token", "newpassword");
  }

  @Test
  void resendVerificationEmail_sends_email_when_user_not_verified() {
    User user = unverifiedUser();
    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
    when(emailVerificationService.createVerificationToken(user.id())).thenReturn("raw-token");

    authService.resendVerificationEmail("alice@example.com");

    verify(emailVerificationService).createVerificationToken(user.id());
    verify(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
  }

  @Test
  void resendVerificationEmail_does_nothing_when_already_verified() {
    User user = verifiedUser();
    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

    authService.resendVerificationEmail("alice@example.com");

    verify(emailVerificationService, never()).createVerificationToken(any());
    verify(emailService, never()).sendVerificationEmail(any(), any(), any());
  }
}
