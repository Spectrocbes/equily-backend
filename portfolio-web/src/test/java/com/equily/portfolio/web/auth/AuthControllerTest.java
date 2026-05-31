package com.equily.portfolio.web.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.equily.identity.domain.User;
import com.equily.identity.domain.UserId;
import com.equily.identity.domain.UserRepository;
import com.equily.identity.domain.exception.EmailNotVerifiedException;
import com.equily.identity.domain.exception.InvalidCredentialsException;
import com.equily.identity.domain.exception.InvalidTokenException;
import com.equily.identity.domain.exception.UserAlreadyExistsException;
import com.equily.identity.infrastructure.usecase.AuthService;
import com.equily.identity.infrastructure.usecase.AuthTokenPair;
import com.equily.portfolio.web.TestSecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
class AuthControllerTest {

  @MockitoBean AuthService authService;
  @MockitoBean UserRepository userRepository;

  @Autowired MockMvc mockMvc;

  private User testUser() {
    return User.reconstruct(
        UserId.generate(), "alice@example.com", "hashed", "Alice", true, Instant.now());
  }

  @Test
  void register_returns_201_with_tokens() throws Exception {
    User user = testUser();
    when(authService.register("alice@example.com", "password1", "Alice"))
        .thenReturn(new AuthTokenPair("access-token", "refresh-token"));
    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"alice@example.com","password":"password1","displayName":"Alice"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
        .andExpect(jsonPath("$.email").value("alice@example.com"));
  }

  @Test
  void register_returns_409_when_email_already_exists() throws Exception {
    when(authService.register(any(), any(), any()))
        .thenThrow(new UserAlreadyExistsException("alice@example.com"));

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"alice@example.com","password":"password1","displayName":"Alice"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void login_returns_200_with_tokens() throws Exception {
    User user = testUser();
    when(authService.login("alice@example.com", "password1"))
        .thenReturn(new AuthTokenPair("access-token", "refresh-token"));
    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"alice@example.com","password":"password1"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.email").value("alice@example.com"));
  }

  @Test
  void login_returns_401_for_invalid_credentials() throws Exception {
    when(authService.login(any(), any())).thenThrow(new InvalidCredentialsException());

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"alice@example.com","password":"wrong"}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void login_returns_403_when_email_not_verified() throws Exception {
    when(authService.login(any(), any())).thenThrow(new EmailNotVerifiedException());

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"alice@example.com","password":"password1"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void refresh_returns_200_with_new_tokens() throws Exception {
    when(authService.refresh("old-refresh-token"))
        .thenReturn(new AuthTokenPair("new-access-token", "new-refresh-token"));

    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"refreshToken":"old-refresh-token"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-access-token"))
        .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
  }

  @Test
  void logout_returns_204() throws Exception {
    UserId userId = UserId.generate();
    doNothing().when(authService).logout(any());

    mockMvc
        .perform(
            post("/auth/logout")
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(
                            userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
        .andExpect(status().isNoContent());
  }

  @Test
  void getMe_returns_200_with_user_info() throws Exception {
    UserId userId = UserId.generate();
    User user = User.reconstruct(userId, "me@test.com", "hash", "Me User", true, Instant.now());
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    mockMvc
        .perform(
            get("/auth/me")
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(
                            userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("me@test.com"))
        .andExpect(jsonPath("$.displayName").value("Me User"));
  }

  @Test
  void verifyEmail_returns_200_for_valid_token() throws Exception {
    doNothing().when(authService).verifyEmail("valid-token");

    mockMvc
        .perform(
            post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"token":"valid-token"}
                    """))
        .andExpect(status().isOk());
  }

  @Test
  void verifyEmail_returns_400_for_invalid_token() throws Exception {
    doThrow(new InvalidTokenException("Invalid or already used verification token"))
        .when(authService)
        .verifyEmail("bad-token");

    mockMvc
        .perform(
            post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"token":"bad-token"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void forgotPassword_always_returns_200() throws Exception {
    doNothing().when(authService).requestPasswordReset(any());

    mockMvc
        .perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"alice@example.com"}
                    """))
        .andExpect(status().isOk());
  }

  @Test
  void resetPassword_returns_200_for_valid_token() throws Exception {
    doNothing().when(authService).resetPassword(any(), any());

    mockMvc
        .perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"token":"valid-token","newPassword":"newpass123"}
                    """))
        .andExpect(status().isOk());
  }

  @Test
  void resetPassword_returns_400_for_invalid_token() throws Exception {
    doThrow(new InvalidTokenException("Invalid or already used reset token"))
        .when(authService)
        .resetPassword(any(), any());

    mockMvc
        .perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"token":"bad-token","newPassword":"newpass123"}
                    """))
        .andExpect(status().isBadRequest());
  }
}
