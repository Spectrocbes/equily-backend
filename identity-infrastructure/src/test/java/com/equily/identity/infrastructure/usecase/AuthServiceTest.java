package com.equily.identity.infrastructure.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.equily.identity.domain.User;
import com.equily.identity.domain.UserId;
import com.equily.identity.domain.UserRepository;
import com.equily.identity.domain.exception.InvalidCredentialsException;
import com.equily.identity.domain.exception.UserAlreadyExistsException;
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
  @InjectMocks AuthService authService;

  private User testUser() {
    return User.reconstruct(
        UserId.generate(), "alice@example.com", "hashed", "Alice", Instant.now());
  }

  @Test
  void register_saves_user_and_returns_token_pair() {
    when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
    when(passwordEncoder.encode("password")).thenReturn("hashed");
    when(jwtService.generateAccessToken(any())).thenReturn("access-token");
    when(refreshTokenService.createRefreshToken(any())).thenReturn("refresh-token");

    AuthTokenPair result = authService.register("alice@example.com", "password", "Alice");

    verify(userRepository).save(any());
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
  void login_returns_token_pair_for_valid_credentials() {
    User user = testUser();
    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
    when(jwtService.generateAccessToken(user)).thenReturn("access-token");
    when(refreshTokenService.createRefreshToken(user.id())).thenReturn("refresh-token");

    AuthTokenPair result = authService.login("alice@example.com", "password");

    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
  }

  @Test
  void login_throws_for_wrong_password() {
    User user = testUser();
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
    User user = testUser();

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
}
