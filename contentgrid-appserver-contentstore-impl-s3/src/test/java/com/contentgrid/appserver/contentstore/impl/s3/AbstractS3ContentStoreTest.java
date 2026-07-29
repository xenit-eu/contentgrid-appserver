package com.contentgrid.appserver.contentstore.impl.s3;

import com.contentgrid.appserver.contentstore.impl.utils.testing.AbstractContentStoreBehaviorTest;
import java.util.UUID;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

abstract class AbstractS3ContentStoreTest extends AbstractContentStoreBehaviorTest {

    @Getter
    private S3ContentStore contentStore;

    protected abstract S3Client createClient();

    @BeforeEach
    void createStore() {
        var client = createClient();
        var bucketName = "test-" + UUID.randomUUID();
        client.createBucket(CreateBucketRequest.builder()
                .bucket(bucketName)
                .build());

        contentStore = new S3ContentStore(
                client,
                bucketName
        );
    }
}
