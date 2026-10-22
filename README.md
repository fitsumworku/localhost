# localhost

LEAP project
Collaborators: Arjun, Arya, Fitsum, Jacob, Sai
This will be for Friday's group project.
Our branching strategy is Gitflow Style

docker compose stop app

docker compose exec -T postgres-db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < test_data/oltp_seed_full.sql

docker compose exec -T postgres-db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < test_data/validate_oltp_seed.sql

docker compose start app
