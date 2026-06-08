package com.equily.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserPreferencesTest {

  @Test
  void defaultFor_creates_eur_fr_preferences() {
    UserId userId = UserId.generate();

    UserPreferences prefs = UserPreferences.defaultFor(userId);

    assertThat(prefs.userId()).isEqualTo(userId);
    assertThat(prefs.currency()).isEqualTo("EUR");
    assertThat(prefs.locale()).isEqualTo("fr");
  }

  @Test
  void constructor_accepts_all_supported_currencies() {
    UserId userId = UserId.generate();

    for (String currency : UserPreferences.SUPPORTED_CURRENCIES) {
      UserPreferences prefs = new UserPreferences(userId, currency, "en");
      assertThat(prefs.currency()).isEqualTo(currency);
    }
  }

  @Test
  void constructor_rejects_unsupported_currency() {
    UserId userId = UserId.generate();

    assertThatThrownBy(() -> new UserPreferences(userId, "JPY", "fr"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported currency: JPY");
  }

  @Test
  void constructor_rejects_null_userId() {
    assertThatThrownBy(() -> new UserPreferences(null, "EUR", "fr"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("userId must not be null");
  }

  @Test
  void constructor_rejects_null_locale() {
    UserId userId = UserId.generate();

    assertThatThrownBy(() -> new UserPreferences(userId, "EUR", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("locale must not be null");
  }

  @Test
  void supported_currencies_contains_four_entries() {
    assertThat(UserPreferences.SUPPORTED_CURRENCIES)
        .containsExactlyInAnyOrder("EUR", "USD", "GBP", "CHF");
  }
}
