package com.equily.portfolio.web.auth;

import jakarta.validation.constraints.NotBlank;

public record ValidateResetTokenRequest(@NotBlank String token) {}
