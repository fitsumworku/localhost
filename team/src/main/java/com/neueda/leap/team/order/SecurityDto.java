package com.neueda.leap.team.order;

public record SecurityDto(long securityId, String ticker, String name, String assetType,
        String exchange, String quoteCurrency, String baseCurrency, String status) {}
