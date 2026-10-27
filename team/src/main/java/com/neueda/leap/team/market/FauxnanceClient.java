package com.neueda.leap.team.market;

import com.neueda.leap.team.config.FauxnanceProperties;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Market data only: Fauxnance does not place trades. Never log its key or raw error bodies. */
@Component
public class FauxnanceClient implements MarketDataClient {
    private static final Logger log = LoggerFactory.getLogger(FauxnanceClient.class);
    // Provider 5xx and network errors are usually momentary: retry within the call, then
    // ask callers to come back soon rather than after the default one-minute backoff.
    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {300, 1000};
    static final long TRANSIENT_RETRY_SECONDS = 15;
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
                var response = send(request, "quotes");
                if (response.statusCode() != 200) {
                    String code = switch (response.statusCode()) {
                        case 202 -> "BACKFILL_IN_PROGRESS";
                        case 401, 403 -> "MARKET_DATA_AUTH_FAILED";
                        case 429 -> "RATE_LIMITED";
                        // Request-level errors do not prove an individual symbol is untradeable.
                        default -> "MARKET_DATA_UNAVAILABLE";
                    };
                    String retryHeader = response.headers().firstValue("Retry-After").orElse(null);
                    long retry = response.statusCode() >= 500 && retryHeader == null
                            ? TRANSIENT_RETRY_SECONDS : retryAfter(retryHeader);
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
                batch.forEach(s -> result.put(s, QuoteResult.error("MARKET_DATA_UNAVAILABLE", TRANSIENT_RETRY_SECONDS)));
            }
        }
        all.forEach(s -> result.putIfAbsent(s, QuoteResult.error("MARKET_DATA_UNAVAILABLE", 60)));
        return Map.copyOf(result);
    }

    /** One quota unit per call. Only well-formed candles are kept; any request-level error means no history. */
    @Override public Optional<List<DailyClose>> dailyCloses(String symbol, LocalDate from, LocalDate to) {
        if (properties.apiKey().isBlank()) return Optional.empty();
        try {
            URI uri = URI.create(properties.baseUrl().replaceAll("/+$", "") + "/candles/"
                    + URLEncoder.encode(symbol, StandardCharsets.UTF_8) + "?from=" + from + "&to=" + to);
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .header("X-Api-Key", properties.apiKey()).header("Accept", "application/json").GET().build();
            var response = send(request, "candles");
            if (response.statusCode() != 200) return Optional.empty();
            JsonNode candles = json.readTree(response.body()).path("data").path("candles");
            if (!candles.isArray()) return Optional.empty();
            List<DailyClose> closes = new ArrayList<>();
            for (JsonNode candle : candles) {
                if (!candle.path("close").isNumber() || candle.path("close").decimalValue().signum() <= 0) continue;
                if (candle.path("synthetic").asBoolean(false) && !properties.allowSynthetic()) continue;
                closes.add(new DailyClose(LocalDate.parse(candle.path("date").asText()), candle.path("close").decimalValue()));
            }
            closes.sort(Comparator.comparing(DailyClose::date));
            return Optional.of(List.copyOf(closes));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Sends with a short in-call retry on provider 5xx and network errors, within one timeout's
     * worth of time so a provider outage cannot stall the order worker. Logs status codes and
     * exception types only: never the key, URL query or response body.
     */
    private HttpResponse<String> send(HttpRequest request, String operation) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(properties.timeoutSeconds()).toNanos();
        for (int attempt = 1; ; attempt++) {
            boolean last = attempt >= MAX_ATTEMPTS || System.nanoTime() >= deadline;
            try {
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 500) return response;
                if (last) {
                    log.warn("Fauxnance {} failed with HTTP {} after {} attempt(s)", operation, response.statusCode(), attempt);
                    return response;
                }
                log.debug("Fauxnance {} returned HTTP {} (attempt {}), retrying", operation, response.statusCode(), attempt);
            } catch (IOException e) {
                if (last) {
                    log.warn("Fauxnance {} failed with {} after {} attempt(s)", operation, e.getClass().getSimpleName(), attempt);
                    throw e;
                }
                log.debug("Fauxnance {} failed with {} (attempt {}), retrying", operation, e.getClass().getSimpleName(), attempt);
            }
            Thread.sleep(BACKOFF_MS[Math.min(attempt - 1, BACKOFF_MS.length - 1)]);
        }
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
