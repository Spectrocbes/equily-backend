package com.equily.portfolio.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.equily.identity.domain.UserId;
import com.equily.identity.domain.UserPreferences;
import com.equily.identity.domain.UserPreferencesUseCase;
import com.equily.portfolio.domain.marketdata.FxRatePort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserPreferencesController.class)
@Import(TestSecurityConfig.class)
class UserPreferencesControllerTest {

  @MockitoBean private UserPreferencesUseCase useCase;
  @MockitoBean private FxRatePort fxRatePort;

  @Autowired private MockMvc mockMvc;

  private Authentication mockAuth() {
    UserId userId = UserId.generate();
    return new UsernamePasswordAuthenticationToken(userId, null, List.of());
  }

  @Test
  void getPreferences_returns_200_with_currency_and_supportedCurrencies() throws Exception {
    UserId userId = UserId.generate();
    UserPreferences prefs = new UserPreferences(userId, "EUR", "fr");
    when(useCase.getPreferences(any())).thenReturn(prefs);

    mockMvc
        .perform(get("/api/v1/preferences").with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.locale").value("fr"))
        .andExpect(jsonPath("$.supportedCurrencies").isArray())
        .andExpect(jsonPath("$.supportedCurrencies.length()").value(4));
  }

  @Test
  void updatePreferences_with_valid_currency_returns_200() throws Exception {
    UserId userId = UserId.generate();
    UserPreferences updated = new UserPreferences(userId, "USD", "en");
    when(useCase.updatePreferences(any(), eq("USD"), eq("en"))).thenReturn(updated);

    mockMvc
        .perform(
            put("/api/v1/preferences")
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"USD\",\"locale\":\"en\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.locale").value("en"));
  }

  @Test
  void updatePreferences_with_invalid_currency_returns_400() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/preferences")
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"JPY\",\"locale\":\"ja\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updatePreferences_with_blank_currency_returns_400() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/preferences")
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"\",\"locale\":\"fr\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updatePreferences_with_blank_locale_returns_400() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/preferences")
                .with(authentication(mockAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"EUR\",\"locale\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getPreferences_includes_eur_to_usd_rate() throws Exception {
    UserId userId = UserId.generate();
    UserPreferences prefs = new UserPreferences(userId, "USD", "en");
    when(useCase.getPreferences(any())).thenReturn(prefs);
    when(fxRatePort.getRate("EUR", "USD")).thenReturn(Optional.of(new BigDecimal("1.08")));

    mockMvc
        .perform(get("/api/v1/preferences").with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.eurToTargetRate").value(1.08));
  }

  @Test
  void getPreferences_returns_rate_1_for_eur() throws Exception {
    UserId userId = UserId.generate();
    UserPreferences prefs = new UserPreferences(userId, "EUR", "fr");
    when(useCase.getPreferences(any())).thenReturn(prefs);

    mockMvc
        .perform(get("/api/v1/preferences").with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.eurToTargetRate").value(1));
  }
}
