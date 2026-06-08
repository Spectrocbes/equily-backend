package com.equily.identity.domain;

public interface UserPreferencesUseCase {
  UserPreferences getPreferences(UserId userId);

  UserPreferences updatePreferences(UserId userId, String currency, String locale);
}
