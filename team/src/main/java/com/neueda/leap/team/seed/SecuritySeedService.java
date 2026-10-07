package com.neueda.leap.team.seed;

import com.neueda.leap.team.service.AuditService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecuritySeedService {
    private final SecurityUniverseLoader loader;
    private final SecuritySeedRepository securities;
    private final AuditService audit;

    public SecuritySeedService(SecurityUniverseLoader loader, SecuritySeedRepository securities, AuditService audit) {
        this.loader = loader; this.securities = securities; this.audit = audit;
    }

    /** Insert missing reference rows and audit them in the same transaction. Never overwrite existing rows. */
    @Transactional
    public SeedResult seed(List<String> resources) {
        List<SeedSecurity> items = loader.load(resources);
        int inserted = 0;
        for (SeedSecurity item : items) {
            var existing = securities.findByTicker(item.ticker());
            if (!existing.isEmpty()) {
                requireCompatible(item, existing);
                continue;
            }
            var id = securities.insertIfAbsent(item);
            if (id.isEmpty()) {
                // Another instance may have inserted the same unique key while this one was starting.
                requireCompatible(item, securities.findByTicker(item.ticker()));
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("ticker", item.ticker()); values.put("name", item.name());
            values.put("asset_type", item.assetType()); values.put("exchange", item.exchange());
            values.put("quote_currency", item.quoteCurrency()); values.put("base_currency", item.baseCurrency());
            values.put("sector", item.sector()); values.put("status", "ACTIVE"); values.put("universe_id", item.universeId());
            audit.recordSystemEvent("securities", Map.of("security_id", id.get()), "SECURITY_SEEDED", null, values);
            inserted++;
        }
        return new SeedResult(items.size(), inserted, items.size() - inserted);
    }

    private static void requireCompatible(SeedSecurity item, List<SecuritySeedRepository.ExistingSecurity> rows) {
        boolean matches = rows.size() == 1;
        if (matches) {
            var stored = rows.get(0);
            matches = stored.ticker().equals(item.ticker()) && stored.exchange().equals(item.exchange())
                    && stored.assetType().equals(item.assetType()) && stored.quoteCurrency().equals(item.quoteCurrency())
                    && Objects.equals(stored.baseCurrency(), item.baseCurrency());
        }
        if (!matches) {
            throw new IllegalStateException("Existing security conflicts with Fauxnance seed: " + item.ticker()
                    + ". Review its exchange, asset type and currencies; do not delete a security referenced by trades."
                    + " dev-v1.json and us-v1.json use different exchange labels for overlapping symbols.");
        }
        // A name, sector or ACTIVE/HALTED/DELISTED status edited in the DB is deliberately preserved.
    }

    public record SeedResult(int total, int inserted, int existing) {}
}
