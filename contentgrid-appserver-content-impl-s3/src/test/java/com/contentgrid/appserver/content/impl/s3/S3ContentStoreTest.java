package com.contentgrid.appserver.content.impl.s3;

import com.contentgrid.appserver.content.impl.utils.testing.AbstractContentStoreBehaviorTest;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import java.util.UUID;
import lombok.Getter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class S3ContentStoreTest extends AbstractContentStoreBehaviorTest {

    @Container
    private static final MinIOContainer minioContainer = new MinIOContainer("minio/minio:RELEASE.2025-07-23T15-54-02Z");

    private static MinioAsyncClient minioClient;

    @Getter
    private S3ContentStore contentStore;

    @BeforeAll
    static void createClient()  {
        minioClient = MinioAsyncClient.builder()
                .endpoint(minioContainer.getS3URL())
                .credentials(minioContainer.getUserName(), minioContainer.getPassword())
                .build();
    }

    @BeforeEach
    void createStore() throws Exception {
        var bucketName = "test-"+ UUID.randomUUID();
        minioClient.makeBucket(MakeBucketArgs.builder()
                .bucket(bucketName)
                .build())
                .join();

        contentStore = new S3ContentStore(
                minioClient,
                bucketName
        );
    }


}