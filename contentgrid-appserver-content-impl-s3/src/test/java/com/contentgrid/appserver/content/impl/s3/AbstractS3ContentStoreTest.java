package com.contentgrid.appserver.content.impl.s3;

import com.contentgrid.appserver.content.impl.utils.testing.AbstractContentStoreBehaviorTest;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import java.util.UUID;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractS3ContentStoreTest extends AbstractContentStoreBehaviorTest {

    @Getter
    private S3ContentStore contentStore;

    protected abstract MinioAsyncClient createClient();

    @BeforeEach
    void createStore() throws Exception {
        var client = createClient();
        var bucketName = "test-"+ UUID.randomUUID();
        client.makeBucket(MakeBucketArgs.builder()
                .bucket(bucketName)
                .build())
                .join();

        contentStore = new S3ContentStore(
                client,
                bucketName
        );
    }
}
