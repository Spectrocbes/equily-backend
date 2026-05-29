package com.equily.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserIdTest {

  @Test
  void generate_creates_non_null_id() {
    assertThat(UserId.generate().value()).isNotNull();
  }

  @Test
  void of_parses_valid_uuid_string() {
    String uuid = UUID.randomUUID().toString();
    assertThat(UserId.of(uuid).value().toString()).isEqualTo(uuid);
  }

  @Test
  void constructor_rejects_null() {
    assertThatThrownBy(() -> new UserId(null)).isInstanceOf(NullPointerException.class);
  }
}
