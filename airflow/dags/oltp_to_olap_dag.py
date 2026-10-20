from __future__ import annotations

import sys
import os
from datetime import datetime
from pathlib import Path

from airflow import DAG
from airflow.operators.python import PythonOperator

ETL_ROOT = Path("/opt/etl_process")
os.environ.setdefault("ETL_CONFIG_PATH", "/opt/airflow/config/database.yaml")
if str(ETL_ROOT) not in sys.path:
    sys.path.insert(0, str(ETL_ROOT))


def run_full_seed() -> None:
    from run_full_etl import run

    run()


def run_fact_seed() -> None:
    from seed_facts import run

    run()


def run_incremental_smoke() -> None:
    from run_incremental_smoke import run

    run(timeout_seconds=30)


def run_python_tests() -> None:
    import subprocess

    subprocess.run([sys.executable, "-m", "pytest", "-q"], cwd=ETL_ROOT, check=True)


with DAG(
    dag_id="oltp_to_olap_etl",
    start_date=datetime(2026, 1, 1),
    schedule=None,
    catchup=False,
    tags=["etl", "postgres", "debezium"],
) as dag:
    full_seed = PythonOperator(
        task_id="full_snapshot_seed",
        python_callable=run_full_seed,
    )
    fact_seed = PythonOperator(
        task_id="fact_snapshot_seed",
        python_callable=run_fact_seed,
    )
    incremental_smoke = PythonOperator(
        task_id="incremental_cdc_smoke",
        python_callable=run_incremental_smoke,
    )
    tests = PythonOperator(
        task_id="etl_unit_tests",
        python_callable=run_python_tests,
    )

    full_seed >> fact_seed >> incremental_smoke >> tests