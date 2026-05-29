package com.equily.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.equily.identity.domain.User;
import com.equily.identity.domain.UserId;
import io.jsonwebtoken.Claims;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private JwtService jwtService;
  private User testUser;

  @BeforeEach
  void setUp() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair kp = kpg.generateKeyPair();
    jwtService = new JwtService(kp.getPrivate(), kp.getPublic());
    testUser = User.register("test@example.com", "hashedPassword", "Test User");
  }

  @Test
  void generateAccessToken_returns_three_part_jwt() {
    String token = jwtService.generateAccessToken(testUser);
    assertThat(token).isNotBlank();
    assertThat(token.split("\\.")).hasSize(3);
  }

  @Test
  void parseToken_extracts_correct_subject() {
    String token = jwtService.generateAccessToken(testUser);
    Claims claims = jwtService.parseToken(token);
    assertThat(claims.getSubject()).isEqualTo(testUser.id().value().toString());
  }

  @Test
  void parseToken_extracts_email_claim() {
    String token = jwtService.generateAccessToken(testUser);
    Claims claims = jwtService.parseToken(token);
    assertThat(claims.get("email", String.class)).isEqualTo("test@example.com");
  }

  @Test
  void isTokenValid_returns_true_for_valid_token() {
    String token = jwtService.generateAccessToken(testUser);
    assertThat(jwtService.isTokenValid(token)).isTrue();
  }

  @Test
  void isTokenValid_returns_false_for_garbage() {
    assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
  }

  @Test
  void extractUserId_returns_correct_id() {
    String token = jwtService.generateAccessToken(testUser);
    UserId extracted = jwtService.extractUserId(token);
    assertThat(extracted).isEqualTo(testUser.id());
  }
}
