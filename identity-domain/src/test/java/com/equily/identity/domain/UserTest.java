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
  void register_sets_emailVerified_false() {
    User user = User.register("alice@example.com", "hash", "Alice");

    assertThat(user.emailVerified()).isFalse();
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

    User user = User.reconstruct(id, "alice@example.com", "stored-hash", "Alice", true, createdAt);

    assertThat(user.id()).isEqualTo(id);
    assertThat(user.email()).isEqualTo("alice@example.com");
    assertThat(user.passwordHash()).isEqualTo("stored-hash");
    assertThat(user.displayName()).isEqualTo("Alice");
    assertThat(user.emailVerified()).isTrue();
    assertThat(user.createdAt()).isEqualTo(createdAt);
  }

  @Test
  void withEmailVerified_returns_new_user_with_emailVerified_true() {
    User user = User.register("alice@example.com", "hash", "Alice");

    User verified = user.withEmailVerified();

    assertThat(verified.emailVerified()).isTrue();
    assertThat(verified.email()).isEqualTo("alice@example.com");
    assertThat(verified.id()).isEqualTo(user.id());
  }

  @Test
  void withNewPassword_returns_new_user_with_updated_hash() {
    User user = User.register("alice@example.com", "oldhash", "Alice");

    User updated = user.withNewPassword("newhash");

    assertThat(updated.passwordHash()).isEqualTo("newhash");
    assertThat(updated.email()).isEqualTo("alice@example.com");
    assertThat(updated.id()).isEqualTo(user.id());
  }
}
