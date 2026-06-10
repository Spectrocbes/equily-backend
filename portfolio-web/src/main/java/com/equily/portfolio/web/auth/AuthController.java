package com.equily.portfolio.web.auth;

import com.equily.identity.domain.User;
import com.equily.identity.domain.UserId;
import com.equily.identity.domain.UserRepository;
import com.equily.identity.infrastructure.usecase.AuthService;
import com.equily.identity.infrastructure.usecase.AuthTokenPair;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;
  private final UserRepository userRepository;

  public AuthController(AuthService authService, UserRepository userRepository) {
    this.authService = authService;
    this.userRepository = userRepository;
  }

  @PostMapping("/register")
  ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
    AuthTokenPair tokens =
        authService.register(request.email(), request.password(), request.displayName());
    User user = userRepository.findByEmail(request.email()).orElseThrow();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            new AuthResponse(
                tokens.accessToken(), tokens.refreshToken(), user.email(), user.displayName()));
  }

  @PostMapping("/login")
  ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
    AuthTokenPair tokens = authService.login(request.email(), request.password());
    User user = userRepository.findByEmail(request.email()).orElseThrow();
    return ResponseEntity.ok(
        new AuthResponse(
            tokens.accessToken(), tokens.refreshToken(), user.email(), user.displayName()));
  }

  @PostMapping("/refresh")
  ResponseEntity<AuthResponse> refresh(@RequestBody @Valid RefreshRequest request) {
    AuthTokenPair tokens = authService.refresh(request.refreshToken());
    return ResponseEntity.ok(
        new AuthResponse(tokens.accessToken(), tokens.refreshToken(), null, null));
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(Authentication authentication) {
    // Logout must always succeed — even if the JWT is expired or absent.
    // AnonymousAuthenticationToken (principal = "anonymousUser") must NOT be cast to UserId.
    if (authentication != null && authentication.getPrincipal() instanceof UserId userId) {
      authService.logout(userId);
    }
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  ResponseEntity<AuthResponse> me(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UserId userId)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    User user = userRepository.findById(userId).orElseThrow();
    return ResponseEntity.ok(new AuthResponse(null, null, user.email(), user.displayName()));
  }

  @PostMapping("/verify-email")
  ResponseEntity<Void> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
    authService.verifyEmail(request.token());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/resend-verification")
  ResponseEntity<Void> resendVerification(@RequestBody @Valid ResendVerificationRequest request) {
    authService.resendVerificationEmail(request.email());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/forgot-password")
  ResponseEntity<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
    authService.requestPasswordReset(request.email());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/validate-reset-token")
  ResponseEntity<Void> validateResetToken(@RequestBody @Valid ValidateResetTokenRequest request) {
    authService.validateResetToken(request.token());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/reset-password")
  ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
    authService.resetPassword(request.token(), request.newPassword());
    return ResponseEntity.ok().build();
  }
}
