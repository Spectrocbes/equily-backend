package com.equily.identity.infrastructure.persistence;

import com.equily.identity.domain.UserId;
import com.equily.identity.domain.UserPreferences;
import com.equily.identity.domain.UserPreferencesRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class UserPreferencesRepositoryAdapter implements UserPreferencesRepository {

  private final UserPreferencesJpaRepository jpa;

  UserPreferencesRepositoryAdapter(UserPreferencesJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<UserPreferences> findByUserId(UserId userId) {
    return jpa.findById(userId.value()).map(this::toDomain);
  }

  @Override
  public void save(UserPreferences preferences) {
    UserPreferencesJpaEntity entity = toJpa(preferences);
    if (jpa.existsById(preferences.userId().value())) {
      entity.markAsExisting();
    }
    jpa.save(entity);
  }

  private UserPreferencesJpaEntity toJpa(UserPreferences preferences) {
    return new UserPreferencesJpaEntity(
        preferences.userId().value(), preferences.currency(), preferences.locale());
  }

  private UserPreferences toDomain(UserPreferencesJpaEntity entity) {
    return new UserPreferences(
        new UserId(entity.getUserId()), entity.getCurrency(), entity.getLocale());
  }
}
