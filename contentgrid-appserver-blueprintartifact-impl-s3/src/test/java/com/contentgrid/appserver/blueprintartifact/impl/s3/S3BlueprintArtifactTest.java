package com.contentgrid.appserver.blueprintartifact.impl.s3;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.contentgrid.appserver.blueprintartifact.impl.utils.AbstractBlueprintArtifactTest;
import com.contentgrid.appserver.blueprintartifact.impl.utils.ZipUtils;
import com.contentgrid.appserver.contentstore.impl.utils.testing.S3MockUtils;
import com.contentgrid.appserver.contentstore.impl.utils.testing.S3TestClients;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Testcontainers
class S3BlueprintArtifactTest extends AbstractBlueprintArtifactTest {

    private static final String BUCKET_NAME = "test-blueprint-artifact";
    private static final String OBJECT_KEY = "test.zip";

    @Container
    private static final S3MockContainer S3_MOCK = S3MockUtils.s3MockContainer();

    private static S3BlueprintArtifact blueprintArtifact;

    @BeforeAll
    static void setup() throws Exception {
        var client = S3TestClients.s3AsyncClient(S3_MOCK.getHttpEndpoint());

        client.createBucket(CreateBucketRequest.builder()
                        .bucket(BUCKET_NAME)
                        .build())
                .join();

        var zipBytes = createZip();
        client.putObject(PutObjectRequest.builder()
                                .bucket(BUCKET_NAME)
                                .key(OBJECT_KEY)
                                .build(),
                        AsyncRequestBody.fromBytes(zipBytes))
                .join();

        blueprintArtifact = new S3BlueprintArtifact(client, BUCKET_NAME, OBJECT_KEY);
    }

    private static byte[] createZip() throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(baos)) {
            ZipUtils.addEntry(zos, "config/a.yaml", "key: a");
            ZipUtils.addEntry(zos, "config/b.yaml", "key: b");
            ZipUtils.addEntry(zos, "config/sub/c.yaml", "key: c");
            ZipUtils.addEntry(zos, "file.txt", "hello");
        }
        return baos.toByteArray();
    }

    @Override
    protected BlueprintArtifact getBlueprintArtifact() {
        return blueprintArtifact;
    }
}
