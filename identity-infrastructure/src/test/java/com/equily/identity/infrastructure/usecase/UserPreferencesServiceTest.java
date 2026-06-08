package com.equily.identity.infrastructure.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.equily.identity.domain.UserId;
import com.equily.identity.domain.UserPreferences;
import com.equily.identity.domain.UserPreferencesRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

  @Mock private UserPreferencesRepository repository;
  @InjectMocks private UserPreferencesService service;

  @Test
  void getPreferences_returns_saved_preferences() {
    UserId userId = UserId.generate();
    UserPreferences saved = new UserPreferences(userId, "USD", "en");
    when(repository.findByUserId(userId)).thenReturn(Optional.of(saved));

    UserPreferences result = service.getPreferences(userId);

    assertThat(result.currency()).isEqualTo("USD");
    assertThat(result.locale()).isEqualTo("en");
  }

  @Test
  void getPreferences_returns_default_when_not_found() {
    UserId userId = UserId.generate();
    when(repository.findByUserId(userId)).thenReturn(Optional.empty());

    UserPreferences result = service.getPreferences(userId);

    assertThat(result.currency()).isEqualTo(UserPreferences.DEFAULT_CURRENCY);
    assertThat(result.locale()).isEqualTo(UserPreferences.DEFAULT_LOCALE);
    assertThat(result.userId()).isEqualTo(userId);
  }

  @Test
  void updatePreferences_saves_and_returns_new_preferences() {
    UserId userId = UserId.generate();

    UserPreferences result = service.updatePreferences(userId, "GBP", "en");

    verify(repository).save(any(UserPreferences.class));
    assertThat(result.currency()).isEqualTo("GBP");
    assertThat(result.locale()).isEqualTo("en");
    assertThat(result.userId()).isEqualTo(userId);
  }
}
