package com.neueda.leap.team.seed;

/** Validated reference data, not a live price and not a user's holding. */
public record SeedSecurity(String ticker, String name, String assetType, String exchange,
        String quoteCurrency, String baseCurrency, String sector, String universeId) {}
