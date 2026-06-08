package com.equily.identity.domain;

import java.util.Optional;

public interface UserPreferencesRepository {
  Optional<UserPreferences> findByUserId(UserId userId);

  void save(UserPreferences preferences);
}
