package com.equily.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class HouseholdTest {

  @Test
  void create_sets_correct_fields() {
    UserId ownerId = UserId.generate();
    Household h = Household.create("Famille Dupont", ownerId);

    assertThat(h.name()).isEqualTo("Famille Dupont");
    assertThat(h.ownerId()).isEqualTo(ownerId);
    assertThat(h.id()).isNotNull();
    assertThat(h.createdAt()).isNotNull();
  }

  @Test
  void create_rejects_blank_name() {
    assertThatThrownBy(() -> Household.create("", UserId.generate()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void create_rejects_null_name() {
    assertThatThrownBy(() -> Household.create(null, UserId.generate()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void reconstruct_preserves_all_fields() {
    HouseholdId id = HouseholdId.generate();
    UserId ownerId = UserId.generate();
    Instant now = Instant.now();

    Household h = Household.reconstruct(id, "Test", ownerId, now);

    assertThat(h.id()).isEqualTo(id);
    assertThat(h.name()).isEqualTo("Test");
    assertThat(h.ownerId()).isEqualTo(ownerId);
    assertThat(h.createdAt()).isEqualTo(now);
  }
}
