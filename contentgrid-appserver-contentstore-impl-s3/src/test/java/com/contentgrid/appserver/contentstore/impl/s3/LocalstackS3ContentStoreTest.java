package com.contentgrid.appserver.contentstore.impl.s3;

import io.minio.MinioAsyncClient;
import lombok.SneakyThrows;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class LocalstackS3ContentStoreTest extends AbstractS3ContentStoreTest {

    @Container
    private static final LocalStackContainer localStackContainer = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.7.0"))
            .withServices(Service.S3);

    @SneakyThrows
    @Override
    protected MinioAsyncClient createClient()  {
        return MinioAsyncClient.builder()
                .endpoint(localStackContainer.getEndpoint().toURL())
                .credentials(localStackContainer.getAccessKey(), localStackContainer.getSecretKey())
                .build();
    }
}