package com.equily.portfolio.domain;

import com.equily.shared.Country;

public record AssetMetadata(String fullName, String isin, Country country) {

    public AssetMetadata {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be null or blank");
        }
        if (country == null) {
            throw new IllegalArgumentException("country must not be null");
        }
        // isin is nullable — crypto assets have none
    }
}
