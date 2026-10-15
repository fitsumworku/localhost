package com.neueda.leap.team.seed;

import com.neueda.leap.team.config.SecuritySeedProperties;
import com.neueda.leap.team.support.AccountTestSupport;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class SecuritySeedIntegrationTests extends AccountTestSupport {
    private static final List<String> DEV = List.of("classpath:universes/dev-v1.json");
    @Autowired SecuritySeedService seeder;
    @Autowired SecuritySeedProperties properties;

    @Test void fullImportPersistsTypesAndCurrencies() {
        assertThat(seeder.seed(properties.resources())).isEqualTo(new SecuritySeedService.SeedResult(569, 569, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM securities", Long.class)).isEqualTo(569);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM securities WHERE asset_type='ETF'", Long.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT quote_currency FROM securities WHERE ticker='INFY.NS'", String.class)).isEqualTo("INR");
        assertThat(jdbc.queryForObject("SELECT base_currency FROM securities WHERE ticker='FX:USDJPY'", String.class)).isEqualTo("USD");
        assertThat(jdbc.queryForObject("SELECT quote_currency FROM securities WHERE ticker='FX:USDJPY'", String.class)).isEqualTo("JPY");
    }

    @Test void rerunPreservesIdsStatusesNamesHoldingsAndUnlistedRows() throws Exception {
        seeder.seed(DEV);
        long id = jdbc.queryForObject("SELECT security_id FROM securities WHERE ticker='AAPL'", Long.class);
        long account = account(token);
        jdbc.update("INSERT INTO account_positions(account_id,security_id,quantity,average_price) VALUES (?,?,2,100)", account, id);
        jdbc.update("UPDATE securities SET status='HALTED',name='Edited name',sector='Edited sector' WHERE security_id=?", id);
        jdbc.update("UPDATE securities SET status='DELISTED' WHERE ticker='MSFT'");
        long otherId = security();
        assertThat(seeder.seed(DEV)).isEqualTo(new SecuritySeedService.SeedResult(20, 0, 20));
        assertThat(jdbc.queryForObject("SELECT security_id FROM securities WHERE ticker='AAPL'", Long.class)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT status FROM securities WHERE security_id=?", String.class, id)).isEqualTo("HALTED");
        assertThat(jdbc.queryForObject("SELECT name FROM securities WHERE security_id=?", String.class, id)).isEqualTo("Edited name");
        assertThat(jdbc.queryForObject("SELECT sector FROM securities WHERE security_id=?", String.class, id)).isEqualTo("Edited sector");
        assertThat(jdbc.queryForObject("SELECT status FROM securities WHERE ticker='MSFT'", String.class)).isEqualTo("DELISTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM securities WHERE security_id=?", Long.class, otherId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT security_id FROM account_positions WHERE account_id=?", Long.class, account)).isEqualTo(id);

    }

    @Test void incompatibleExistingCurrencyRollsBackEarlierInserts() {
        jdbc.update("""
                INSERT INTO securities(ticker,name,asset_type,exchange,quote_currency)
                VALUES ('XOM','Existing conflicting row','EQUITY','NYSE','EUR')
                """);
        assertThatThrownBy(() -> seeder.seed(DEV)).isInstanceOf(IllegalStateException.class).hasMessageContaining("XOM");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM securities", Long.class)).isEqualTo(1);

    }

    @Test void switchingBetweenDevAndFullUsExchangeLabelsDoesNotCreateDuplicates() {
        seeder.seed(DEV);
        assertThatThrownBy(() -> seeder.seed(properties.resources()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Existing security conflicts");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM securities", Long.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM securities WHERE ticker='AAPL'", Long.class)).isEqualTo(1);

    }

    @Test void aLaterInvalidFilePreventsAnyImport() {
        assertThatThrownBy(() -> seeder.seed(List.of(DEV.get(0), "classpath:universes/missing.json")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Cannot read securities universe");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM securities", Long.class)).isZero();

    }

    @Test void databaseFailureRollsBackTheEntireSeed() {
        jdbc.execute("ALTER TABLE securities ADD CONSTRAINT test_seed_failure CHECK (ticker <> 'AMZN') NOT VALID");
        try {
            assertThatThrownBy(() -> seeder.seed(DEV)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM securities", Long.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE securities DROP CONSTRAINT test_seed_failure");
        }
    }

    @Test void concurrentStartupsInsertEachSecurityExactlyOnce() throws Exception {
        Assumptions.assumeTrue(nativePostgres(), "Concurrency requires PostgreSQL/Testcontainers, not PGlite.");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<SecuritySeedService.SeedResult> work = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return seeder.seed(DEV);
            };
            var first = workers.submit(work); var second = workers.submit(work); start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).inserted() + second.get(30, TimeUnit.SECONDS).inserted()).isEqualTo(20);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM securities", Long.class)).isEqualTo(20);

        } finally { start.countDown(); workers.shutdownNow(); }
    }

}
