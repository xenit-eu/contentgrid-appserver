FROM paradedb/paradedb:0.18.11

RUN apt-get update && apt-get install -y git build-essential postgresql-server-dev-17

RUN git clone https://github.com/ChenHuajun/pg_roaringbitmap
WORKDIR /pg_roaringbitmap
RUN git checkout v0.5.5
RUN make install

WORKDIR /
RUN git clone https://github.com/cybertec-postgresql/pgfaceting.git
WORKDIR /pgfaceting
RUN git checkout v0.2.0
RUN make install

EXPOSE 5432