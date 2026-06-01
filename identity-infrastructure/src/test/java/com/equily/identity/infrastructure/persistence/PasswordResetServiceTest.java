package com.equily.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PasswordResetService.class)
@Testcontainers
@ActiveProfiles("local")
class PasswordResetServiceTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @MockitoBean PasswordEncoder passwordEncoder;

  @Autowired private PasswordResetService passwordResetService;
  @Autowired private UserJpaRepository userJpaRepository;
  @Autowired private PasswordResetTokenJpaRepository tokenJpaRepository;

  private UserId savedUserId;

  @BeforeEach
  void setUp() {
    UUID id = UUID.randomUUID();
    UserJpaEntity user =
        new UserJpaEntity(id, "reset@test.com", "oldhash", "Reset User", true, Instant.now());
    userJpaRepository.save(user);
    savedUserId = new UserId(id);
  }

  @Test
  void createResetToken_returns_non_blank_token() {
    String rawToken = passwordResetService.createResetToken(savedUserId);
    assertThat(rawToken).isNotBlank();
  }

  @Test
  void resetPassword_updates_password_hash() {
    when(passwordEncoder.encode(anyString())).thenReturn("newhash");
    String rawToken = passwordResetService.createResetToken(savedUserId);

    passwordResetService.resetPassword(rawToken, "newpassword");

    UserJpaEntity user = userJpaRepository.findById(savedUserId.value()).orElseThrow();
    assertThat(user.getPasswordHash()).isEqualTo("newhash");
  }

  @Test
  void resetPassword_throws_for_unknown_token() {
    assertThatThrownBy(() -> passwordResetService.resetPassword("unknown-token", "newpassword"))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void resetPassword_throws_for_expired_token() {
    String rawToken = passwordResetService.createResetToken(savedUserId);
    String hash = sha256(rawToken);
    PasswordResetTokenJpaEntity entity =
        tokenJpaRepository.findByTokenHashAndUsedAtIsNull(hash).orElseThrow();
    entity.setExpiresAt(Instant.now().minus(Duration.ofHours(2)));
    tokenJpaRepository.save(entity);

    assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, "newpassword"))
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
