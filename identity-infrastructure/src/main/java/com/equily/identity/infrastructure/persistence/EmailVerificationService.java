package com.equily.identity.infrastructure.persistence;

import com.equily.identity.domain.UserId;
import com.equily.identity.domain.exception.InvalidTokenException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmailVerificationService {

  private final EmailVerificationTokenJpaRepository tokenRepo;
  private final UserJpaRepository userRepo;
  private static final Duration TOKEN_TTL = Duration.ofHours(24);

  public EmailVerificationService(
      EmailVerificationTokenJpaRepository tokenRepo, UserJpaRepository userRepo) {
    this.tokenRepo = tokenRepo;
    this.userRepo = userRepo;
  }

  public String createVerificationToken(UserId userId) {
    tokenRepo.deleteAllByUserId(userId.value());

    String rawToken = TokenHashUtils.generateRawToken();
    String hash = TokenHashUtils.sha256(rawToken);

    EmailVerificationTokenJpaEntity entity = new EmailVerificationTokenJpaEntity();
    entity.setId(UUID.randomUUID());
    entity.setUserId(userId.value());
    entity.setTokenHash(hash);
    entity.setExpiresAt(Instant.now().plus(TOKEN_TTL));
    entity.setCreatedAt(Instant.now());
    tokenRepo.save(entity);

    return rawToken;
  }

  public UserId verifyEmail(String rawToken) {
    String hash = TokenHashUtils.sha256(rawToken);
    EmailVerificationTokenJpaEntity entity =
        tokenRepo
            .findByTokenHashAndUsedAtIsNull(hash)
            .orElseThrow(
                () -> new InvalidTokenException("Invalid or already used verification token"));

    if (entity.getExpiresAt().isBefore(Instant.now())) {
      throw new InvalidTokenException("Verification token has expired");
    }

    entity.setUsedAt(Instant.now());
    tokenRepo.save(entity);

    userRepo
        .findById(entity.getUserId())
        .ifPresent(
            u -> {
              u.setEmailVerified(true);
              userRepo.save(u);
            });

    return new UserId(entity.getUserId());
  }
}
