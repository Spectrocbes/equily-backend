package com.equily.identity.infrastructure.usecase;

import com.equily.identity.domain.UserId;
import com.equily.identity.domain.UserPreferences;
import com.equily.identity.domain.UserPreferencesRepository;
import com.equily.identity.domain.UserPreferencesUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserPreferencesService implements UserPreferencesUseCase {

  private final UserPreferencesRepository repository;

  public UserPreferencesService(UserPreferencesRepository repository) {
    this.repository = repository;
  }

  @Override
  public UserPreferences getPreferences(UserId userId) {
    return repository.findByUserId(userId).orElse(UserPreferences.defaultFor(userId));
  }

  @Override
  public UserPreferences updatePreferences(UserId userId, String currency, String locale) {
    UserPreferences prefs = new UserPreferences(userId, currency, locale);
    repository.save(prefs);
    return prefs;
  }
}
