package com.equily.identity.infrastructure.persistence;

import com.equily.identity.domain.UserId;
import java.time.Duration;
import java.time.Instant;
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
    String rawToken = TokenHashUtils.generateRawToken();
    String hash = TokenHashUtils.sha256(rawToken);

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
    String hash = TokenHashUtils.sha256(rawToken);
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
}
