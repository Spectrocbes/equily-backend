package com.equily.portfolio.web;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.application.Period;
import com.equily.portfolio.application.PortfolioAnalyticsUseCase;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.analytics.GeographicExposure;
import com.equily.portfolio.domain.analytics.PortfolioHistoryPoint;
import com.equily.portfolio.domain.analytics.TopPerformer;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/analytics")
class PortfolioAnalyticsController {

  private final PortfolioAnalyticsUseCase analyticsUseCase;

  PortfolioAnalyticsController(PortfolioAnalyticsUseCase analyticsUseCase) {
    this.analyticsUseCase = analyticsUseCase;
  }

  @GetMapping("/history")
  ResponseEntity<List<PortfolioHistoryPointResponse>> getHistory(
      @RequestParam(defaultValue = "ONE_MONTH") String period,
      @RequestParam(defaultValue = "EUR") String currency,
      @RequestParam(required = false) String accountType,
      Authentication auth) {
    UserId userId = extractUserId(auth);
    Period p = Period.valueOf(period);
    List<PortfolioHistoryPoint> points =
        accountType != null
            ? analyticsUseCase.getPortfolioHistoryByType(userId, accountType, p, currency)
            : analyticsUseCase.getPortfolioHistory(userId, p, currency);
    return ResponseEntity.ok(points.stream().map(this::toResponse).toList());
  }

  @GetMapping("/accounts/{id}/history")
  ResponseEntity<List<PortfolioHistoryPointResponse>> getAccountHistory(
      @PathVariable String id,
      @RequestParam(defaultValue = "ONE_MONTH") String period,
      @RequestParam(defaultValue = "EUR") String currency,
      Authentication auth) {
    UserId userId = extractUserId(auth);
    Period p = Period.valueOf(period);
    List<PortfolioHistoryPoint> points =
        analyticsUseCase.getAccountHistory(
            new FinancialAccountId(UUID.fromString(id)), userId, p, currency);
    return ResponseEntity.ok(points.stream().map(this::toResponse).toList());
  }

  @GetMapping("/accounts/{id}/geographic-exposure")
  ResponseEntity<List<GeographicExposureResponse>> getGeographicExposure(
      @PathVariable String id,
      @RequestParam(defaultValue = "EUR") String currency,
      Authentication auth) {
    UserId userId = extractUserId(auth);
    List<GeographicExposure> exposure =
        analyticsUseCase.getGeographicExposure(
            new FinancialAccountId(UUID.fromString(id)), userId, currency);
    return ResponseEntity.ok(exposure.stream().map(this::toResponse).toList());
  }

  @GetMapping("/top-performers")
  ResponseEntity<List<TopPerformerResponse>> getTopPerformers(
      @RequestParam(defaultValue = "EUR") String currency,
      @RequestParam(defaultValue = "5") int limit,
      Authentication auth) {
    UserId userId = extractUserId(auth);
    List<TopPerformer> performers = analyticsUseCase.getTopPerformers(userId, currency, limit);
    return ResponseEntity.ok(performers.stream().map(this::toResponse).toList());
  }

  private UserId extractUserId(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof UserId userId)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return userId;
  }

  private PortfolioHistoryPointResponse toResponse(PortfolioHistoryPoint p) {
    return new PortfolioHistoryPointResponse(
        p.date().toString(), p.totalValue(), p.invested(), p.pnl());
  }

  private GeographicExposureResponse toResponse(GeographicExposure e) {
    return new GeographicExposureResponse(e.region(), e.value(), e.weight());
  }

  private TopPerformerResponse toResponse(TopPerformer t) {
    return new TopPerformerResponse(
        t.ticker(),
        t.accountName(),
        t.currentValue(),
        t.totalInvested(),
        t.pnl(),
        t.pnlPercent(),
        t.dayChangePercent());
  }
}
