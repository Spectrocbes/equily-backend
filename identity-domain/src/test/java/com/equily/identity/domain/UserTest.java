package com.equily.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void register_creates_user_with_lowercased_trimmed_email() {
    User user = User.register("  TEST@Example.COM  ", "hash", "Alice");

    assertThat(user.email()).isEqualTo("test@example.com");
    assertThat(user.displayName()).isEqualTo("Alice");
    assertThat(user.passwordHash()).isEqualTo("hash");
    assertThat(user.id()).isNotNull();
    assertThat(user.createdAt()).isNotNull();
  }

  @Test
  void register_throws_on_null_email() {
    assertThatThrownBy(() -> User.register(null, "hash", "Alice"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("email must not be null");
  }

  @Test
  void register_throws_on_blank_email() {
    assertThatThrownBy(() -> User.register("   ", "hash", "Alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("email must not be blank");
  }

  @Test
  void reconstruct_creates_user_with_all_provided_fields() {
    UserId id = UserId.generate();
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

    User user = User.reconstruct(id, "alice@example.com", "stored-hash", "Alice", createdAt);

    assertThat(user.id()).isEqualTo(id);
    assertThat(user.email()).isEqualTo("alice@example.com");
    assertThat(user.passwordHash()).isEqualTo("stored-hash");
    assertThat(user.displayName()).isEqualTo("Alice");
    assertThat(user.createdAt()).isEqualTo(createdAt);
  }
}
