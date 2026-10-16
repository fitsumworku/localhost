# OLTP test data

This package targets `initdb/oltp_schema.sql` from `localhost(20261016-011409).zip`.
It generates only OLTP data. It includes equity, ETF, forex, and crypto trades;
it has no dependency on the OLAP design or ETL.

Copy this entire `test_data` folder into the project root, beside `docker-compose.yaml`.
No Java, schema, Dockerfile, or application-properties changes are required.
Do not run the older scripts in `data_generators/` or their `data/*_insert.sql` output:
they reference columns that no longer match this schema.

## Choose one ready-to-run SQL file

| File | Contents |
| --- | --- |
| `oltp_seed.sql` | Application demo data; ledger, audit, and dispute tables remain empty for these demo users. |
| `oltp_seed_full.sql` | The same demo users and economic history, plus sample cash-ledger entries, audit logs, and disputes. Use this for testing all tables. |
| `validate_oltp_seed.sql` | Read-only consistency checks. Every `violations` result should be `0`. |
| `generate_oltp_seed.py` | Reusable Python 3.10+ generator; standard library only. |
| `*.manifest.json` | Exact row counts, logins, fixture account scenarios, settings, and SQL checksum. |
| `universes/*.json` | Copies of the four current application universe files used by the generator. |

**Load either the application seed or the full seed, not both.** They intentionally
use the same demo emails. The full version is not an add-on to the application seed.
Neither SQL file includes active pending orders by default, so the worker will not
execute any seeded historical orders when the backend starts.

The full seed adds historical fixture rows only. It does not implement or enable
runtime cash-ledger/audit writes. Your current application will not maintain those
sample ledgers when you perform new operations until your teammate implements that
feature. Dispute APIs exist in this upload; dispute resolution currently does not
reverse the underlying cash transaction or trade.

## Default dataset

| Table | Application seed | Full seed |
| --- | ---: | ---: |
| Roles | Reuses/inserts CLIENT and ADMIN | Same |
| Users | 32 | 32 |
| Accounts | 60 | 60 |
| Account_Cash_Balances | 60 | 60 |
| Securities | 569 catalog entries, reusing matching existing rows | Same |
| Orders | 789 | 789 |
| Order_Reservations | 684 | 684 |
| Executions | 681 | 681 |
| Trades | 582 | 582 |
| Account_Positions | 243 | 243 |
| Transactions | 318 | 318 |
| Cash_Ledger | 0 | 846 |
| Disputes | 0 | 20 |
| Audit_Logs | 0 | 96 |

The 30 clients own one to three accounts each; the two admins have no trading
accounts. Clients 28–30 are suspended. The orders include 582 filled, 51 cancelled,
and 156 rejected orders. There are 304 equity, 92 ETF, 95 forex, and 91 crypto trades.
An execution can be a failed/rejected attempt, so there are more executions than
trades; every filled order still has exactly one successful full execution.

All names, amounts, prices, exchange-rate fixtures, and histories are synthetic.
Prices are marked `synthetic:oltp-seed`, not actual market observations. The reference
date is fixed at `2026-10-15`; rerunning the generator with the same inputs produces
byte-identical SQL. Dates are UTC. Historical fills use weekday session times, but
this is not a holiday-calendar or historical-price backtest.

## Load into the existing operational database

Run the following from your project root. The operational database must already
have the current `initdb/oltp_schema.sql` applied. Do not rerun that schema script
over data you want to keep: its DROP statements reset the database tables.

```bash
docker compose stop app
docker compose up -d postgres-db
docker compose exec -T postgres-db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < test_data/oltp_seed.sql
docker compose exec -T postgres-db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < test_data/validate_oltp_seed.sql
docker compose start app
```

For all-table fixtures, substitute `test_data/oltp_seed_full.sql` on the load line.
If this is a shared database, also pause any other backend/worker instances while
loading and validating. If a load command fails, resolve the error before continuing.
These commands use the credentials/database name inside the running PostgreSQL
container; they do not assume a particular host port or `.env` database name.

In pgAdmin, connect to the **operational database**, open the chosen SQL file in the
Query Tool, and run the complete file. Then run `validate_oltp_seed.sql`. Execute the
whole script, not selected individual statements: temporary ID maps exist only in
that transaction. After an error in the Query Tool, run `ROLLBACK;` before retrying.

Keep this folder outside `initdb/`. The current Dockerfile automatically copies SQL
files from `initdb/` for fresh-database initialization; the two seed variants must
not both run, and schema-before-seed ordering must be explicit. Manual loading also
works with an already-initialized Docker volume.

## Demo logins

Every generated user has the password **`DemoPass123!`**. Its checked-in BCrypt hash
was generated and verified with Spring Security. This is intentionally a shared,
documented test password, not a real-user credential.

| Email | Purpose |
| --- | --- |
| `admin01@paysprint.test` | Active admin; administration and dispute testing |
| `admin02@paysprint.test` | Second admin |
| `client01@paysprint.test` | Active client with a diversified trading account |
| `client02@paysprint.test` | Two accounts: one empty and one cash-only |
| `client03@paysprint.test` | Three accounts: fully sold position, low cash, and diversified |
| `client28@paysprint.test`–`client30@paysprint.test` | Suspended clients; login should be blocked |

