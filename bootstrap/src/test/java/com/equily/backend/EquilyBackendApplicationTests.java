package com.equily.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, TestJwtConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class EquilyBackendApplicationTests {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void contextLoads() {}

  @Test
  void unauthenticated_request_returns_401() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/accounts", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
