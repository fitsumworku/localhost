# OLTP → OLAP reporting

This implementation uses your separate `postgres-olap` container and a scheduled
Spring Boot ETL job. The app keeps its existing OLTP datasource for authentication,
accounts and trading, and a second JDBC datasource for reporting. No frontend
changes are included.

## Start with an existing OLTP database

The OLAP schema has changed. A Docker image rebuild alone does not update tables
inside an existing PostgreSQL volume. Apply the new schema **only to OLAP**:

```bash
docker compose stop app
docker compose up -d --build postgres-olap
docker compose exec -T postgres-olap sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < initdb/olap_schema.sql
docker compose up -d --build app
docker compose logs -f app
```

The schema command resets reporting tables. It does not touch OLTP. After the app
starts, the first scheduled refresh runs after about 10 seconds. Subsequent runs
start 60 seconds after the preceding run finishes. No volume deletion or OLTP
reset is needed. Do not run `docker compose down -v` to apply this change.

For a completely new environment, `docker compose up -d --build` initializes
both schemas from their separate Dockerfiles. Load your test data into OLTP; the
next ETL refresh will copy the reporting data. The app does not generate trade
history by itself. Existing `.env` database settings still work.

## Configuration

Docker Compose supplies the separate OLAP JDBC URL and credentials and enables
reporting. Optional `.env` settings:

```dotenv
REPORTING_ENABLED=true
ETL_ENABLED=true
ETL_INITIAL_DELAY_MS=10000
ETL_DELAY_MS=60000
ETL_BATCH_SIZE=500
REPORTING_QUERY_TIMEOUT_SECONDS=120
OLAP_POSTGRES_DB=sampleolapdata
OLAP_POSTGRES_USER=postgres
# Optional: OLAP_POSTGRES_PASSWORD; otherwise uses POSTGRES_PASSWORD.
OLAP_HOST_PORT=5433
```

Choose an unused OLAP host port if 5433 is occupied. Containers always connect to
`postgres-olap:5432`; host ports are only for pgAdmin/IDE clients outside Docker.
The existing OLTP port defaults remain unchanged.

For an IDE run, reporting is off by default so existing backend tests and local
OLTP workflows need no second database. Enable it with these environment values,
in addition to your existing OLTP/JWT variables:

```dotenv
REPORTING_ENABLED=true
OLAP_DB_URL=jdbc:postgresql://localhost:5433/sampleolapdata
OLAP_DB_USERNAME=postgres
OLAP_DB_PASSWORD=<your OLAP password>
ETL_ENABLED=true
```

`.env` is read by Compose, not automatically by a Java IDE process. Recreate the
app container after changing environment settings. `ETL_ENABLED=false` disables
automatic refresh while leaving admin manual refresh and reporting available;
`REPORTING_ENABLED=false` disables the entire reporting module.

## Metric definitions

| Metric | Definition |
|---|---|
| Total trading volume | Sum of successful trades' stored USD `Cash_Amount`; gross buy plus sell value. This is platform activity, not market-wide exchange volume. |
| Trade count | Number of trade rows, counted once per successful execution. Failed/rejected execution attempts contribute no volume. |
| Sector volume | Successful trade value grouped by the security's current sector; missing/blank sectors appear as `Unclassified`. |
| Stock/instrument volume | Grouped by security ID, with ticker and exchange. Use `assetType=EQUITY` for stocks only. Default includes equities, ETFs, FX and crypto. Quantities are summed only within one instrument. |
| Top users | Successful trade value across all accounts belonging to the same client, largest first. Historical trades remain counted if that client is later blacklisted. |
| Buy/sell volume | Positive USD values for each side. Total = buy + sell, not buy minus sell. |
| Active client count | Current `CLIENT` users with `Status=ACTIVE`, including clients who have never traded or opened an account. |
| Trading client count | Distinct client users with at least one successful trade within the selected period/asset filter. This is a trading-activity measure, not login/session tracking. |
| Blacklisted client count | Current `CLIENT` users with `Status=SUSPENDED`. Admin identities are excluded from client counts. |
| Submitted orders | Every persisted order whose creation time is in the selected period, regardless of its current status. Requests rejected before an order is saved are not available to count. |
| Order status counts | Current SUBMITTED, ACCEPTED, IN_EXECUTION, FILLED, REJECTED and CANCELLED states of those submitted orders. `SUBMITTED` status alone is not the total submitted count. |

