package com.contentgrid.appserver.blueprintartifact.impl.s3;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.autoconfigure.s3.testing.S3TestClients;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReference;
import org.junit.jupiter.api.Test;

class S3BlueprintArtifactReferenceResolverTest {

    // No actual S3 calls are made: S3Artifact downloads lazily, so a dummy endpoint is fine.
    private final S3BlueprintArtifactReferenceResolver resolver = new S3BlueprintArtifactReferenceResolver(
            S3TestClients.s3AsyncClient("http://localhost:9000"));

    @Test
    void resolve_s3Reference_returnsS3BlueprintArtifact() {
        var ref = BlueprintArtifactReference.of(S3BlueprintArtifact.SCHEME + ":my-bucket/path/to/blueprint-artifact.zip");
        assertThat(resolver.resolve(ref)).isInstanceOfSatisfying(S3BlueprintArtifact.class,
                blueprintArtifact -> assertThat(blueprintArtifact.getReference()).isEqualTo(ref));
    }

    @Test
    void resolve_unsupportedScheme_returnsNull() {
        var ref = BlueprintArtifactReference.of("file:/some/path");
        assertThat(resolver.resolve(ref)).isNull();
    }
}
