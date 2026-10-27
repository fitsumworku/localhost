package com.neueda.leap.team.market;

import com.neueda.leap.team.config.FauxnanceProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class FauxnanceClientTests {
    HttpServer server;
    FauxnanceClient client;
    volatile int status=200;
    volatile String body="";
    volatile String retry="60";
    volatile String observedKey;
    volatile int failFirst=0;
    AtomicInteger requests=new AtomicInteger();
    @BeforeEach void start()throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/quotes",exchange->{
            boolean fail=requests.incrementAndGet()<=failFirst;observedKey=exchange.getRequestHeaders().getFirst("X-Api-Key");
            exchange.getResponseHeaders().set("Content-Type","application/json");
            if(retry!=null)exchange.getResponseHeaders().set("Retry-After",retry);
            byte[] data=(fail?"{\"message\":\"Internal Server Error\"}":body).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(fail?500:status,data.length);exchange.getResponseBody().write(data);exchange.close();
        });server.start();
        client=new FauxnanceClient(new FauxnanceProperties("http://127.0.0.1:"+server.getAddress().getPort()+"/v1","test-provider-key",2,true,1800,345600),JsonMapper.builder().build(),Clock.systemUTC());
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void parsesBatchQuotesWithExactDecimalsAndPerItemFailures(){
        body="""
                {"data":{"quotes":[{"symbol":"AAPL","source":"upstream:test","stale":false,
                "quote":{"price":123.123456789123,"currency":"USD","asOf":"2026-10-07T15:00:00Z","marketState":"open"}},
                {"symbol":"BAD","error":{"code":"SYMBOL_NOT_FOUND","message":"internal details"}}]}}
                """;
        var results=client.quotes(Set.of("AAPL","BAD","MISSING"));
        assertThat(results.get("AAPL").quote().price()).isEqualByComparingTo("123.123456789123");
        assertThat(results.get("BAD").errorCode()).isEqualTo("SYMBOL_NOT_FOUND");assertThat(results.get("MISSING").quote()).isNull();
        assertThat(observedKey).isEqualTo("test-provider-key");assertThat(requests.get()).isEqualTo(1);
    }
    @Test void obeysQuotaBackoffAndHandlesGatewayNonJsonErrors(){
        status=429;retry="7200";body="too many requests";
        assertThat(client.quotes(Set.of("AAPL")).get("AAPL").retryAfterSeconds()).isEqualTo(7200);
        status=403;body="Forbidden";assertThat(client.quotes(Set.of("AAPL")).get("AAPL").errorCode()).isEqualTo("MARKET_DATA_AUTH_FAILED");
    }
    @Test void retriesMomentaryProviderErrorsWithinTheCall(){
        failFirst=1;body="""
                {"data":{"quotes":[{"symbol":"AAPL","source":"cache","stale":false,
                "quote":{"price":100,"currency":"USD","asOf":"2026-10-07T15:00:00Z","marketState":"unknown"}}]}}
                """;
        assertThat(client.quotes(Set.of("AAPL")).get("AAPL").quote().price()).isEqualByComparingTo("100");
        assertThat(requests.get()).isEqualTo(2);
    }
    @Test void persistentProviderErrorsAskForAShortRetry(){
        failFirst=Integer.MAX_VALUE;retry=null;
        var result=client.quotes(Set.of("AAPL")).get("AAPL");
        assertThat(result.errorCode()).isEqualTo("MARKET_DATA_UNAVAILABLE");
        assertThat(result.retryAfterSeconds()).isEqualTo(FauxnanceClient.TRANSIENT_RETRY_SECONDS);
        assertThat(requests.get()).isEqualTo(3);
        // A provider-supplied Retry-After still wins.
        retry="120";assertThat(client.quotes(Set.of("AAPL")).get("AAPL").retryAfterSeconds()).isEqualTo(120);
    }
    @Test void malformedQuotesNeverBecomeExecutablePrices(){
        body="""
                {"data":{"quotes":[{"symbol":"AAPL","source":"cache","stale":false,
                "quote":{"price":-12,"currency":"USD","asOf":"bad time","marketState":"open"}}]}}
                """;
        assertThat(client.quotes(Set.of("AAPL")).get("AAPL").quote()).isNull();
    }
    @Test void splitsRequestsAtProviderLimit(){
        body="{\"data\":{\"quotes\":[]}}";var symbols=new HashSet<String>();for(int i=0;i<26;i++)symbols.add("T"+i);
        assertThat(client.quotes(symbols)).hasSize(26);assertThat(requests.get()).isEqualTo(2);
    }
    @Test void missingKeyDoesNotCallUpstream(){
        var offline=new FauxnanceClient(new FauxnanceProperties("http://127.0.0.1:"+server.getAddress().getPort()+"/v1","",2,true,1800,345600),JsonMapper.builder().build(),Clock.systemUTC());
        assertThat(offline.quotes(Set.of("AAPL")).get("AAPL").errorCode()).isEqualTo("MARKET_DATA_NOT_CONFIGURED");assertThat(requests.get()).isZero();
    }
    @Test void parsesDailyClosesAndEncodesSymbolPath(){
        var path=new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/v1/candles",exchange->{
            path.set(exchange.getRequestURI().getRawPath());
            byte[] data=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,data.length);exchange.getResponseBody().write(data);exchange.close();
        });
        body="""
                {"data":{"symbol":"X:BTC-USD","interval":"1d","currency":"USD","candles":[
                {"date":"2026-10-17","close":81500.5,"synthetic":false},{"date":"2026-10-16","close":80000.25,"synthetic":true},
                {"date":"2026-10-18","close":-1,"synthetic":false}]}}
                """;
        var closes=client.dailyCloses("X:BTC-USD",java.time.LocalDate.parse("2026-10-16"),java.time.LocalDate.parse("2026-10-18")).orElseThrow();
        assertThat(path.get()).isEqualTo("/v1/candles/X%3ABTC-USD");
        assertThat(closes).extracting(DailyClose::date).map(Object::toString).containsExactly("2026-10-16","2026-10-17");
        assertThat(closes.get(1).close()).isEqualByComparingTo("81500.5");
        status=500;body="{\"message\":\"Internal Server Error\"}";
        assertThat(client.dailyCloses("AAPL",java.time.LocalDate.parse("2026-10-16"),java.time.LocalDate.parse("2026-10-18"))).isEmpty();
    }
}
