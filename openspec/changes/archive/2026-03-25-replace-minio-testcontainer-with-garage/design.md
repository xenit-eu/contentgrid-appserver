## Context

Three test classes currently use `org.testcontainers.containers.MinIOContainer` to spin up a Minio S3-compatible server for testing:

1. `MinioS3ContentStoreTest` -- unit-level S3 content store tests
2. `S3ContentStoreWriteFailureTest` -- write failure scenario tests
3. `InvoicingApiApplicationTest` -- full integration test with Spring Boot

The `MinIOContainer` is a convenience class from the `org.testcontainers:minio` module that wraps a `GenericContainer` and provides helper methods (`getS3URL()`, `getUserName()`, `getPassword()`). Minio is no longer maintained, so we need to replace it with Garage (`dxflrs/garage`), an actively maintained S3-compatible object store.

Garage requires more initialization than Minio: after starting the container, we must configure a cluster layout, create an API key, create a bucket, and grant permissions -- all via the Garage CLI (`/garage` binary inside the container). Minio auto-creates buckets on first access via the S3 API.

## Goals / Non-Goals

**Goals:**
- Replace `MinIOContainer` with a custom `GarageContainer` extending `GenericContainer` from TestContainers
- Encapsulate all Garage initialization (layout, key, bucket) inside the container class so test code remains simple
- Maintain identical S3 client behavior -- all tests continue using `MinioAsyncClient` unchanged
- Remove the `org.testcontainers:minio` dependency

**Non-Goals:**
- Replacing the Minio Java SDK (`io.minio:minio`) -- it stays as the S3 client
- Changing production code
- Supporting multi-node Garage clusters in tests
- Supporting Garage's K2V or web gateway features

## Decisions

### 1. Create a `GarageContainer` utility class

**Decision**: Create a `GarageContainer` class extending `GenericContainer<GarageContainer>` that handles all Garage initialization.

**Rationale**: Garage requires multi-step initialization (layout assignment, layout apply, key creation, bucket creation, permission granting) that would be noisy to duplicate across test classes. A reusable container class keeps test code clean and mirrors the convenience of `MinIOContainer`.

**Alternative considered**: Using raw `GenericContainer` with initialization in each test class. Rejected because it would duplicate 20+ lines of init code across 3 test files and be error-prone.

**Location**: Place in the `contentgrid-appserver-contentstore-impl-s3` test sources, since that's the lowest module that needs it. The integration-test module already depends on this module and can reuse it.

### 2. Garage initialization via `execInContainer`

**Decision**: Use TestContainers' `execInContainer()` to run `/garage` CLI commands during container startup (in a `withStartupCheckStrategy` or overridden `containerIsStarted` callback).

**Rationale**: Garage ships with a full CLI at `/garage` inside the Docker image. Using the CLI is the documented way to set up Garage and avoids coupling to the admin HTTP API. The CLI uses internal RPC to communicate with the running server, so no additional port mapping is needed.

**Commands to run after container start**:
1. `garage status` -- get the node ID
2. `garage layout assign -z dc1 -c 1G <node-id>` -- assign the node to a layout
3. `garage layout apply --version 1` -- apply the layout
4. `garage key create <key-name>` -- create an API key (captures access key + secret from output)
5. `garage bucket create <bucket-name>` -- create the test bucket
6. `garage bucket allow --read --write <bucket-name> --key <key-name>` -- grant permissions

### 3. Configuration via environment + generated TOML

**Decision**: Mount a Garage configuration file via `withCopyToContainer()` that sets up a single-node Garage instance with `replication_factor = 1`, S3 API on port 3900, and admin API on port 3903.

**Rationale**: Garage requires a TOML config file at startup. Using `withCopyToContainer()` from Transferable strings is clean and avoids needing external test resource files.

### 4. Port mapping

**Decision**: Expose port 3900 (S3 API) as the primary mapped port. The admin port (3903) does not need to be exposed since we use `execInContainer` for all admin operations.

### 5. S3 endpoint URL uses path-style access

**Decision**: Use path-style bucket access (`http://host:port/bucket/key`) rather than virtual-hosted-style (`http://bucket.host:port/key`).

**Rationale**: Path-style access works without DNS configuration. The Minio Java SDK defaults to path-style access. The integration test currently sets `MINIO_DOMAIN=localhost` on the Minio container to enable virtual-hosted-style, but the Spring properties wire the S3 URL directly, so path-style will work identically for all test scenarios.

### 6. Keep `testImplementation 'io.minio:minio'` in integration-test module

**Decision**: The `io.minio:minio` dependency remains in both modules since it's the S3 client SDK, not related to the container.

## Risks / Trade-offs

- **[Garage startup time]** Garage's initialization (layout + key + bucket via CLI) adds several exec calls during container startup. This may add 1-2 seconds compared to Minio. Mitigation: container is `static` and shared across all tests in a class, so startup cost is amortized.

- **[S3 compatibility gaps]** Garage may not support every S3 operation that Minio does. Mitigation: Our usage is limited to `putObject`, `getObject`, `removeObject`, `makeBucket`, and `removeBucket` -- all well-supported by Garage.

- **[CLI output parsing]** We need to parse the `garage key create` output to extract the access key and secret key. Mitigation: The output format is stable and well-documented; we parse it with simple string matching.

- **[Docker image availability]** `dxflrs/garage` is hosted on Docker Hub. Mitigation: The image is actively maintained (updated within the last 24 hours) and has 1M+ pulls.
