FROM apache/airflow:2.10.5-python3.11

COPY etl_process/requirements.txt /tmp/etl-requirements.txt
RUN pip install --no-cache-dir -r /tmp/etl-requirements.txt