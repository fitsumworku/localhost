#!/usr/bin/env bash
set -euo pipefail

# Run from the repository or any directory on the EC2 host.
ETL_DIR="${ETL_DIR:-/home/ec2-user/localhost/etl_process}"
USER_ID=1001
GROUP_ID="e2e-transaction-${USER_ID}-$(date +%s)"

cd "$ETL_DIR"
source venv/bin/activate

ACCOUNT_ID="${1:-$(docker exec postgres_container psql -U postgres -d app -At -c \
  "SELECT account_id FROM accounts WHERE user_id = ${USER_ID} ORDER BY account_id DESC LIMIT 1;") }"
ACCOUNT_ID="$(printf '%s' "$ACCOUNT_ID" | tr -d '[:space:]')"
if [ -z "$ACCOUNT_ID" ]; then
  echo "No account found for user_id=${USER_ID}. Run 01_account_etl_test.sh first." >&2
  exit 1
fi

# Generated seed data may have advanced IDs without advancing the sequence.
docker exec postgres_container psql -U postgres -d app \
  -v ON_ERROR_STOP=1 -qAt -c \
  "SELECT setval(
      pg_get_serial_sequence('transactions', 'transaction_id'),
      COALESCE(MAX(transaction_id), 0) + 1,
      false
   )
   FROM transactions;" >/dev/null

# Start the CDC consumer before creating the deposit.
python -u run_cdc_etl.py \
  --max-messages 1 \
  --group-id "$GROUP_ID" \
  --offset-reset latest \
  --idle-seconds 120 \
  > "/tmp/${GROUP_ID}.log" 2>&1 &
CONSUMER_PID=$!
sleep 10

TRANSACTION_ID=$(docker exec postgres_container psql -U postgres -d app \
  -v ON_ERROR_STOP=1 -At -c \
  "INSERT INTO transactions
      (account_id, client_request_id, transaction_amount, transaction_type,
       transaction_status, completed_at)
   VALUES
      (${ACCOUNT_ID}, gen_random_uuid(), 10000.00, 'DEPOSIT',
       'COMPLETED', clock_timestamp())
   RETURNING transaction_id;")
echo "Created OLTP transaction_id=${TRANSACTION_ID} for account_id=${ACCOUNT_ID}"

wait "$CONSUMER_PID"
cat "/tmp/${GROUP_ID}.log"

echo "OLTP transaction:"
docker exec postgres_container psql -U postgres -d app -c \
  "SELECT transaction_id, account_id, transaction_amount,
          transaction_type, transaction_status
   FROM transactions WHERE transaction_id = ${TRANSACTION_ID};"
echo "OLAP transaction:"
docker exec postgres_olap_container psql -U postgres -d sampleolapdata -c \
  "SELECT transaction_id, user_key, account_key, transaction_type_key,
          transaction_amount, transaction_status
   FROM fact_transactions WHERE transaction_id = ${TRANSACTION_ID};"
