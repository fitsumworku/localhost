#!/usr/bin/env python3
"""Generate deterministic PostgreSQL fixtures for the supplied OLTP schema.

Python 3.10+; standard library only. This writes files, never connects to a DB.
Demo password for every generated user: DemoPass123!
"""
from __future__ import annotations
import argparse
from collections import Counter
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, ROUND_DOWN, ROUND_HALF_UP, getcontext
import hashlib
import json
from pathlib import Path
import random
import re
import uuid

getcontext().prec = 70
D = Decimal
CENT = D('0.01')
UNIT = D('0.000000000001')
ZERO = D('0')
PASSWORD_HASH = '$2a$10$29jzeobe9uBxD3.0TObtO.gaaaBYY9vXeaZcOEb3lZ7Tjn4Q7rbxq'
UNIVERSES = ('us-v1.json', 'india-v1.json', 'fx-v1.json', 'crypto-v1.json')
PRIMARY = {
    'users': 'user_id', 'accounts': 'account_id', 'account_positions': 'position_id',
    'orders': 'order_id', 'executions': 'execution_id', 'trades': 'trade_id',
    'transactions': 'transaction_id', 'cash_ledger': 'ledger_id',
    'disputes': 'dispute_id', 'audit_logs': 'audit_id',
}
TABLES = ('users', 'accounts', 'account_cash_balances', 'orders', 'order_reservations',
          'executions', 'trades', 'account_positions', 'transactions', 'cash_ledger',
          'disputes', 'audit_logs')
REFERENCES = {
    'user_id': 'users', 'account_id': 'accounts', 'security_id': 'securities',
    'order_id': 'orders', 'execution_id': 'executions', 'trade_id': 'trades',
    'transaction_id': 'transactions', 'admin_id': 'users', 'actor_user_id': 'users',
    'subject_user_id': 'users',
}
USD_PER_CURRENCY = {k: D(v) for k, v in {
    'USD': '1', 'INR': '0.012', 'EUR': '1.08', 'GBP': '1.27', 'JPY': '0.0067',
    'CHF': '1.12', 'CAD': '0.74', 'AUD': '0.67', 'NZD': '0.61',
}.items()}


def money(x):
    return D(x).quantize(CENT, rounding=ROUND_HALF_UP)


def units(x, rounding=ROUND_HALF_UP):
    return D(x).quantize(UNIT, rounding=rounding)


def literal(value):
    if value is None:
        return 'NULL'
    if isinstance(value, bool):
        return 'TRUE' if value else 'FALSE'
    if isinstance(value, (int, Decimal)):
        return str(value)
    if isinstance(value, datetime):
        value = value.isoformat()
    if isinstance(value, (dict, list)):
        return literal(json.dumps(value, ensure_ascii=False, sort_keys=True)) + '::jsonb'
    return "'" + str(value).replace("'", "''") + "'"


def load_securities(directory):
    rows, seen = [], set()
    for name in UNIVERSES:
        data = json.loads((directory / name).read_text(encoding='utf-8'))
        if data['version'] != 1:
            raise ValueError('Unsupported universe version: ' + name)
        for item in data['symbols']:
            ticker = item['symbol'].strip().upper()
            if ticker in seen:
                raise ValueError('Duplicate universe ticker: ' + ticker)
            seen.add(ticker)
            asset = {'equity': 'EQUITY', 'etf': 'ETF', 'fx': 'FOREX', 'crypto': 'CRYPTO'}[item['type'].lower()]
            rows.append(dict(ticker=ticker, name=item['name'].strip(), asset_type=asset,
                exchange=item['exchange'].strip().upper(), quote_currency=item['currency'].strip().upper(),
                base_currency=ticker[3:6] if asset == 'FOREX' else None,
                status='ACTIVE', sector=item.get('sector') or None))
    return [dict(security_id=i, **r) for i, r in enumerate(sorted(rows, key=lambda x: x['ticker']), 1)]


