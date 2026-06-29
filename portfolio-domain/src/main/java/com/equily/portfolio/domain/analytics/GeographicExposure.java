package com.equily.portfolio.domain.analytics;

import java.math.BigDecimal;

public record GeographicExposure(String region, BigDecimal value, BigDecimal weight) {}
