FROM postgres:18

# Install build dependencies.
RUN apt-get update && apt-get install -y git build-essential postgresql-server-dev-18

# Install pg_roaringbitmap extension.
RUN git clone https://github.com/ChenHuajun/pg_roaringbitmap
WORKDIR /pg_roaringbitmap
RUN git checkout v0.5.5
RUN make install

# Install pgfaceting extension.
WORKDIR /
RUN git clone https://github.com/cybertec-postgresql/pgfaceting.git
WORKDIR /pgfaceting
RUN git checkout v0.2.0
RUN make install

# Cleanup.
FROM postgres:18
WORKDIR /
COPY --from=0 /usr/lib/postgresql/18/lib/ /usr/lib/postgresql/18/lib/
COPY --from=0 /usr/share/postgresql/18/extension/ /usr/share/postgresql/18/extension/

EXPOSE 5432