package com.equily.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.equily.identity.domain.UserId;
import com.equily.identity.domain.exception.InvalidTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EmailVerificationService.class)
@Testcontainers
@ActiveProfiles("local")
class EmailVerificationServiceTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private EmailVerificationService emailVerificationService;
  @Autowired private UserJpaRepository userJpaRepository;
  @Autowired private EmailVerificationTokenJpaRepository tokenJpaRepository;

  private UserId savedUserId;

  @BeforeEach
  void setUp() {
    UUID id = UUID.randomUUID();
    UserJpaEntity user =
        new UserJpaEntity(id, "verify@test.com", "hash", "Verify User", false, Instant.now());
    userJpaRepository.save(user);
    savedUserId = new UserId(id);
  }

  @Test
  void createVerificationToken_returns_non_blank_token() {
    String rawToken = emailVerificationService.createVerificationToken(savedUserId);
    assertThat(rawToken).isNotBlank();
  }

  @Test
  void verifyEmail_marks_user_as_verified() {
    String rawToken = emailVerificationService.createVerificationToken(savedUserId);

    emailVerificationService.verifyEmail(rawToken);

    UserJpaEntity user = userJpaRepository.findById(savedUserId.value()).orElseThrow();
    assertThat(user.isEmailVerified()).isTrue();
  }

  @Test
  void verifyEmail_throws_for_unknown_token() {
    assertThatThrownBy(() -> emailVerificationService.verifyEmail("unknown-token"))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void verifyEmail_throws_for_expired_token() {
    String rawToken = emailVerificationService.createVerificationToken(savedUserId);
    String hash = sha256(rawToken);
    EmailVerificationTokenJpaEntity entity =
        tokenJpaRepository.findByTokenHashAndUsedAtIsNull(hash).orElseThrow();
    entity.setExpiresAt(Instant.now().minus(Duration.ofHours(1)));
    tokenJpaRepository.save(entity);

    assertThatThrownBy(() -> emailVerificationService.verifyEmail(rawToken))
        .isInstanceOf(InvalidTokenException.class);
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