Date filters are ISO timestamps: `from` is inclusive and `to` is exclusive. Omit
both for all time. Daily boundaries in New York must include the correct UTC
offset for that date, for example `2026-10-15T00:00:00-04:00` through
`2026-10-16T00:00:00-04:00`. Stored timestamps and date-dimension keys use UTC.
Current active/blacklisted population counts are snapshot-wide, independent of
date and asset filters; trading activity and orders use those filters.

Order reports describe the current outcome of a submitted-order cohort. They do
not claim to show every state transition or the status as of a historical date.
For example, an order submitted yesterday and filled today counts as FILLED in
yesterday's submitted cohort after refresh; today's trading volume includes the
trade. A request for historical transition counts would need an OLTP event
history, which the current schema does not have.

## ETL flow and reliability

1. Acquire a transaction-scoped advisory lock on OLAP. Concurrent manual or
   scheduled runs cannot overwrite one another with out-of-order snapshots.
2. Validate the OLAP schema version and reject an operational database as the
   target before modifying reporting tables.
3. Read OLTP in a read-only `REPEATABLE READ` transaction. All dimension and fact
   queries therefore see one consistent source snapshot.
4. Replace report dimensions and facts inside one OLAP transaction, with bounded
   JDBC batches. Copy users, accounts, securities, orders, execution attempts,
   trades, non-trading transactions and disputes. User email, password hash,
   token version and JWT fields are excluded. Cash ledger and audit implementation remain
   outside this reporting work.
5. Update the successful snapshot timestamp, refresh count and row counts in that
   same target transaction, then commit. If any extraction/transformation/load
   step fails, roll back OLAP and retain the previous successful report snapshot.

Repeated refreshes replace a snapshot; they do not append duplicates. Changes to
existing orders, securities, user status and disputes are picked up. Source rows
removed during development disappear from the next snapshot. OLTP is read only
throughout ETL. Foreign trades use the actual stored execution conversion/value;
the job never calls Fauxnance or reprices historical trades.

Full refresh is a deliberate choice for this project's data size. It scans the
reporting source tables each run and rebuilds OLAP rows; it is not incremental
CDC. If the dataset becomes large, reduce refresh frequency and implement an
incremental pipeline once change tracking and deletion handling are defined.
There is no Kafka, Debezium, Airflow, trigger or stored procedure requirement.

Dimensions use current values (Type 1). Changing a user's name/status/role or a
security's sector will reclassify the existing facts on the next refresh. This
supports current dashboards; it does not retain historical dimension versions.

Reports run in read-only repeatable-read OLAP transactions. A report sees one
complete snapshot even if a refresh commits during its queries. Each response
includes the source snapshot time and last successful load time so the caller can
see freshness. Before the first successful load, metric endpoints return 503;
thereafter a failed refresh leaves older data readable with its old timestamps.
ETL status and app logs show when a successful refresh last happened.
The two scheduler threads let order polling continue during the ETL job.

## API

All routes require an active ADMIN bearer token. Client accounts cannot view
platform-wide reports or trigger ETL. Use `/auth/login` with your existing admin
credentials. Live Swagger UI at `/swagger-ui.html` and `/v3/api-docs` includes the
actual endpoint and DTO definitions.

