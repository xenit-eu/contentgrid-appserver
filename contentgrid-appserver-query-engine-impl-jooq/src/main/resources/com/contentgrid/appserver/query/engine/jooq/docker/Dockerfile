# 1. Start from the ParadeDB base image. This one already includes the location for the postgresql-server-dev-x dependency.
FROM paradedb/paradedb:0.18.11

# 2. Build and install the dependencies.
RUN apt-get update && apt-get install -y git build-essential postgresql-server-dev-17

# 3. Clone, build, and install pg_roaringbitmap.
RUN git clone https://github.com/ChenHuajun/pg_roaringbitmap
WORKDIR /pg_roaringbitmap
RUN git checkout v0.5.5
RUN make install

# 4. Clone, build, and install pgfaceting.
WORKDIR /
RUN git clone https://github.com/cybertec-postgresql/pgfaceting.git
WORKDIR /pgfaceting
RUN git checkout v0.2.0
RUN make install

# 5. Create a new image based on the ParadeDB base image, but with the extensions installed.
FROM paradedb/paradedb:0.18.11
COPY --from=0 /usr/share/postgresql /usr/share/postgresql

EXPOSE 5432