class Timeline:
    def __init__(self, start):
        self.day = start

    def next(self, security=None):
        self.day += timedelta(days=1)
        while self.day.weekday() >= 5:
            self.day += timedelta(days=1)
        # Synthetic historical sessions: India at 08:00 UTC, others at 15:00 UTC.
        hour = 8 if security and security['exchange'] in ('NSE', 'BSE') else 15
        return datetime.combine(self.day, datetime.min.time(), timezone.utc).replace(hour=hour)


class Dataset:
    def __init__(self, args, securities):
        self.args = args
        self.rng = random.Random(args.seed)
        self.as_of = datetime.combine(args.as_of, datetime.max.time(), timezone.utc).replace(microsecond=0)
        self.securities = securities
        self.by_id = {r['security_id']: r for r in securities}
        self.rows = {name: [] for name in TABLES}
        self.states = {}
        self.scenarios = []
        self.namespace = (args.namespace + '-') if args.namespace else ''
        self.group = {
            'US': [r for r in securities if r['asset_type'] == 'EQUITY' and r['quote_currency'] == 'USD'],
            'INDIA': [r for r in securities if r['asset_type'] == 'EQUITY' and r['quote_currency'] == 'INR'],
            'ETF': [r for r in securities if r['asset_type'] == 'ETF'],
            'FOREX': [r for r in securities if r['asset_type'] == 'FOREX'],
            'CRYPTO': [r for r in securities if r['asset_type'] == 'CRYPTO'],
        }
        if any(not rows for rows in self.group.values()):
            raise ValueError('All five universe groups (US, India, ETF, FX, crypto) are required')

    def add(self, table, **row):
        if table in PRIMARY:
            row = {PRIMARY[table]: len(self.rows[table]) + 1, **row}
        self.rows[table].append(row)
        return row

    def request_id(self, kind, account, number):
        return str(uuid.uuid5(uuid.NAMESPACE_URL,
            f'paysprint-seed/{self.args.seed}/{self.args.as_of}/{self.namespace}/{kind}/{account}/{number}'))

    def ledger(self, account, kind, amount, time, transaction=None, trade=None):
        if self.args.include_ledger:
            incoming = kind in ('DEPOSIT', 'DIVIDEND', 'INTEREST', 'SELL')
            self.add('cash_ledger', account_id=account, transaction_id=transaction, trade_id=trade,
                entry_type=kind, debit_amount=amount if incoming else ZERO,
                credit_amount=ZERO if incoming else amount,
                running_balance=self.states[account]['cash'], entry_date=time)

    def cash_event(self, account, kind, amount, time, failed=False):
        amount = money(amount)
        state = self.states[account]
        row = self.add('transactions', account_id=account,
            client_request_id=self.request_id('cash', account, len(self.rows['transactions']) + 1),
            transaction_amount=amount, transaction_type=kind,
            transaction_date=time - timedelta(seconds=1),
            transaction_status='FAILED' if failed else 'COMPLETED', completed_at=time,
            failure_reason='INSUFFICIENT_AVAILABLE_CASH' if failed else None)
        if not failed:
            change = amount if kind in ('DEPOSIT', 'DIVIDEND', 'INTEREST') else -amount
            state['cash'] = money(state['cash'] + change)
            assert state['cash'] >= ZERO
            state['cash_updated'] = time
            self.ledger(account, kind, amount, time, transaction=row['transaction_id'])
        return row

    def order(self, account, security, side, value, status, created, accepted, end, attempts=0, reason=None):
        return self.add('orders', account_id=account, security_id=security['security_id'],
            client_request_id=self.request_id('order', account, len(self.rows['orders']) + 1), side=side,
            requested_amount=money(value) if side == 'B' else None,
            quantity_ordered=units(value) if side == 'S' else None, order_status=status,
            created_date=created, updated_date=end or accepted, accepted_at=accepted,
            terminal_at=end, rejection_reason=reason, next_attempt_at=None if end else self.as_of + timedelta(minutes=5),
            execution_token=None, execution_lease_until=None, execution_attempts=attempts,
            last_execution_message='MARKET_CLOSED' if status == 'ACCEPTED' else reason)

    def reserve(self, order, status):
        self.add('order_reservations', order_id=order['order_id'], account_id=order['account_id'],
            security_id=order['security_id'], side=order['side'], reserved_cash=order['requested_amount'],
            reserved_quantity=order['quantity_ordered'], status=status, created_date=order['accepted_at'],
            resolved_at=None if status == 'ACTIVE' else order['terminal_at'])

    def price(self, security):
        symbol = security['ticker']
        native = {'X:BTC-USD': D('60000'), 'X:ETH-USD': D('3000'), 'X:SOL-USD': D('140'),
                  'X:DOGE-USD': D('0.12'), 'X:XRP-USD': D('0.55')}.get(symbol)
        if security['asset_type'] == 'FOREX':
            native = USD_PER_CURRENCY[security['base_currency']] / USD_PER_CURRENCY[security['quote_currency']]
        if native is None:
            native = D(self.rng.randint(500, 4000) if security['quote_currency'] == 'INR' else self.rng.randint(20, 450))
        quoted = units(native * D(self.rng.randint(9200, 10800)) / D(10000))
        rate = units(USD_PER_CURRENCY[security['quote_currency']])
        return quoted, rate, units(quoted * rate)

    def fill(self, account, security, side, value, time, retry=False, overnight=False):
        state = self.states[account]
        sid = security['security_id']
        quoted, rate, usd_price = self.price(security)
        quantity = units(money(value) / usd_price, ROUND_DOWN) if side == 'B' else units(value)
        cash = money(quantity * usd_price)
        previous = state['positions'].get(sid, dict(quantity=ZERO, average=ZERO))
        assert quantity > ZERO and cash > ZERO
        assert state['cash'] >= money(value) if side == 'B' else previous['quantity'] >= quantity
        accepted = time - timedelta(minutes=20)
        if overnight:
            accepted = datetime.combine(time.date() - timedelta(days=1), datetime.min.time(), timezone.utc).replace(hour=22)
        order = self.order(account, security, side, value, 'FILLED', accepted - timedelta(seconds=1),
            accepted, time, attempts=2 if (retry or overnight) else 1)
        self.reserve(order, 'CONSUMED')
        if retry:
            self.add('executions', order_id=order['order_id'], account_id=account, security_id=sid,
                side=side, status_of_execution='FAILED', started_at=accepted + timedelta(seconds=1),
                finished_at=accepted + timedelta(seconds=3), quote_price=None, quote_currency=None,
                quote_timestamp=None, quote_source=None, fx_rate_to_usd=None, fx_quote_timestamp=None,
                fx_source=None, quantity_filled=None, price_of_execution=None,
                failure_reason='RATE_LIMITED', exchange_trade_id=None)
        execution = self.add('executions', order_id=order['order_id'], account_id=account, security_id=sid,
            side=side, status_of_execution='FILLED', started_at=time - timedelta(seconds=1), finished_at=time,
            quote_price=quoted, quote_currency=security['quote_currency'], quote_timestamp=time - timedelta(seconds=30),
            quote_source='synthetic:oltp-seed', fx_rate_to_usd=rate,
            fx_quote_timestamp=None if security['quote_currency'] == 'USD' else time - timedelta(minutes=1),
            fx_source=None if security['quote_currency'] == 'USD' else 'synthetic:oltp-seed-fx',
            quantity_filled=quantity, price_of_execution=usd_price, failure_reason=None, exchange_trade_id=None)
        trade = self.add('trades', execution_id=execution['execution_id'], account_id=account,
            trade_date=time, settlement_date=time)
        new_quantity = previous['quantity'] + quantity if side == 'B' else previous['quantity'] - quantity
        average = ZERO if new_quantity == ZERO else (
            units((previous['quantity'] * previous['average'] + cash) / new_quantity)
            if side == 'B' else previous['average'])
        state['positions'][sid] = dict(quantity=units(new_quantity), average=average, updated=time)
        state['cash'] = money(state['cash'] - cash if side == 'B' else state['cash'] + cash)
        state['cash_updated'] = time
        self.ledger(account, 'BUY' if side == 'B' else 'SELL', cash, time, trade=trade['trade_id'])
        return trade

    def terminal_examples(self, account, security, timeline):
        state = self.states[account]
        end = timeline.next(security)
        accepted = end - timedelta(minutes=1)
        cancelled = self.order(account, security, 'B', D('125'), 'CANCELLED',
            accepted - timedelta(seconds=1), accepted, end)
        self.reserve(cancelled, 'RELEASED')
        end = timeline.next(security)
        self.order(account, security, 'B', state['cash'] + D('1000000'), 'REJECTED', end, None, end,
            reason='INSUFFICIENT_AVAILABLE_CASH')
        end = timeline.next(security)
        position = state['positions'].get(security['security_id'], {'quantity': ZERO})
        self.order(account, security, 'S', position['quantity'] + D('1000'), 'REJECTED', end, None, end,
            reason='INSUFFICIENT_AVAILABLE_QUANTITY')
        end = timeline.next(security)
        accepted = end - timedelta(minutes=1)
        rejected = self.order(account, security, 'B', D('50'), 'REJECTED', accepted - timedelta(seconds=1),
            accepted, end, attempts=1, reason='SECURITY_NOT_TRADABLE')
        self.reserve(rejected, 'RELEASED')
        self.add('executions', order_id=rejected['order_id'], account_id=account, security_id=security['security_id'],
            side='B', status_of_execution='REJECTED', started_at=end - timedelta(seconds=1), finished_at=end,
            quote_price=None, quote_currency=None, quote_timestamp=None, quote_source=None, fx_rate_to_usd=None,
            fx_quote_timestamp=None, fx_source=None, quantity_filled=None, price_of_execution=None,
            failure_reason='SECURITY_NOT_TRADABLE', exchange_trade_id=None)

    def generate(self):
        names = [('Alex', 'Morgan'), ('Priya', 'Shah'), ('Jordan', "O'Neal"), ('Sam', 'Chen'),
                 ('Maya', 'Patel'), ('Noah', 'Williams'), ('Zoë', 'Martin'), ('Avery', 'Jones')]
        for i in range(1, self.args.admins + 1):
            created = self.as_of - timedelta(days=400, minutes=i)
            self.add('users', name=f'Demo Admin {i}', email=f'{self.namespace}admin{i:02}@paysprint.test',
                password_hash=PASSWORD_HASH, role_name='ADMIN', status='ACTIVE', token_version=0,
                created_date=created, updated_date=created)
        for i in range(1, self.args.clients + 1):
            first, last = names[(i - 1) % len(names)]
            suspended = self.args.clients >= 10 and i > self.args.clients - max(1, self.args.clients // 10)
            created = self.as_of - timedelta(days=250, minutes=i)
            user = self.add('users', name=f'{first} {last} {i}', email=f'{self.namespace}client{i:02}@paysprint.test',
                password_hash=PASSWORD_HASH, role_name='CLIENT', status='SUSPENDED' if suspended else 'ACTIVE',
                token_version=1 if suspended else 0, created_date=created,
                updated_date=self.as_of - timedelta(days=2) if suspended else created)
            for j in range(1 + (i - 1) % self.args.max_accounts):
                account = self.add('accounts', user_id=user['user_id'], currency='USD',
                    created_date=created + timedelta(days=j + 1), updated_date=created + timedelta(days=j + 1))
                aid = account['account_id']
                self.states[aid] = dict(cash=ZERO, cash_updated=account['created_date'], positions={}, owner=user)
                timeline = Timeline(self.args.as_of - timedelta(days=150 + self.args.trades_per_account * 2 - (aid % 10)))
                # Scale creation dates for very large requested histories.
                if timeline.day <= account['created_date'].date():
                    account['created_date'] = datetime.combine(timeline.day - timedelta(days=2), datetime.min.time(), timezone.utc)
                    account['updated_date'] = account['created_date']
                    if user['created_date'] >= account['created_date']:
                        user['created_date'] = account['created_date'] - timedelta(days=1)
                        if not suspended:
                            user['updated_date'] = user['created_date']
                    self.states[aid]['cash_updated'] = account['created_date']
                kind = {2: 'EMPTY', 3: 'CASH_ONLY', 4: 'FULLY_SOLD', 5: 'LOW_CASH'}.get(aid % 20, 'DIVERSIFIED')
                self.scenarios.append(dict(fixture_account_id=aid, email=user['email'], scenario=kind))
                if kind == 'EMPTY':
                    continue
                if kind == 'LOW_CASH':
                    self.cash_event(aid, 'DEPOSIT', '25', timeline.next())
                    self.cash_event(aid, 'WITHDRAWAL', '100', timeline.next(), failed=True)
                    time = timeline.next()
                    self.order(aid, self.group['US'][0], 'B', D('100'), 'REJECTED', time, None, time,
                        reason='INSUFFICIENT_AVAILABLE_CASH')
                    continue
                self.cash_event(aid, 'DEPOSIT', D('25000') + D(self.rng.randint(0, 20000)), timeline.next())
                if kind == 'CASH_ONLY':
                    self.cash_event(aid, 'WITHDRAWAL', '500', timeline.next())
                    continue
                if kind == 'FULLY_SOLD':
                    security = self.group['US'][aid % len(self.group['US'])]
                    self.fill(aid, security, 'B', D('1000'), timeline.next(security))
                    self.fill(aid, security, 'S', self.states[aid]['positions'][security['security_id']]['quantity'], timeline.next(security))
                else:
                    choices = [self.rng.choice(self.group[g]) for g in ('US', 'INDIA', 'ETF', 'FOREX', 'CRYPTO')]
                    for n in range(self.args.trades_per_account):
                        if n < 5:
                            security, side, value = choices[n], 'B', D(self.rng.randint(25000, 120000)) / 100
                        elif n == 5:
                            security, side, value = choices[0], 'B', D('400')
                        elif n == 6:
                            security, side = choices[0], 'S'
                            value = units(self.states[aid]['positions'][security['security_id']]['quantity'] / 2, ROUND_DOWN)
                        else:
                            security = self.rng.choice(choices)
                            held = self.states[aid]['positions'].get(security['security_id'], {'quantity': ZERO})['quantity']
                            side = 'S' if held > ZERO and self.rng.random() < 0.4 else 'B'
                            value = units(held / D(self.rng.choice([2, 3, 4])), ROUND_DOWN) if side == 'S' else D(self.rng.randint(25000, 120000)) / 100
                        if side == 'B' and self.states[aid]['cash'] < money(value) + D('1000'):
                            self.cash_event(aid, 'DEPOSIT', '10000', timeline.next())
                        self.fill(aid, security, side, value, timeline.next(security),
                            retry=n == 1, overnight=n == 0)
                for cash_type, amount in [('DIVIDEND', '42.75'), ('INTEREST', '3.25'), ('FEE', '4.99'), ('WITHDRAWAL', '250')]:
                    self.cash_event(aid, cash_type, amount, timeline.next())
                self.cash_event(aid, 'WITHDRAWAL', self.states[aid]['cash'] + D('500'), timeline.next(), failed=True)
                self.terminal_examples(aid, self.group['US'][aid % len(self.group['US'])], timeline)
        if self.args.include_pending:
            self.pending_examples()
        for aid, state in self.states.items():
            self.add('account_cash_balances', account_id=aid, cash_balance=state['cash'], updated_date=state['cash_updated'])
            for sid, p in sorted(state['positions'].items()):
                self.add('account_positions', account_id=aid, security_id=sid, quantity=p['quantity'],
                    average_price=p['average'], updated_date=p['updated'])
        if self.args.include_disputes:
            self.disputes()
        if self.args.include_audit:
            self.audit()
        return self

    def pending_examples(self):
        count = 0
        for aid, state in self.states.items():
            if count >= 3:
                break
            if state['owner']['status'] != 'ACTIVE' or state['cash'] < D('1000') or not state['positions']:
                continue
            security = self.group['US'][0]
            time = (self.as_of - timedelta(days=1)).replace(hour=22, minute=0, second=count)
            buy = self.order(aid, security, 'B', D('100'), 'ACCEPTED', time - timedelta(seconds=1), time, None, attempts=1)
            self.reserve(buy, 'ACTIVE')
            positions = [(sid, p) for sid, p in state['positions'].items() if p['quantity'] > ZERO]
            if positions:
                sid, p = positions[0]
                sell = self.order(aid, self.by_id[sid], 'S', units(p['quantity'] / 4, ROUND_DOWN),
                    'ACCEPTED', time, time + timedelta(seconds=1), None, attempts=1)
                self.reserve(sell, 'ACTIVE')
            count += 1

    def disputes(self):
        statuses = ('OPEN', 'UNDER_REVIEW', 'ESCALATED', 'RESOLVED', 'REJECTED')
        sources = [('trade', r) for r in self.rows['trades'][::max(1, len(self.rows['trades']) // 15)]]
        sources += [('transaction', r) for r in self.rows['transactions'] if r['transaction_status'] == 'COMPLETED' and r['transaction_type'] == 'WITHDRAWAL'][:5]
        for i, (kind, source) in enumerate(sources[:20]):
            status = statuses[i % len(statuses)]
            created = self.as_of - timedelta(days=5, minutes=i)
            self.add('disputes', account_id=source['account_id'], admin_id=None if status == 'OPEN' else 1 + i % self.args.admins,
                transaction_id=source['transaction_id'] if kind == 'transaction' else None,
                trade_id=source['trade_id'] if kind == 'trade' else None,
                dispute_type='EXECUTION_PRICE_QUERY' if kind == 'trade' else 'UNRECOGNIZED_TRANSACTION',
                description='Synthetic demo dispute; resolving this record does not reverse its source.',
                status=status, date_created=created,
                date_resolved=created + timedelta(days=1) if status in ('RESOLVED', 'REJECTED') else None)

    def audit(self):
        for user in self.rows['users']:
            self.add('audit_logs', actor_user_id=user['user_id'], actor_type='USER', subject_user_id=user['user_id'],
                affected_table='users', record_key={'user_id': user['user_id']}, action_type='INSERT', old_value=None,
                new_value={'name': user['name'], 'email': user['email'], 'status': 'ACTIVE', 'role': user['role_name']},
                timestamp=user['created_date'])
            if user['status'] == 'SUSPENDED':
                self.add('audit_logs', actor_user_id=1, actor_type='USER', subject_user_id=user['user_id'],
                    affected_table='users', record_key={'user_id': user['user_id']}, action_type='UPDATE',
                    old_value={'status': 'ACTIVE'}, new_value={'status': 'SUSPENDED'}, timestamp=user['updated_date'])
        for account in self.rows['accounts']:
            self.add('audit_logs', actor_user_id=account['user_id'], actor_type='USER', subject_user_id=account['user_id'],
                affected_table='accounts', record_key={'account_id': account['account_id']}, action_type='INSERT',
                old_value=None, new_value={'currency': 'USD'}, timestamp=account['created_date'])
        # Include a SYSTEM actor fixture with no user actor, as required by the schema.
        first = self.rows['accounts'][0]
        self.add('audit_logs', actor_user_id=None, actor_type='SYSTEM', subject_user_id=first['user_id'],
            affected_table='accounts', record_key={'account_id': first['account_id']}, action_type='SEED_RECONCILIATION',
            old_value=None, new_value={'result': 'complete'}, timestamp=self.as_of)

    def summary(self):
        return dict(generator_version=1, seed=self.args.seed, as_of=str(self.args.as_of), namespace=self.args.namespace,
            demo_password='DemoPass123!', securities=len(self.securities),
            rows={table: len(rows) for table, rows in self.rows.items()},
            order_statuses=dict(Counter(r['order_status'] for r in self.rows['orders'])),
            user_statuses=dict(Counter(r['status'] for r in self.rows['users'])),
            traded_asset_types=dict(Counter(self.by_id[e['security_id']]['asset_type'] for e in self.rows['executions'] if e['status_of_execution'] == 'FILLED')),
            optional_features={name: getattr(self.args, 'include_' + name) for name in ('pending', 'ledger', 'audit', 'disputes')},
            accounts=self.scenarios,
            users=[{k: u[k] for k in ('email', 'role_name', 'status')} for u in self.rows['users']])

    def sql(self):
        out = ['-- Synthetic OLTP test data. No real customers, prices, or market executions.',
               f'-- Seed {self.args.seed}; fixed reference date {self.args.as_of}. Password: DemoPass123!',
               '-- STOP the backend while loading. Run against the existing current OLTP schema.',
               '-- No DROP/TRUNCATE/DELETE; repeated demo emails abort the whole transaction.',
               '-- GENERATED Cash_Amount is deliberately omitted. IDs come from live DB sequences.',
               'BEGIN;', 'SET LOCAL search_path = pg_temp, public;',
               "SET LOCAL TIME ZONE 'UTC';", 'SET LOCAL standard_conforming_strings = on;',
               'CREATE TEMP TABLE _seed_guard (test_name TEXT PRIMARY KEY, ok BOOLEAN NOT NULL,',
               '    CONSTRAINT seed_precondition_failed CHECK (ok)) ON COMMIT DROP;',
               'CREATE TEMP TABLE _seed_emails(email TEXT PRIMARY KEY) ON COMMIT DROP;']
        out.append('INSERT INTO _seed_emails VALUES\n' + ',\n'.join('(' + literal(u['email']) + ')' for u in self.rows['users']) + ';')
        out.append("INSERT INTO _seed_guard SELECT 'demo_emails_must_be_new', NOT EXISTS (SELECT 1 FROM public.users u JOIN _seed_emails s USING(email));")
        out.append("INSERT INTO public.roles(name) VALUES ('CLIENT'),('ADMIN') ON CONFLICT(name) DO NOTHING;")
        out.append('CREATE TEMP TABLE _seed_catalog(fixture_id INT PRIMARY KEY, ticker TEXT, name TEXT, asset_type TEXT, exchange TEXT, quote_currency TEXT, base_currency TEXT, status TEXT, sector TEXT) ON COMMIT DROP;')
        columns = ['security_id', 'ticker', 'name', 'asset_type', 'exchange', 'quote_currency', 'base_currency', 'status', 'sector']
        out.append('INSERT INTO _seed_catalog VALUES\n' + ',\n'.join('(' + ','.join(literal(s[c]) for c in columns) + ')' for s in self.securities) + ';')
        out.append("""INSERT INTO _seed_guard SELECT 'existing_securities_must_match_universes', NOT EXISTS (
    SELECT 1 FROM _seed_catalog f JOIN public.securities s ON upper(s.ticker) = f.ticker
    WHERE s.ticker IS DISTINCT FROM f.ticker OR s.exchange IS DISTINCT FROM f.exchange OR s.asset_type IS DISTINCT FROM f.asset_type
       OR s.quote_currency IS DISTINCT FROM f.quote_currency OR s.base_currency IS DISTINCT FROM f.base_currency);
INSERT INTO public.securities(ticker,name,asset_type,exchange,quote_currency,base_currency,status,sector)
SELECT ticker,name,asset_type,exchange,quote_currency,base_currency,status,sector FROM _seed_catalog ORDER BY fixture_id
ON CONFLICT(ticker,exchange) DO NOTHING;
CREATE TEMP TABLE _seed_securities(fixture_id INT PRIMARY KEY, real_id BIGINT NOT NULL UNIQUE) ON COMMIT DROP;
INSERT INTO _seed_securities SELECT f.fixture_id,s.security_id FROM _seed_catalog f
JOIN public.securities s ON s.ticker=f.ticker AND s.exchange=f.exchange;""")
        for table, pk in PRIMARY.items():
            if not self.rows[table]:
                continue
            out.append(f'CREATE TEMP TABLE _seed_{table}(fixture_id INT PRIMARY KEY, real_id BIGINT NOT NULL UNIQUE) ON COMMIT DROP;')
            out.append(f"INSERT INTO _seed_{table} SELECT i,nextval(pg_get_serial_sequence('public.{table}','{pk}')) FROM generate_series(1,{len(self.rows[table])}) i ORDER BY i;")
        def reference(table, value):
            return f'(SELECT real_id FROM _seed_{table} WHERE fixture_id={int(value)})'
        for table in TABLES:
            rows = self.rows[table]
            if not rows:
                continue
            columns = ['role_id' if c == 'role_name' else c for c in rows[0]]
            values = []
            for row in rows:
                parts = []
                for column, value in row.items():
                    if value is None:
                        parts.append('NULL')
                    elif column == PRIMARY.get(table):
                        parts.append(reference(table, value))
                    elif column in REFERENCES:
                        parts.append(reference(REFERENCES[column], value))
                    elif column == 'role_name':
                        parts.append('(SELECT role_id FROM public.roles WHERE name=' + literal(value) + ')')
                    elif column == 'record_key':
                        key, ident = next(iter(value.items()))
                        parts.append('jsonb_build_object(' + literal(key) + ',' + reference(REFERENCES[key], ident) + ')')
                    else:
                        parts.append(literal(value))
                values.append('(' + ','.join(parts) + ')')
            # Batches keep SQL manageable for pgAdmin and PostgreSQL parsers.
            for offset in range(0, len(values), 200):
                out.append(f"INSERT INTO public.{table}({','.join(columns)}) VALUES\n" + ',\n'.join(values[offset:offset+200]) + ';')
        out.append("""SELECT u.email, r.name AS role, u.status, count(a.account_id) AS accounts
FROM public.users u JOIN _seed_emails s USING(email)
JOIN public.roles r USING(role_id) LEFT JOIN public.accounts a USING(user_id)
GROUP BY u.user_id,u.email,r.name,u.status ORDER BY u.email;
COMMIT;
""")
        return '\n\n'.join(out)


def main():
    here = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--clients', type=int, default=30)
    parser.add_argument('--admins', type=int, default=2)
    parser.add_argument('--max-accounts', type=int, default=3)
    parser.add_argument('--trades-per-account', type=int, default=12, help='Filled trades per diversified account; special scenarios have fewer.')
    parser.add_argument('--seed', type=int, default=20261015)
    parser.add_argument('--as-of', type=date.fromisoformat, default=date(2026, 10, 15))
    parser.add_argument('--namespace', default='', help='Optional prefix, e.g. cohort2, for loading another independent group.')
    parser.add_argument('--universes-dir', type=Path, default=here / 'universes')
    parser.add_argument('--output', type=Path, default=here / 'oltp_seed.sql')
    for feature in ('pending', 'ledger', 'audit', 'disputes'):
        parser.add_argument('--include-' + feature, action='store_true')
    args = parser.parse_args()
    if not (1 <= args.clients <= 10000 and 1 <= args.admins <= 20 and 1 <= args.max_accounts <= 10 and 7 <= args.trades_per_account <= 100):
        parser.error('Use clients 1..10000, admins 1..20, max-accounts 1..10, trades-per-account 7..100.')
    if args.namespace and not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,30}', args.namespace):
        parser.error('namespace must contain 1..31 lowercase letters, digits or hyphens, starting with a letter/digit.')
    dataset = Dataset(args, load_securities(args.universes_dir)).generate()
    sql = dataset.sql()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(sql, encoding='utf-8', newline='\n')
    summary = dataset.summary()
    summary['sql_sha256'] = hashlib.sha256(sql.encode('utf-8')).hexdigest()
    args.output.with_suffix('.manifest.json').write_text(json.dumps(summary, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print('Wrote', args.output)
    print(json.dumps({k: summary[k] for k in ('securities', 'rows', 'order_statuses', 'traded_asset_types')}, indent=2))


if __name__ == '__main__':
    main()
