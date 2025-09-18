package com.contentgrid.appserver.contentstore.impl.s3;

import io.minio.MinioAsyncClient;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MinioS3ContentStoreTest extends AbstractS3ContentStoreTest {

    @Container
    private static final MinIOContainer minioContainer = new MinIOContainer("minio/minio:RELEASE.2025-07-23T15-54-02Z");

    @Override
    protected MinioAsyncClient createClient()  {
        return MinioAsyncClient.builder()
                .endpoint(minioContainer.getS3URL())
                .credentials(minioContainer.getUserName(), minioContainer.getPassword())
                .build();
    }
}