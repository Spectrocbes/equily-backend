package com.equily.portfolio.web;

import com.equily.identity.domain.UserId;
import com.equily.identity.domain.UserPreferences;
import com.equily.identity.domain.UserPreferencesUseCase;
import com.equily.portfolio.domain.marketdata.FxRatePort;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences")
class UserPreferencesController {

  private final UserPreferencesUseCase useCase;
  private final FxRatePort fxRatePort;

  UserPreferencesController(UserPreferencesUseCase useCase, FxRatePort fxRatePort) {
    this.useCase = useCase;
    this.fxRatePort = fxRatePort;
  }

  @GetMapping
  ResponseEntity<UserPreferencesResponse> getPreferences(Authentication authentication) {
    UserId userId = (UserId) authentication.getPrincipal();
    UserPreferences prefs = useCase.getPreferences(userId);
    return ResponseEntity.ok(toResponse(prefs));
  }

  @PutMapping
  ResponseEntity<UserPreferencesResponse> updatePreferences(
      @RequestBody @Valid UpdatePreferencesRequest request, Authentication authentication) {
    UserId userId = (UserId) authentication.getPrincipal();
    UserPreferences prefs = useCase.updatePreferences(userId, request.currency(), request.locale());
    return ResponseEntity.ok(toResponse(prefs));
  }

  private UserPreferencesResponse toResponse(UserPreferences prefs) {
    BigDecimal eurToTarget =
        prefs.currency().equals("EUR")
            ? BigDecimal.ONE
            : fxRatePort.getRate("EUR", prefs.currency()).orElse(BigDecimal.ONE);
    List<String> supported = new ArrayList<>(UserPreferences.SUPPORTED_CURRENCIES);
    supported.sort(null);
    return new UserPreferencesResponse(prefs.currency(), prefs.locale(), supported, eurToTarget);
  }
}
