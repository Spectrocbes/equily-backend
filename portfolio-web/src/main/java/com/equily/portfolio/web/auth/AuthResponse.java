package com.equily.portfolio.web.auth;

public record AuthResponse(
    String accessToken, String refreshToken, String email, String displayName) {}
