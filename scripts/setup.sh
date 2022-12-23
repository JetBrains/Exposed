#!/bin/bash

BOOTSTRAP_SQL="
CREATE DATABASE exposed_template1;
CREATE USER exposed_template1 WITH ENCRYPTED PASSWORD 'exposed_template1';
ALTER DATABASE exposed_template1 OWNER TO exposed_template1;
ALTER USER exposed_template1 CREATEDB;
"

# Start postgres
echo "Starting Postgres..."
docker pull postgres:latest
POSTGRES_SHA=$(docker ps | grep postgres | cut -d ' ' -f 1) > /dev/null 2>&1

if [ -z "$POSTGRES_SHA" ]
then
  POSTGRES_SHA=$(
    docker run \
      --env POSTGRES_USER=postgres \
      --env POSTGRES_PASSWORD=postgres \
      --detach \
      --restart unless-stopped \
      -p 5432:5432 \
      -d postgres:latest
  )
  sleep 5
fi

echo -e "Postgres is running in container $POSTGRES_SHA\n\n"

# Initialize the DB
echo "Setting up the testing database..."
echo $BOOTSTRAP_SQL | docker exec -i $POSTGRES_SHA psql -U postgres
echo -e "\nDone!"
