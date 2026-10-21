#!/usr/bin/env bash
set -euo pipefail

# Run from the repository or any directory on the EC2 host.
ETL_DIR="${ETL_DIR:-/home/ec2-user/localhost/etl_process}"
USER_ID=1001
GROUP_ID="e2e-account-${USER_ID}-$(date +%s)"

cd "$ETL_DIR"
source venv/bin/activate

# User 1001 must already exist in both source and target before loading an account.
docker exec postgres_container psql -U postgres -d app -v ON_ERROR_STOP=1 -c \
  "SELECT user_id, name, status FROM users WHERE user_id = ${USER_ID};"
docker exec postgres_olap_container psql -U postgres -d sampleolapdata -v ON_ERROR_STOP=1 -c \
  "SELECT user_id, user_name, status FROM dim_user WHERE user_id = ${USER_ID};"

# Start the CDC consumer before creating the account.
python -u run_cdc_etl.py \
  --max-messages 1 \
  --group-id "$GROUP_ID" \
  --offset-reset latest \
  --idle-seconds 120 \
  > "/tmp/${GROUP_ID}.log" 2>&1 &
CONSUMER_PID=$!
sleep 10

ACCOUNT_ID=$(docker exec postgres_container psql -U postgres -d app \
  -v ON_ERROR_STOP=1 -At -c \
  "INSERT INTO accounts (user_id, currency)
   VALUES (${USER_ID}, 'USD')
   RETURNING account_id;")
echo "Created OLTP account_id=${ACCOUNT_ID} for user_id=${USER_ID}"

wait "$CONSUMER_PID"
cat "/tmp/${GROUP_ID}.log"

echo "OLTP account:"
docker exec postgres_container psql -U postgres -d app -c \
  "SELECT account_id, user_id, currency FROM accounts WHERE account_id = ${ACCOUNT_ID};"

echo "OLAP account:"
docker exec postgres_olap_container psql -U postgres -d sampleolapdata -c \
  "SELECT account_id, user_key, account_status, created_date
   FROM dim_account WHERE account_id = ${ACCOUNT_ID};"
