package com.equily.identity.infrastructure.persistence;

import com.equily.identity.domain.UserId;
import com.equily.identity.domain.exception.InvalidTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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

    String rawToken =
        UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
    String hash = sha256(rawToken);

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
    String hash = sha256(rawToken);
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

  private String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
