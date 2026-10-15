package com.neueda.leap.team.market;

import com.neueda.leap.team.config.FauxnanceProperties;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Market data only: Fauxnance does not place trades. Never log its key or raw error bodies. */
@Component
public class FauxnanceClient implements MarketDataClient {
    private final FauxnanceProperties properties;
    private final JsonMapper json;
    private final Clock clock;
    private final HttpClient http;

    public FauxnanceClient(FauxnanceProperties properties, JsonMapper json, Clock clock) {
        this.properties = properties; this.json = json.rebuild().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build(); this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public Map<String, QuoteResult> quotes(Set<String> symbols) {
        Map<String, QuoteResult> result = new HashMap<>();
        List<String> all = new ArrayList<>(symbols);
        for (int start=0; start<all.size(); start+=25) {
            List<String> batch = all.subList(start, Math.min(start+25, all.size()));
            if (properties.apiKey().isBlank()) {
                batch.forEach(s -> result.put(s, QuoteResult.error("MARKET_DATA_NOT_CONFIGURED", 300)));
                continue;
            }
            try {
                String joined = URLEncoder.encode(String.join(",", batch), StandardCharsets.UTF_8);
                URI uri = URI.create(properties.baseUrl().replaceAll("/+$", "") + "/quotes?symbols=" + joined);
                var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                        .header("X-Api-Key", properties.apiKey()).header("Accept", "application/json").GET().build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    String code = switch (response.statusCode()) {
                        case 202 -> "BACKFILL_IN_PROGRESS";
                        case 401, 403 -> "MARKET_DATA_AUTH_FAILED";
                        case 429 -> "RATE_LIMITED";
                        // Request-level errors do not prove an individual symbol is untradeable.
                        default -> "MARKET_DATA_UNAVAILABLE";
                    };
                    long retry = retryAfter(response.headers().firstValue("Retry-After").orElse(null));
                    if (response.statusCode() == 401 || response.statusCode() == 403) retry = Math.max(300, retry);
                    long delay = retry;
                    batch.forEach(s -> result.put(s, QuoteResult.error(code, delay)));
                    continue;
                }
                JsonNode entries = json.readTree(response.body()).path("data").path("quotes");
                if (entries.isArray()) for (JsonNode item : entries) {
                    String symbol = item.path("symbol").asText("");
                    if (!batch.contains(symbol)) continue;
                    if (result.containsKey(symbol)) {
                        result.put(symbol, QuoteResult.error("INVALID_QUOTE_RESPONSE", 60));
                        continue;
                    }
                    if (item.has("error")) {
                        String code = item.path("error").path("code").asText("");
                        // Only documented, non-sensitive codes escape the adapter.
                        if (!Set.of("SYMBOL_NOT_FOUND", "BACKFILL_IN_PROGRESS", "RATE_LIMITED").contains(code))
                            code = "MARKET_DATA_UNAVAILABLE";
                        result.put(symbol, QuoteResult.error(code, 60));
                    } else {
                        result.put(symbol, parseQuote(symbol, item));
                    }
                }
                batch.forEach(s -> result.putIfAbsent(s, QuoteResult.error("INVALID_QUOTE_RESPONSE", 60)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                batch.forEach(s -> result.put(s, QuoteResult.error("MARKET_DATA_INTERRUPTED", 60)));
                break;
            } catch (Exception e) {
                batch.forEach(s -> result.put(s, QuoteResult.error("MARKET_DATA_UNAVAILABLE", 60)));
            }
        }
        all.forEach(s -> result.putIfAbsent(s, QuoteResult.error("MARKET_DATA_UNAVAILABLE", 60)));
        return Map.copyOf(result);
    }

    private QuoteResult parseQuote(String symbol, JsonNode item) {
        try {
            JsonNode q = item.path("quote");
            if (!q.path("price").isNumber() || !item.path("stale").isBoolean()) throw new IllegalArgumentException();
            var price = q.path("price").decimalValue();
            String currency = q.path("currency").asText("");
            String source = item.path("source").asText("");
            String state = q.path("marketState").asText("");
            if (price.signum() <= 0 || price.precision() > 40 || !currency.matches("[A-Z]{3}")
                    || source.isBlank() || source.length() > 100
                    || !Set.of("open", "closed", "pre", "post", "unknown").contains(state)) throw new IllegalArgumentException();
            return QuoteResult.ok(new MarketQuote(symbol, price, currency, Instant.parse(q.path("asOf").asText()),
                    state, source, item.path("stale").asBoolean()));
        } catch (RuntimeException e) { return QuoteResult.error("INVALID_QUOTE_RESPONSE", 60); }
    }

    private long retryAfter(String value) {
        if (value == null) return 60;
        try { return Math.max(60, Long.parseLong(value)); }
        catch (NumberFormatException e) {
            try { return Math.max(60, Duration.between(clock.instant(),
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds()); }
            catch (RuntimeException ignored) { return 60; }
        }
    }
}
