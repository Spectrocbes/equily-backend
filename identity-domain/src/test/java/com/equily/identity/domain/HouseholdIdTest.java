package com.equily.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HouseholdIdTest {

  @Test
  void generate_creates_non_null_id() {
    assertThat(HouseholdId.generate().value()).isNotNull();
  }

  @Test
  void constructor_rejects_null() {
    assertThatThrownBy(() -> new HouseholdId(null)).isInstanceOf(NullPointerException.class);
  }
}
