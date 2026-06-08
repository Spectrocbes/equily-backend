package com.equily.portfolio.web;

import java.math.BigDecimal;
import java.util.List;

public record UserPreferencesResponse(
    String currency, String locale, List<String> supportedCurrencies, BigDecimal eurToTargetRate) {}
