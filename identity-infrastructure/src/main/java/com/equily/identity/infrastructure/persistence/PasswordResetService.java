package com.equily.identity.infrastructure.persistence;

import com.equily.identity.domain.UserId;
import com.equily.identity.domain.exception.InvalidTokenException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordResetService {

  private final PasswordResetTokenJpaRepository tokenRepo;
  private final UserJpaRepository userRepo;
  private final PasswordEncoder passwordEncoder;
  private static final Duration TOKEN_TTL = Duration.ofHours(1);

  public PasswordResetService(
      PasswordResetTokenJpaRepository tokenRepo,
      UserJpaRepository userRepo,
      PasswordEncoder passwordEncoder) {
    this.tokenRepo = tokenRepo;
    this.userRepo = userRepo;
    this.passwordEncoder = passwordEncoder;
  }

  public String createResetToken(UserId userId) {
    tokenRepo.deleteAllByUserId(userId.value());

    String rawToken = TokenHashUtils.generateRawToken();
    String hash = TokenHashUtils.sha256(rawToken);

    PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity();
    entity.setId(UUID.randomUUID());
    entity.setUserId(userId.value());
    entity.setTokenHash(hash);
    entity.setExpiresAt(Instant.now().plus(TOKEN_TTL));
    entity.setCreatedAt(Instant.now());
    tokenRepo.save(entity);

    return rawToken;
  }

  public void resetPassword(String rawToken, String newPassword) {
    String hash = TokenHashUtils.sha256(rawToken);
    PasswordResetTokenJpaEntity entity =
        tokenRepo
            .findByTokenHashAndUsedAtIsNull(hash)
            .orElseThrow(() -> new InvalidTokenException("Invalid or already used reset token"));

    if (entity.getExpiresAt().isBefore(Instant.now())) {
      throw new InvalidTokenException("Reset token has expired");
    }

    entity.setUsedAt(Instant.now());
    tokenRepo.save(entity);

    userRepo
        .findById(entity.getUserId())
        .ifPresent(
            user -> {
              user.setPasswordHash(passwordEncoder.encode(newPassword));
              userRepo.save(user);
            });
  }
}
