package com.equily.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.equily.identity.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("local")
class UserRepositoryAdapterTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private UserJpaRepository jpaRepository;

  private UserRepositoryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new UserRepositoryAdapter(jpaRepository);
  }

  @Test
  void save_and_findByEmail_roundtrip() {
    User user = User.register("it@test.com", "hash", "IT User");
    adapter.save(user);

    Optional<User> found = adapter.findByEmail("it@test.com");
    assertThat(found).isPresent();
    assertThat(found.get().email()).isEqualTo("it@test.com");
    assertThat(found.get().displayName()).isEqualTo("IT User");
  }

  @Test
  void findById_returns_user_when_exists() {
    User user = User.register("findbyid@test.com", "hash", "Find Me");
    adapter.save(user);

    Optional<User> found = adapter.findById(user.id());
    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(user.id());
  }

  @Test
  void existsByEmail_returns_true_when_exists() {
    User user = User.register("exists@test.com", "hash", "Exists");
    adapter.save(user);

    assertThat(adapter.existsByEmail("exists@test.com")).isTrue();
  }

  @Test
  void existsByEmail_returns_false_when_not_exists() {
    assertThat(adapter.existsByEmail("nobody@test.com")).isFalse();
  }
}