| Method | Path | Purpose |
|---|---|---|
| GET | `/reports/summary` | Trading, order and client counts together |
| GET | `/reports/volume/sectors` | Sector volume ranking |
| GET | `/reports/volume/securities` | Instrument volume ranking, including units per instrument |
| GET | `/reports/volume/users` | Top clients across their accounts |
| GET | `/reports/volume/sides` | Buy and sell totals |
| GET | `/reports/orders` | Submitted total and current status breakdown |
| GET | `/reports/users` | Current client population and distinct trading clients |
| GET | `/reports/etl` | Last successful ETL snapshot, refresh count and loaded row counts |
| POST | `/reports/etl/refresh` | Run a synchronous refresh and return its result |

Volume rankings accept `limit` (default 10, maximum 100). Report endpoints accept
optional `from`, `to` and `assetType` (`EQUITY`, `ETF`, `FOREX`, `CRYPTO`). Examples:

```bash
# Set TOKEN to the admin accessToken returned by /auth/login.
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/reports/etl
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8081/reports/etl/refresh
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/reports/summary
curl -G -H "Authorization: Bearer $TOKEN" \
  --data-urlencode 'from=2026-10-01T00:00:00Z' \
  --data-urlencode 'to=2026-11-01T00:00:00Z' \
  --data-urlencode 'assetType=EQUITY' \
  --data-urlencode 'limit=10' \
  http://localhost:8081/reports/volume/securities
```

Invalid date ranges/limits return 400. Missing credentials return 401, clients
return 403, an overlapping refresh returns 409, and unavailable/uninitialized
reporting storage returns 503. Manual refresh can take time and returns only
when complete; it does not run inside an OLTP business transaction.

You can also run `reporting_queries.sql` directly in the OLAP Query Tool in
pgAdmin. Configure pgAdmin's reporting server as host `postgres-olap`, port 5432,
and the configured OLAP database/user/password. Do not connect those queries to
the operational database.

## Files and scope

- `initdb/olap_schema.sql`: full resettable reporting schema and metadata.
- `team/.../reporting/`: JDBC configuration, ETL, scheduler, reporting DTOs,
  queries and protected API.
- `team/.../config/SecurityConfig.java`: explicit admin reporting routes.
- `team/src/main/resources/application.properties`: reporting/ETL options.
- `docker-compose.yaml`: separate reporting credentials and startup dependency.
- `team/pom.xml`: makes both schemas available to integration tests.
- `etl_process/reporting_queries.sql`: SQL examples for the requested metrics.

The OLTP schema, trading rules, stale quote choice, security universe files,
frontend and existing authentication behavior are unchanged.

## Run the reporting tests

With Java 17 and Docker available, run from the backend directory:

```bash
cd team
./mvnw -Dtest=ReportingIntegrationTests,ReportingEtlServiceTests test
```

The integration tests start two disposable PostgreSQL 16 containers. They check
metric calculations, date boundaries, all current order states, user changes,
multiple accounts, duplicate-free repeat loads, rollback, admin access and
refresh contention. They reset their own test databases, not the Compose data.

An alternative for an environment without Docker is two **disposable** native
PostgreSQL databases. Set `REPORTING_TEST_OLTP_JDBC_URL`,
`REPORTING_TEST_OLAP_JDBC_URL`, `REPORTING_TEST_DB_USERNAME`,
`REPORTING_TEST_DB_PASSWORD` and `REPORTING_TEST_ALLOW_RESET=true` before running
those tests. The explicit reset flag is required because this mode resets public
tables in both supplied databases. Never point it at your working databases.

See `VERIFICATION.json` for the checks actually run on this delivered version.

Validation on this delivered version: the backend packaged successfully; seven
reporting integration tests and four ETL unit tests passed. The native database
lock-contention test was skipped. A packaged-app smoke test passed 43 checks
using the full supplied seed, including scheduled/manual refresh, all fact
counts, USD reconciliation and unchanged OLTP data. Database checks used PGlite,
not Docker/native PostgreSQL. An initial whole-project test attempt was blocked
by local database-protocol and Mockito runtime issues; the focused reporting
tests passed after the test harness was repaired. The entire existing test suite
was not verified.
