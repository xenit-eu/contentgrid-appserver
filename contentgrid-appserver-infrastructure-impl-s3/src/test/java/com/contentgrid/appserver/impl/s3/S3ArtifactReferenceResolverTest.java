package com.contentgrid.appserver.impl.s3;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import io.minio.MinioAsyncClient;
import org.junit.jupiter.api.Test;

class S3ArtifactReferenceResolverTest {

    // No actual S3 calls are made: S3Artifact downloads lazily, so a dummy endpoint is fine.
    private final MinioAsyncClient client = MinioAsyncClient.builder()
            .endpoint("http://localhost:9000")
            .credentials("test", "test")
            .build();

    private final S3ArtifactReferenceResolver resolver = new S3ArtifactReferenceResolver(client);

    @Test
    void resolve_s3Reference_returnsS3Artifact() {
        var ref = ArtifactReference.of(S3Artifact.SCHEME + ":my-bucket/path/to/artifact.zip");
        assertThat(resolver.resolve(ref)).isInstanceOfSatisfying(S3Artifact.class,
                artifact -> assertThat(artifact.getReference()).isEqualTo(ref));
    }

    @Test
    void resolve_unsupportedScheme_returnsNull() {
        var ref = ArtifactReference.of("file:/some/path");
        assertThat(resolver.resolve(ref)).isNull();
    }
}
