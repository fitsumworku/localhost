package com.neueda.leap.team.seed;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SecuritySeedRepository {
    private final JdbcTemplate jdbc;
    public SecuritySeedRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<ExistingSecurity> findByTicker(String ticker) {
        return jdbc.query("""
                SELECT security_id, ticker, asset_type, exchange, quote_currency, base_currency
                FROM securities WHERE upper(ticker) = ?
                """, (rs, row) -> new ExistingSecurity(rs.getLong("security_id"), rs.getString("ticker"),
                rs.getString("asset_type"), rs.getString("exchange"), rs.getString("quote_currency"),
                rs.getString("base_currency")), ticker);
    }

    public Optional<Long> insertIfAbsent(SeedSecurity item) {
        List<Long> ids = jdbc.query("""
                INSERT INTO securities(ticker, name, asset_type, exchange, quote_currency, base_currency, status, sector)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                ON CONFLICT (ticker, exchange) DO NOTHING
                RETURNING security_id
                """, (rs, row) -> rs.getLong(1), item.ticker(), item.name(), item.assetType(), item.exchange(),
                item.quoteCurrency(), item.baseCurrency(), item.sector());
        return ids.stream().findFirst();
    }

    public record ExistingSecurity(long id, String ticker, String assetType, String exchange,
            String quoteCurrency, String baseCurrency) {}
}
