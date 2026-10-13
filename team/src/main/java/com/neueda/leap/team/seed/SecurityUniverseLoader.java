package com.neueda.leap.team.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityUniverseLoader {
    private static final Pattern US = Pattern.compile("^[A-Z][A-Z0-9.-]{0,14}$");
    private static final Pattern INDIA = Pattern.compile("^[A-Z][A-Z0-9.-]{0,12}\\.(NS|BO)$");
    private static final Pattern FX = Pattern.compile("^FX:([A-Z]{3})([A-Z]{3})$");
    private static final Pattern CRYPTO = Pattern.compile("^X:([A-Z0-9]{2,10})-([A-Z]{3,5})$");
    private final JsonMapper json;
    private final ResourceLoader resources;

    public SecurityUniverseLoader(JsonMapper json, ResourceLoader resources) {
        this.json = json;
        this.resources = resources;
    }

    /** Read and validate every selected file before the service writes any rows. */
    public List<SeedSecurity> load(List<String> locations) {
        if (locations == null || locations.isEmpty()) {
            throw new IllegalArgumentException("Configure at least one securities seed resource.");
        }
        Map<String, SeedSecurity> securities = new TreeMap<>();
        for (String location : locations) {
            if (location == null || !location.startsWith("classpath:universes/")
                    || !location.endsWith(".json") || location.contains("..")) {
                throw invalid(String.valueOf(location), "Use an explicit classpath:universes/*.json resource.");
            }
            Universe universe;
            try (var input = resources.getResource(location).getInputStream()) {
                universe = json.readValue(input, Universe.class);
            } catch (IOException | RuntimeException ex) {
                throw new IllegalArgumentException("Cannot read securities universe " + location, ex);
            }
            if (universe == null || !Integer.valueOf(1).equals(universe.version())) {
                throw invalid(location, "Only universe version 1 is supported.");
            }
            String id = required(universe.id(), 255, location, "id");
            String market = required(universe.market(), 16, location, "market").toUpperCase(Locale.ROOT);
            if (!Set.of("US", "IN", "FX", "CRYPTO").contains(market)) {
                throw invalid(location, "Unsupported market " + market);
            }
            if (universe.symbols() == null || universe.symbols().isEmpty()) {
                throw invalid(location, "symbols must be a nonempty array.");
            }
            for (Symbol symbol : universe.symbols()) {
                SeedSecurity security = convert(symbol, market, id, location);
                if (securities.putIfAbsent(security.ticker(), security) != null) {
                    throw invalid(location, "Duplicate Fauxnance symbol " + security.ticker()
                            + ". Choose us-v1.json OR dev-v1.json; do not combine overlapping universes.");
                }
            }
        }
        // Consistent insertion order also avoids opposite lock ordering during concurrent startups.
        return List.copyOf(securities.values());
    }

    private SeedSecurity convert(Symbol item, String market, String universeId, String source) {
        if (item == null) throw invalid(source, "A symbol entry cannot be null.");
        String ticker = required(item.symbol(), 40, source, "symbol").toUpperCase(Locale.ROOT);
        String name = required(item.name(), 255, source, ticker + ".name");
        String exchange = required(item.exchange(), 80, source, ticker + ".exchange").toUpperCase(Locale.ROOT);
        String currency = required(item.currency(), 3, source, ticker + ".currency").toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw invalid(source, "Invalid quote currency for " + ticker);
        String assetType = switch (required(item.type(), 16, source, ticker + ".type").toLowerCase(Locale.ROOT)) {
            case "equity" -> "EQUITY";
            case "etf" -> "ETF";
            case "fx" -> "FOREX";
            case "crypto" -> "CRYPTO";
            default -> throw invalid(source, "Unsupported asset type for " + ticker);
        };
        String baseCurrency = null;
        boolean valid;
        switch (market) {
            case "US" -> valid = US.matcher(ticker).matches() && !INDIA.matcher(ticker).matches()
                    && Set.of("EQUITY", "ETF").contains(assetType) && currency.equals("USD");
            case "IN" -> valid = INDIA.matcher(ticker).matches() && assetType.equals("EQUITY")
                    && currency.equals("INR") && exchange.equals(ticker.endsWith(".NS") ? "NSE" : "BSE");
            case "FX" -> {
                var pair = FX.matcher(ticker);
                valid = pair.matches() && assetType.equals("FOREX") && exchange.equals("FX");
                if (valid) {
                    baseCurrency = pair.group(1);
                    valid = currency.equals(pair.group(2)) && !baseCurrency.equals(currency);
                }
            }
            case "CRYPTO" -> {
                var pair = CRYPTO.matcher(ticker);
                valid = pair.matches() && assetType.equals("CRYPTO") && exchange.equals("CRYPTO")
                        && currency.equals(pair.group(2));
            }
            default -> valid = false;
        }
        if (!valid) throw invalid(source, "Symbol, type, exchange or currency does not match market " + market + ": " + ticker);
        String sector = item.sector() == null || item.sector().isBlank() ? null
                : required(item.sector(), 100, source, ticker + ".sector");
        return new SeedSecurity(ticker, name, assetType, exchange, currency, baseCurrency, sector, universeId);
    }

    private static String required(String value, int max, String source, String field) {
        if (value == null || value.isBlank() || value.strip().length() > max) {
            throw invalid(source, field + " must contain 1 to " + max + " characters.");
        }
        return value.strip();
    }
    private static IllegalArgumentException invalid(String source, String message) {
        return new IllegalArgumentException("Invalid securities universe " + source + ": " + message);
    }

    // Metadata such as source/asOf and upstream adapterHints is not part of our Securities schema.
    // Ignore it only for these file DTOs; HTTP request DTOs still reject unknown fields.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Universe(Integer version, String id, String market, List<Symbol> symbols) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Symbol(String symbol, String name, String type, String exchange, String currency, String sector) {}
}
