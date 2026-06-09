package com.contentgrid.appserver.blueprintartifact.impl.s3;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.contentgrid.appserver.blueprintartifact.impl.utils.AbstractBlueprintArtifactTest;
import com.contentgrid.appserver.contentstore.impl.utils.testing.S3MockUtils;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class S3BlueprintArtifactTest extends AbstractBlueprintArtifactTest {

    private static final String BUCKET_NAME = "test-blueprint-artifact";
    private static final String OBJECT_KEY = "test.zip";

    @Container
    private static final S3MockContainer S3_MOCK = S3MockUtils.s3MockContainer();

    private static S3BlueprintArtifact blueprintArtifact;

    @BeforeAll
    static void setup() throws Exception {
        var client = MinioAsyncClient.builder()
                .endpoint(S3_MOCK.getHttpEndpoint())
                .credentials("test", "test")
                .build();

        client.makeBucket(MakeBucketArgs.builder()
                .bucket(BUCKET_NAME)
                .build())
                .join();

        var zipBytes = createZip();
        client.putObject(PutObjectArgs.builder()
                .bucket(BUCKET_NAME)
                .object(OBJECT_KEY)
                .stream(new ByteArrayInputStream(zipBytes), zipBytes.length, -1)
                .build())
                .join();

        blueprintArtifact = new S3BlueprintArtifact(client, BUCKET_NAME, OBJECT_KEY);
    }

    private static byte[] createZip() throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(baos)) {
            addEntry(zos, "config/a.yaml", "key: a");
            addEntry(zos, "config/b.yaml", "key: b");
            addEntry(zos, "config/sub/c.yaml", "key: c");
            addEntry(zos, "file.txt", "hello");
        }
        return baos.toByteArray();
    }

    private static void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes());
        zos.closeEntry();
    }

    @Override
    protected BlueprintArtifact getBlueprintArtifact() {
        return blueprintArtifact;
    }
}
