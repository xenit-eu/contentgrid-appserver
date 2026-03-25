package com.contentgrid.appserver.contentstore.impl.s3;

import io.minio.MinioAsyncClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MinioS3ContentStoreTest extends AbstractS3ContentStoreTest {

    @Container
    private static final GarageContainer garageContainer = new GarageContainer();

    @Override
    protected MinioAsyncClient createClient()  {
        return MinioAsyncClient.builder()
                .endpoint(garageContainer.getS3URL())
                .credentials(garageContainer.getAccessKey(), garageContainer.getSecretKey())
                .build();
    }
}
