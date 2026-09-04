FROM postgres:16-alpine

# POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB are supplied at runtime via --env-file .env

# place *.sql or *.sh files here to run them on first container start
COPY ./init/ /docker-entrypoint-initdb.d/

EXPOSE 5432
