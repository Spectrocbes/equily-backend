package com.equily.identity.infrastructure.usecase;

public record AuthTokenPair(String accessToken, String refreshToken) {}
