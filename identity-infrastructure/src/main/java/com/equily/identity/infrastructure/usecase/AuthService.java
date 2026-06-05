package com.equily.identity.infrastructure.usecase;

import com.equily.identity.domain.User;
import com.equily.identity.domain.UserId;
import com.equily.identity.domain.UserRepository;
import com.equily.identity.domain.exception.EmailNotVerifiedException;
import com.equily.identity.domain.exception.InvalidCredentialsException;
import com.equily.identity.domain.exception.UserAlreadyExistsException;
import com.equily.identity.domain.exception.UserNotFoundException;
import com.equily.identity.infrastructure.email.EmailService;
import com.equily.identity.infrastructure.persistence.EmailVerificationService;
import com.equily.identity.infrastructure.persistence.PasswordResetService;
import com.equily.identity.infrastructure.persistence.RefreshTokenService;
import com.equily.identity.infrastructure.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final PasswordEncoder passwordEncoder;
  private final EmailVerificationService emailVerificationService;
  private final EmailService emailService;
  private final PasswordResetService passwordResetService;

  public AuthService(
      UserRepository userRepository,
      JwtService jwtService,
      RefreshTokenService refreshTokenService,
      PasswordEncoder passwordEncoder,
      EmailVerificationService emailVerificationService,
      EmailService emailService,
      PasswordResetService passwordResetService) {
    this.userRepository = userRepository;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
    this.passwordEncoder = passwordEncoder;
    this.emailVerificationService = emailVerificationService;
    this.emailService = emailService;
    this.passwordResetService = passwordResetService;
  }

  public AuthTokenPair register(String email, String password, String displayName) {
    if (userRepository.existsByEmail(email)) {
      throw new UserAlreadyExistsException(email);
    }
    String hash = passwordEncoder.encode(password);
    User user = User.register(email, hash, displayName);
    userRepository.save(user);

    String token = emailVerificationService.createVerificationToken(user.id());
    emailService.sendVerificationEmail(email, displayName, token);

    return generateTokenPair(user);
  }

  public AuthTokenPair login(String email, String password) {
    User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(password, user.passwordHash())) {
      throw new InvalidCredentialsException();
    }
    if (!user.emailVerified()) {
      throw new EmailNotVerifiedException();
    }
    return generateTokenPair(user);
  }

  public AuthTokenPair refresh(String rawRefreshToken) {
    String newRawToken = UUID.randomUUID() + UUID.randomUUID().toString();
    Optional<UserId> userIdOpt =
        refreshTokenService.rotateRefreshToken(rawRefreshToken, newRawToken);

    UserId userId =
        userIdOpt.orElseThrow(
            () -> new InvalidCredentialsException("Invalid or expired refresh token"));

    User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);

    String newRefreshToken = refreshTokenService.createRefreshToken(userId);
    return new AuthTokenPair(jwtService.generateAccessToken(user), newRefreshToken);
  }

  public void logout(UserId userId) {
    refreshTokenService.revokeAllForUser(userId);
  }

  public void verifyEmail(String token) {
    emailVerificationService.verifyEmail(token);
  }

  public void resendVerificationEmail(String email) {
    User user =
        userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException(email));
    if (user.emailVerified()) return;
    String token = emailVerificationService.createVerificationToken(user.id());
    emailService.sendVerificationEmail(email, user.displayName(), token);
  }

  public void requestPasswordReset(String email) {
    userRepository
        .findByEmail(email)
        .ifPresent(
            user -> {
              String token = passwordResetService.createResetToken(user.id());
              emailService.sendPasswordResetEmail(email, user.displayName(), token);
            });
  }

  public void validateResetToken(String token) {
    passwordResetService.validateToken(token);
  }

  public void resetPassword(String token, String newPassword) {
    passwordResetService.resetPassword(token, newPassword);
  }

  private AuthTokenPair generateTokenPair(User user) {
    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = refreshTokenService.createRefreshToken(user.id());
    return new AuthTokenPair(accessToken, refreshToken);
  }
}
