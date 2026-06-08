package com.equily.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "user_preferences", schema = "identity")
class UserPreferencesJpaEntity implements Persistable<UUID> {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "locale", nullable = false, length = 5)
  private String locale;

  @Transient private boolean isNew = true;

  protected UserPreferencesJpaEntity() {}

  UserPreferencesJpaEntity(UUID userId, String currency, String locale) {
    this.userId = userId;
    this.currency = currency;
    this.locale = locale;
  }

  @Override
  public UUID getId() {
    return userId;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    this.isNew = false;
  }

  void markAsExisting() {
    this.isNew = false;
  }

  UUID getUserId() {
    return userId;
  }

  String getCurrency() {
    return currency;
  }

  String getLocale() {
    return locale;
  }
}
