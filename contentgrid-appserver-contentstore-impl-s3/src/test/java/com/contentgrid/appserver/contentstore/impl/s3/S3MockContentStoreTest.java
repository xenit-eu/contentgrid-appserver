package com.contentgrid.appserver.contentstore.impl.s3;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import io.minio.MinioAsyncClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class S3MockContentStoreTest extends AbstractS3ContentStoreTest {

    @Container
    private static final S3MockContainer s3MockContainer = new S3MockContainer("latest");

    @Override
    protected MinioAsyncClient createClient() {
        return MinioAsyncClient.builder()
                .endpoint(s3MockContainer.getHttpEndpoint())
                .credentials("test", "test")
                .build();
    }
}
