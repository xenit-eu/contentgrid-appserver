## 1. Create GarageContainer class

- [x] 1.1 Create `GarageContainer` class in `contentgrid-appserver-contentstore-impl-s3/src/test/java/com/contentgrid/appserver/contentstore/impl/s3/GarageContainer.java` extending `GenericContainer<GarageContainer>`. Use Docker image `dxflrs/garage:v2.2.0`. Generate a TOML config with `replication_factor = 1`, S3 API on port 3900, and admin API on port 3903. Use `withCopyToContainer()` to mount the config at `/etc/garage.toml`. Expose port 3900.
- [x] 1.2 Override `containerIsStarted()` to run Garage CLI initialization: parse node ID from `garage status`, run `garage layout assign`, `garage layout apply --version 1`, `garage key create`, parse access key and secret key from the output. Store credentials as instance fields.
- [x] 1.3 Add a `createBucket(String bucketName)` method that runs `garage bucket create <name>` and `garage bucket allow --read --write <name> --key <key-name>`.
- [x] 1.4 Add accessor methods: `getS3URL()` (returns `http://<host>:<mapped-port>`), `getAccessKey()`, `getSecretKey()`.

## 2. Update test classes to use GarageContainer

- [x] 2.1 Update `MinioS3ContentStoreTest`: replace `MinIOContainer` with `GarageContainer`. Update `createClient()` to use `getS3URL()`, `getAccessKey()`, `getSecretKey()`. Remove `MinIOContainer` import.
- [x] 2.2 Update `S3ContentStoreWriteFailureTest`: replace `MinIOContainer` with `GarageContainer`. Update `setUp()` to use `GarageContainer` accessors. Remove `MinIOContainer` import. Note: this test creates buckets via the Minio SDK (`client.makeBucket()`); these should continue to work since Garage supports the S3 CreateBucket API. However, if the test's bucket creation via SDK fails, switch to using `garageContainer.createBucket()` instead.
- [x] 2.3 Update `InvoicingApiApplicationTest`: replace `MinIOContainer` with `GarageContainer`. Call `garageContainer.createBucket(BUCKET_NAME)` during container init or in `@DynamicPropertySource`. Update dynamic properties to use `GarageContainer` accessors. Remove `MINIO_DOMAIN` env var (no longer needed). Remove `MinIOContainer` import.

## 3. Update Gradle dependencies

- [x] 3.1 In `contentgrid-appserver-contentstore-impl-s3/build.gradle`: remove `testImplementation "org.testcontainers:minio"`. Keep `implementation 'io.minio:minio'`.
- [x] 3.2 In `contentgrid-appserver-integration-test/build.gradle`: remove `testImplementation 'org.testcontainers:minio'`. Keep `testImplementation 'io.minio:minio'`.

## 4. Verify

- [x] 4.1 Run `./gradlew check` and confirm all tests pass with zero changes to test assertions or production code.
