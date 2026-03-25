## Why

The Minio TestContainer (`org.testcontainers:minio` / `MinIOContainer`) is used across our test suite to provide an S3-compatible backend. Minio is no longer maintained, making it an unreliable dependency for our test infrastructure. Garage is an actively maintained, lightweight S3-compatible storage engine that can serve as a drop-in replacement for test purposes.

## What Changes

- Replace all `MinIOContainer` TestContainer usages with a Garage-based `GenericContainer` configuration
- Remove the `org.testcontainers:minio` dependency from all Gradle build files
- Keep the Minio Java SDK (`io.minio:minio`) unchanged -- it remains our S3 client for both production and test code

## Capabilities

### New Capabilities
- `garage-testcontainer`: A reusable Garage TestContainer setup that provides S3-compatible storage for tests, replacing the Minio TestContainer

### Modified Capabilities

_None -- this is a pure test infrastructure swap. No spec-level behavior changes._

## Impact

- **Test code**: 3 test files declare `MinIOContainer` instances that need replacement:
  - `MinioS3ContentStoreTest.java` (contentstore-impl-s3 module)
  - `S3ContentStoreWriteFailureTest.java` (contentstore-impl-s3 module)
  - `InvoicingApiApplicationTest.java` (integration-test module)
- **Gradle dependencies**: `org.testcontainers:minio` removed from 2 build files (contentstore-impl-s3, integration-test)
- **Production code**: No changes. The Minio Java SDK and all production S3 code remain untouched.
- **CI/Docker**: The Garage Docker image (`dxflrs/garage`) will be pulled instead of `minio/minio`
