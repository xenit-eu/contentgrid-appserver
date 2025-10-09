#!/bin/bash

docker kill contentgrid-paradeb || true
docker container rm contentgrid-paradeb || true
docker volume rm paradedb_data || true
docker build -t contentgrid-paradeb .
docker run \
  --name contentgrid-paradeb \
  -e POSTGRES_USER=myuser \
  -e POSTGRES_PASSWORD=mypassword \
  -e POSTGRES_DB=mydatabase \
  -v paradedb_data:/var/lib/postgresql/data/ \
  --shm-size=512m \
  -p 5432:5432 \
  -d \
  contentgrid-paradeb