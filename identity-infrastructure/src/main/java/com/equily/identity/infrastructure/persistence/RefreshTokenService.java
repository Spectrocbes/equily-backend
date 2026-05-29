package com.equily.identity.infrastructure.persistence;

import com.equily.identity.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RefreshTokenService {

  private final RefreshTokenJpaRepository jpa;
  private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

  public RefreshTokenService(RefreshTokenJpaRepository jpa) {
    this.jpa = jpa;
  }

  public String createRefreshToken(UserId userId) {
    String rawToken = UUID.randomUUID().toString() + UUID.randomUUID().toString();
    String hash = sha256(rawToken);

    RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
    entity.setId(UUID.randomUUID());
    entity.setUserId(userId.value());
    entity.setTokenHash(hash);
    entity.setExpiresAt(Instant.now().plus(REFRESH_TOKEN_TTL));
    entity.setCreatedAt(Instant.now());
    jpa.save(entity);

    return rawToken;
  }

  public Optional<UserId> rotateRefreshToken(String rawToken, String newRawToken) {
    String hash = sha256(rawToken);
    Optional<RefreshTokenJpaEntity> opt = jpa.findByTokenHashAndRevokedAtIsNull(hash);

    if (opt.isEmpty()) return Optional.empty();
    RefreshTokenJpaEntity entity = opt.get();

    if (entity.getExpiresAt().isBefore(Instant.now())) {
      return Optional.empty();
    }

    entity.setRevokedAt(Instant.now());
    jpa.save(entity);

    return Optional.of(new UserId(entity.getUserId()));
  }

  public void revokeAllForUser(UserId userId) {
    jpa.revokeAllByUserId(userId.value(), Instant.now());
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
