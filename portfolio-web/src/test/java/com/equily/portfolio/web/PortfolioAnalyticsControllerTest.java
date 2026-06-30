package com.equily.portfolio.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.application.Period;
import com.equily.portfolio.application.PortfolioAnalyticsUseCase;
import com.equily.portfolio.domain.analytics.GeographicExposure;
import com.equily.portfolio.domain.analytics.PortfolioHistoryPoint;
import com.equily.portfolio.domain.analytics.TopPerformer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PortfolioAnalyticsController.class)
@Import(TestSecurityConfig.class)
class PortfolioAnalyticsControllerTest {

  @MockitoBean private PortfolioAnalyticsUseCase analyticsUseCase;

  @Autowired private MockMvc mockMvc;

  private UserId testUserId;

  @BeforeEach
  void setUp() {
    testUserId = UserId.generate();
  }

  private Authentication mockAuth() {
    return new UsernamePasswordAuthenticationToken(testUserId, null, List.of());
  }

  @Test
  void getHistory_returns_200_with_points() throws Exception {
    List<PortfolioHistoryPoint> points =
        List.of(
            new PortfolioHistoryPoint(
                LocalDate.of(2024, 6, 1),
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(9000),
                BigDecimal.valueOf(1000)));

    when(analyticsUseCase.getPortfolioHistory(any(), any(Period.class), anyString()))
        .thenReturn(points);

    mockMvc
        .perform(get("/api/v1/analytics/history").with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].date").value("2024-06-01"))
        .andExpect(jsonPath("$[0].value").value(10000))
        .andExpect(jsonPath("$[0].pnl").value(1000));
  }

  @Test
  void getHistory_accepts_period_param() throws Exception {
    when(analyticsUseCase.getPortfolioHistory(any(), eq(Period.ONE_YEAR), anyString()))
        .thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/analytics/history")
                .param("period", "ONE_YEAR")
                .with(authentication(mockAuth())))
        .andExpect(status().isOk());
  }

  @Test
  void getHistory_endpoint_accepts_accountType_param() throws Exception {
    List<PortfolioHistoryPoint> points =
        List.of(
            new PortfolioHistoryPoint(
                LocalDate.of(2024, 6, 1),
                BigDecimal.valueOf(3000),
                BigDecimal.valueOf(3000),
                BigDecimal.ZERO));

    when(analyticsUseCase.getPortfolioHistoryByType(
            any(), eq("INVESTMENT"), any(Period.class), anyString()))
        .thenReturn(points);

    mockMvc
        .perform(
            get("/api/v1/analytics/history")
                .param("accountType", "INVESTMENT")
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].value").value(3000));
  }

  @Test
  void getHistory_returns_400_for_invalid_period() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/analytics/history")
                .param("period", "INVALID_PERIOD")
                .with(authentication(mockAuth())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getGeographicExposure_returns_200() throws Exception {
    UUID accountId = UUID.randomUUID();
    List<GeographicExposure> exposure =
        List.of(
            new GeographicExposure(
                "United States", BigDecimal.valueOf(5000), BigDecimal.valueOf(60)),
            new GeographicExposure("France", BigDecimal.valueOf(3333), BigDecimal.valueOf(40)));

    when(analyticsUseCase.getGeographicExposure(any(), any(), anyString())).thenReturn(exposure);

    mockMvc
        .perform(
            get("/api/v1/analytics/accounts/{id}/geographic-exposure", accountId)
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].region").value("United States"));
  }

  @Test
  void getAccountHistory_endpoint_returns_200() throws Exception {
    UUID accountId = UUID.randomUUID();
    List<PortfolioHistoryPoint> points =
        List.of(
            new PortfolioHistoryPoint(
                LocalDate.of(2024, 6, 1),
                BigDecimal.valueOf(5000),
                BigDecimal.valueOf(4500),
                BigDecimal.valueOf(500)));

    when(analyticsUseCase.getAccountHistory(any(), any(), any(Period.class), anyString()))
        .thenReturn(points);

    mockMvc
        .perform(
            get("/api/v1/analytics/accounts/{id}/history", accountId)
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].date").value("2024-06-01"))
        .andExpect(jsonPath("$[0].value").value(5000))
        .andExpect(jsonPath("$[0].pnl").value(500));
  }

  @Test
  void getHistory_returns_401_when_unauthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/analytics/history")).andExpect(status().isUnauthorized());
  }

  @Test
  void getTopPerformers_returns_200_with_limit() throws Exception {
    List<TopPerformer> performers =
        List.of(
            new TopPerformer(
                "AAPL",
                "My PEA",
                BigDecimal.valueOf(2000),
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(1.5)));

    when(analyticsUseCase.getTopPerformers(any(), anyString(), anyInt())).thenReturn(performers);

    mockMvc
        .perform(
            get("/api/v1/analytics/top-performers")
                .param("limit", "3")
                .with(authentication(mockAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].ticker").value("AAPL"))
        .andExpect(jsonPath("$[0].pnlPercent").value(100));
  }
}