Never assume the fixture account numbers in the manifest are actual database IDs.
The SQL obtains new IDs from database sequences. Find real IDs through the API or:

```sql
SELECT u.email, a.account_id, b.cash_balance
FROM users u
JOIN accounts a USING (user_id)
JOIN account_cash_balances b USING (account_id)
WHERE u.email IN ('client01@paysprint.test', 'client02@paysprint.test', 'client03@paysprint.test')
ORDER BY u.email, a.account_id;
```

## Regenerate or change the size

Python is only needed to regenerate files, not to load the supplied SQL.

```bash
# Reproduce the application seed exactly.
python3 test_data/generate_oltp_seed.py

# Reproduce the all-table seed exactly.
python3 test_data/generate_oltp_seed.py --include-ledger --include-audit --include-disputes --output test_data/oltp_seed_full.sql

# Larger, independent group; this can coexist with the original demo emails.
python3 test_data/generate_oltp_seed.py --clients 100 --trades-per-account 30 --namespace cohort2 --seed 42 --output test_data/cohort2.sql

# Include just dispute examples with the application demo data.
python3 test_data/generate_oltp_seed.py --include-disputes --output test_data/with-disputes.sql

# Generate a different historical reference date.
python3 test_data/generate_oltp_seed.py --as-of 2026-10-20 --output test_data/later-snapshot.sql
```

The options `--clients`, `--admins`, `--max-accounts`, `--trades-per-account`,
`--seed`, `--as-of`, `--namespace`, and `--universes-dir` control generation.
Special account scenarios deliberately have fewer fills than `--trades-per-account`.
The generator assembles SQL in memory; use modest values for routine demos, such as
30–100 clients and 12–30 trades per diversified account.

## Optional pending orders

```bash
python3 test_data/generate_oltp_seed.py --include-pending --output test_data/with-pending.sql
```

This adds accepted orders and active cash/share reservations for a few funded,
active clients. There are no fake partial fills or invented IN_EXECUTION leases.
To inspect a stable pending snapshot, set `ORDER_WORKER_ENABLED=false` in `.env`
and recreate the app container with `docker compose up -d --force-recreate app`.
Merely restarting an existing container does not reload its environment settings.
When enabled again, the worker may fill or retry these orders using current market
quotes. The fixed `next_attempt_at` can already be in the past when you load it.

## Repeated loads and existing data

- No DROP, TRUNCATE, DELETE, or sequence reset is included in either seed file.
- Matching existing securities are reused by ticker/exchange; existing names,
  sectors, and statuses are preserved. The catalog compatibility check follows
  the application's strict ticker/type/exchange/currency rules, including case.
- Existing demo emails cause the entire load to fail at
  `seed_precondition_failed`, with `demo_emails_must_be_new` in the failing row.
  This prevents duplicate users and economic histories.
- To load another group without deleting anything, regenerate with a different
  `--namespace`, such as `cohort2`. Changing only `--seed` or `--as-of` does not
  change the emails and therefore does not bypass this check.
- Loading uses one database transaction. Failed transactions can leave gaps in
  sequences, which are normal; existing records are not overwritten.
- The validation SQL checks the entire OLTP database. Existing inconsistent or
  manually entered opening balances/holdings may therefore produce violations
  unrelated to these fixtures. Its reconciliation assumes histories start at zero.

## What the fixtures enforce

Balances are replayed from completed cash operations and trades; failed cash
operations have no cash effect. Buy quantities truncate to 12 decimals, cash rounds
to cents, and average acquisition prices use the rounded cash amount, matching
`OrderExecutionService`. Sells preserve average cost until the position reaches
zero. Generated `Executions.Cash_Amount` is omitted from INSERT statements.

Accepted commitments reserve enough cash or shares; cancellations and execution
rejections release reservations. Upfront rejections never acquire a reservation.
The history includes an overnight wait followed by a fill, a failed quote attempt
followed by a fill, and a security rejected during execution (a historical halt
that may have ended by the snapshot). Foreign-currency executions include the
quote, conversion rate, provenance, and timestamps required by the schema.

The full variant supplies audit examples rather than a complete audit trail of
every fixture operation. Ledger entries cover every completed economic event in
the generated histories. Disputes cover all five statuses and reference the
correct account's trade or transaction.

## Verification performed

Both ready-to-run datasets and a generated pending-order variant were loaded into
isolated PGlite PostgreSQL-compatible databases using your exact schema. Each
passed all 37 consistency checks with exact expected row counts. Additional checks
verified duplicate-load refusal, compatibility with existing/reordered security
IDs, rejection of incompatible or lowercase tickers, and detection of deliberately
corrupted balances/average prices. Both supplied datasets regenerate byte-for-byte.

Your current backend also built successfully with the same `-DskipTests` setting
as its Dockerfile. The packaged Spring application started against the full seed
and passed 18 startup/API checks, including client/admin login, suspended-client
rejection, account ownership, seeded balances/positions/orders/trades/disputes,
and logout. The application seeder recognized all 569 existing securities and
inserted none. The application's full JUnit suite was not rerun for this task.

This environment has no Docker Engine, so these checks did not launch native
PostgreSQL 16 Docker containers or contact Fauxnance. See `verification.json` for
the recorded results and the exact source-schema checksum.
