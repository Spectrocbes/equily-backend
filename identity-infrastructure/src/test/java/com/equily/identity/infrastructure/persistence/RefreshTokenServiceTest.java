package com.equily.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.equily.identity.domain.UserId;
import java.time.Instant;
import java.util.Optional;
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
@Import(RefreshTokenService.class)
@Testcontainers
@ActiveProfiles("local")
class RefreshTokenServiceTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private RefreshTokenService refreshTokenService;
  @Autowired private UserJpaRepository userJpaRepository;

  private UserId savedUserId;

  @BeforeEach
  void setUp() {
    UUID id = UUID.randomUUID();
    UserJpaEntity user =
        new UserJpaEntity(id, "token-user@test.com", "hash", "Token User", true, Instant.now());
    userJpaRepository.save(user);
    savedUserId = new UserId(id);
  }

  @Test
  void createRefreshToken_returns_non_blank_raw_token() {
    String rawToken = refreshTokenService.createRefreshToken(savedUserId);
    assertThat(rawToken).isNotBlank();
  }

  @Test
  void rotateRefreshToken_returns_userId_for_valid_token() {
    String rawToken = refreshTokenService.createRefreshToken(savedUserId);

    Optional<UserId> result =
        refreshTokenService.rotateRefreshToken(rawToken, UUID.randomUUID().toString());

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(savedUserId);
  }

  @Test
  void rotateRefreshToken_returns_empty_for_unknown_token() {
    Optional<UserId> result =
        refreshTokenService.rotateRefreshToken("unknown-token", UUID.randomUUID().toString());

    assertThat(result).isEmpty();
  }

  @Test
  void revokeAllForUser_completes_without_error() {
    refreshTokenService.createRefreshToken(savedUserId);
    refreshTokenService.createRefreshToken(savedUserId);

    refreshTokenService.revokeAllForUser(savedUserId);
  }
}
