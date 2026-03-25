## ADDED Requirements

### Requirement: GarageContainer provides S3-compatible storage for tests
The `GarageContainer` class SHALL extend `GenericContainer<GarageContainer>` and provide an S3-compatible object storage backend using the Garage Docker image (`dxflrs/garage`). It SHALL automatically initialize a single-node Garage cluster on startup, including layout assignment, API key creation, and bucket creation.

#### Scenario: Container starts and initializes successfully
- **WHEN** a `GarageContainer` is created and started
- **THEN** the Garage S3 API SHALL be accessible on the mapped port
- **AND** the cluster layout SHALL be assigned and applied
- **AND** an API key SHALL be created

#### Scenario: Creating a bucket via the container
- **WHEN** `createBucket(bucketName)` is called on a started `GarageContainer`
- **THEN** the named bucket SHALL be created in the Garage instance
- **AND** the API key SHALL have read and write permissions on the bucket

### Requirement: GarageContainer exposes S3 connection details
The `GarageContainer` SHALL provide accessor methods to retrieve the S3 endpoint URL, access key, and secret key so that test code can construct an S3 client (e.g., `MinioAsyncClient`) without knowledge of Garage internals.

#### Scenario: Retrieving S3 endpoint URL
- **WHEN** `getS3URL()` is called on a started `GarageContainer`
- **THEN** it SHALL return a URL in the format `http://<host>:<mapped-port>` pointing to the Garage S3 API

#### Scenario: Retrieving access credentials
- **WHEN** `getAccessKey()` and `getSecretKey()` are called on a started `GarageContainer`
- **THEN** they SHALL return the access key ID and secret key of the API key created during initialization

### Requirement: GarageContainer replaces MinIOContainer in all test classes
All test classes that previously used `MinIOContainer` SHALL use `GarageContainer` instead, with no changes to test logic or assertions. The Minio Java SDK (`io.minio:minio`) SHALL remain as the S3 client library.

#### Scenario: MinioS3ContentStoreTest uses GarageContainer
- **WHEN** `MinioS3ContentStoreTest` runs
- **THEN** it SHALL use a `GarageContainer` instance to provide S3 storage
- **AND** the `MinioAsyncClient` SHALL connect using credentials from the `GarageContainer`
- **AND** all existing tests SHALL pass without modification to test logic

#### Scenario: S3ContentStoreWriteFailureTest uses GarageContainer
- **WHEN** `S3ContentStoreWriteFailureTest` runs
- **THEN** it SHALL use a `GarageContainer` instance to provide S3 storage
- **AND** all existing tests SHALL pass without modification to test logic

#### Scenario: InvoicingApiApplicationTest uses GarageContainer
- **WHEN** `InvoicingApiApplicationTest` runs
- **THEN** it SHALL use a `GarageContainer` instance to provide S3 storage
- **AND** Spring dynamic properties SHALL be wired from the `GarageContainer`'s accessor methods
- **AND** all existing tests SHALL pass without modification to test logic

### Requirement: org.testcontainers:minio dependency is removed
The `org.testcontainers:minio` Gradle dependency SHALL be removed from all modules. No code SHALL import from `org.testcontainers.containers.MinIOContainer`.

#### Scenario: Build succeeds without testcontainers minio module
- **WHEN** `./gradlew check` is run
- **THEN** the build SHALL succeed
- **AND** no compile dependency on `org.testcontainers:minio` SHALL exist
