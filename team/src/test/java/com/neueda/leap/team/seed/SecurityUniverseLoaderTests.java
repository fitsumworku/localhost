package com.neueda.leap.team.seed;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.*;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class SecurityUniverseLoaderTests {
    private static final String US = "classpath:universes/us-v1.json";
    private static final String DEV = "classpath:universes/dev-v1.json";
    private final JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final SecurityUniverseLoader loader = new SecurityUniverseLoader(json, new DefaultResourceLoader());

    @Test void fullCatalogContains569DistinctCanonicalSymbolsIncludingEtfs() {
        var rows = loader.load(List.of(US, "classpath:universes/india-v1.json",
                "classpath:universes/fx-v1.json", "classpath:universes/crypto-v1.json"));
        assertThat(rows).hasSize(569);
        assertThat(rows.stream().map(SeedSecurity::ticker)).doesNotHaveDuplicates();
        assertThat(rows.stream().filter(r -> r.assetType().equals("EQUITY"))).hasSize(533);
        assertThat(rows.stream().filter(r -> r.assetType().equals("ETF"))).hasSize(12);
        assertThat(rows.stream().filter(r -> r.assetType().equals("FOREX"))).hasSize(12);
        assertThat(rows.stream().filter(r -> r.assetType().equals("CRYPTO"))).hasSize(12);
        var fx = rows.stream().filter(r -> r.ticker().equals("FX:USDJPY")).findFirst().orElseThrow();
        assertThat(fx.baseCurrency()).isEqualTo("USD");
        assertThat(fx.quoteCurrency()).isEqualTo("JPY");
        var india = rows.stream().filter(r -> r.ticker().equals("INFY.NS")).findFirst().orElseThrow();
        assertThat(india.exchange()).isEqualTo("NSE");
        assertThat(india.quoteCurrency()).isEqualTo("INR");
        var crypto = rows.stream().filter(r -> r.ticker().equals("X:BTC-USD")).findFirst().orElseThrow();
        assertThat(crypto.baseCurrency()).isNull();
        assertThat(crypto.quoteCurrency()).isEqualTo("USD");
        assertThat(rows.stream().filter(r -> r.ticker().equals("BRK.B"))).hasSize(1);
    }

    @Test void smallDevUniverseIsAnAlternativeAndKeepsItsExchangeLabels() {
        var rows = loader.load(List.of(DEV));
        assertThat(rows).hasSize(20);
        assertThat(rows.stream().filter(r -> r.ticker().equals("AAPL")).findFirst().orElseThrow().exchange())
                .isEqualTo("NASDAQ");
    }

    @Test void overlappingDevAndFullUsListsFailInsteadOfCreatingDuplicateSecurities() {
        assertThatThrownBy(() -> loader.load(List.of(US, DEV)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate Fauxnance symbol");
    }

    @Test void forexQuoteCurrencyMustMatchTheSymbol() {
        assertThatThrownBy(() -> loadJson("""
                {"version":1,"id":"bad-fx","market":"FX","symbols":[
                {"symbol":"FX:USDJPY","name":"USD/JPY","type":"fx","exchange":"FX","currency":"USD"}]}
                """)).hasMessageContaining("does not match market FX");
    }

    @Test void indiaSuffixMustMatchItsExchange() {
        assertThatThrownBy(() -> loadJson("""
                {"version":1,"id":"bad-india","market":"IN","symbols":[
                {"symbol":"INFY.NS","name":"Infosys","type":"equity","exchange":"BSE","currency":"INR"}]}
                """)).hasMessageContaining("does not match market IN");
    }

    @Test void unsupportedAssetTypesAndVersionsDoNotSilentlyDisappear() {
        assertThatThrownBy(() -> loadJson("""
                {"version":1,"id":"bad-type","market":"US","symbols":[
                {"symbol":"BOND","name":"Bond","type":"bond","exchange":"US","currency":"USD"}]}
                """)).hasMessageContaining("Unsupported asset type");
        assertThatThrownBy(() -> loadJson("{\"version\":2,\"symbols\":[]}"))
                .hasMessageContaining("Only universe version 1");
    }

    @Test void absentEmptyAndUnreadableInputsFailClearly() {
        assertThatThrownBy(() -> loader.load(List.of())).hasMessageContaining("at least one");
        assertThatThrownBy(() -> loader.load(List.of("classpath:universes/not-found.json")))
                .hasMessageContaining("Cannot read securities universe");
        assertThatThrownBy(() -> loadJson("{\"version\":1,\"id\":\"empty\",\"market\":\"US\",\"symbols\":[]}"))
                .hasMessageContaining("nonempty array");
    }

    private List<SeedSecurity> loadJson(String text) {
        ResourceLoader memory = new DefaultResourceLoader() {
            @Override public Resource getResource(String location) {
                return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8));
            }
        };
        return new SecurityUniverseLoader(json, memory).load(List.of("classpath:universes/test.json"));
    }
}
