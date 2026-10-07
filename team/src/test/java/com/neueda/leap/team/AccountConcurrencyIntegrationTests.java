package com.neueda.leap.team;

import com.neueda.leap.team.support.AccountTestSupport;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.*;

class AccountConcurrencyIntegrationTests extends AccountTestSupport {
    @BeforeEach void requireNativePostgres() {
        Assumptions.assumeTrue(nativePostgres(), "PGlite has one PostgreSQL session; run these tests with PostgreSQL 16/Testcontainers.");
    }
    @Test void simultaneousDepositsDoNotLoseUpdates() throws Exception {
        long id = account(token);
        var work = new ArrayList<Callable<MvcResult>>();
        for (int i = 0; i < 8; i++) work.add(() -> cash(id, "deposits", "10.00"));
        for (MvcResult r : simultaneous(work)) assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(balance(id)).isEqualByComparingTo("80.00");
        assertThat(count("transactions", id)).isEqualTo(8);
        assertThat(count("cash_ledger", id)).isEqualTo(8);
    }
    @Test void simultaneousRetriesPostExactlyOnce() throws Exception {
        long id = account(token); UUID key = UUID.randomUUID();
        var work = new ArrayList<Callable<MvcResult>>();
        for (int i = 0; i < 6; i++) work.add(() -> cash(id, "deposits", "10.00", key, token));
        var results = simultaneous(work);
        assertThat(results.stream().filter(r -> r.getResponse().getStatus() == 201).count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> r.getResponse().getStatus() == 200).count()).isEqualTo(5);
        assertThat(balance(id)).isEqualByComparingTo("10.00");
        assertThat(count("transactions", id)).isEqualTo(1);
        assertThat(count("cash_ledger", id)).isEqualTo(1);
    }
    @Test void simultaneousWithdrawalsCannotOverspendAvailableCash() throws Exception {
        long id = account(token); cash(id, "deposits", "100.00");
        var results = simultaneous(List.of(() -> cash(id, "withdrawals", "80.00"), () -> cash(id, "withdrawals", "80.00")));
        assertThat(results.stream().map(r -> r.getResponse().getStatus()).toList()).containsExactlyInAnyOrder(201, 409);
        assertThat(balance(id)).isEqualByComparingTo("20.00");
        assertThat(count("transactions", id)).isEqualTo(3); // Deposit, successful withdrawal, failed withdrawal.
        assertThat(count("cash_ledger", id)).isEqualTo(2);
    }
    private List<MvcResult> simultaneous(List<Callable<MvcResult>> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        CountDownLatch ready = new CountDownLatch(work.size()), go = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<MvcResult>>();
            for (Callable<MvcResult> job : work) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                return job.call();
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
            var results = new ArrayList<MvcResult>();
            for (Future<MvcResult> f : futures) results.add(f.get(30, TimeUnit.SECONDS));
            return results;
        } finally { go.countDown(); pool.shutdownNow(); }
    }
}
