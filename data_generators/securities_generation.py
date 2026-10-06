import json
from datetime import datetime
from pathlib import Path


def map_asset_type(symbol_type: str) -> str:
    t = (symbol_type or "").strip().lower()
    if t in {"equity", "etf"}:
        return "EQUITY"
    if t in {"fx", "forex"}:
        return "FOREX"
    if t == "crypto":
        return "CRYPTO"
    raise ValueError(f"Unsupported symbol type: {symbol_type}")


def infer_base_currency_for_fx(ticker: str) -> str:
    pair = ticker.split(":", 1)[-1]
    pair = "".join(ch for ch in pair if ch.isalpha()).upper()
    if len(pair) < 6:
        raise ValueError(f"Cannot infer FX base currency from ticker: {ticker}")
    return pair[:3]


def sql_literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, str):
        return "'" + value.replace("'", "''") + "'"
    return str(value)


def build_security_row(symbol: dict) -> dict:
    ticker = str(symbol.get("symbol", "")).strip()
    name = str(symbol.get("name", "")).strip()
    symbol_type = str(symbol.get("type", "")).strip()
    exchange = str(symbol.get("exchange", "")).strip()
    quote_currency = str(symbol.get("currency", "")).strip().upper()

    if not ticker or not name or not symbol_type or not exchange or len(quote_currency) != 3:
        raise ValueError(f"Invalid symbol record: {symbol}")

    asset_type = map_asset_type(symbol_type)
    base_currency = infer_base_currency_for_fx(ticker) if asset_type == "FOREX" else None

    return {
        "Ticker": ticker,
        "Name": name,
        "Asset_Type": asset_type,
        "Exchange": exchange,
        "Quote_Currency": quote_currency,
        "Base_Currency": base_currency,
        "Status": "ACTIVE",
        "Sector": None,
    }


def load_universes(universes_dir: Path):
    rows = []
    seen = set()

    for json_file in sorted(universes_dir.glob("*.json")):
        with json_file.open("r", encoding="utf-8") as f:
            payload = json.load(f)

        symbols = payload.get("symbols", [])
        if not isinstance(symbols, list):
            continue

        for symbol in symbols:
            row = build_security_row(symbol)
            key = (row["Ticker"], row["Exchange"])
            if key in seen:
                continue
            seen.add(key)
            rows.append(row)

    return rows


def generate_sql(rows):
    lines = [
        "-- Securities seed generated from universes/*.json",
        "-- Generator: data_generators/securities_universe_generator.py",
        f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"-- Total records: {len(rows)}",
        "-- Target table: Securities",
        "",
    ]

    for row in rows:
        lines.append(
            "INSERT INTO Securities (Ticker, Name, Asset_Type, Exchange, Quote_Currency, Base_Currency, Status, Sector)"
        )
        lines.append(
            "VALUES ("
            f"{sql_literal(row['Ticker'])}, "
            f"{sql_literal(row['Name'])}, "
            f"{sql_literal(row['Asset_Type'])}, "
            f"{sql_literal(row['Exchange'])}, "
            f"{sql_literal(row['Quote_Currency'])}, "
            f"{sql_literal(row['Base_Currency'])}, "
            f"{sql_literal(row['Status'])}, "
            f"{sql_literal(row['Sector'])}"
            ");"
        )

    return "\n".join(lines) + "\n"


def main():
    repo_root = Path(__file__).resolve().parent.parent
    universes_dir = repo_root / "team" / "src" / "main" / "resources" / "universes"
    output_dir = repo_root / "data"
    output_dir.mkdir(parents=True, exist_ok=True)
    output_sql = output_dir / "securities_insert.sql"

    rows = load_universes(universes_dir)
    sql = generate_sql(rows)

    output_sql.write_text(sql, encoding="utf-8")

    print(f"Loaded {len(rows)} unique securities from: {universes_dir}")
    print(f"Wrote SQL seed file: {output_sql}")


if __name__ == "__main__":
    main()
