package com.contentgrid.appserver.blueprintartifact.impl.s3;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReference;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReferenceResolver;
import io.minio.MinioAsyncClient;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class S3BlueprintArtifactReferenceResolver implements BlueprintArtifactReferenceResolver {

    private final MinioAsyncClient client;

    @Override
    public BlueprintArtifact resolve(BlueprintArtifactReference reference) {
        var parts = reference.toString().split(":");
        if (parts.length == 2 && S3BlueprintArtifact.SCHEME.equals(parts[0])) {
            var path = parts[1];
            var slashIndex = path.indexOf('/');
            if (slashIndex > 0) {
                var bucketName = path.substring(0, slashIndex);
                var objectKey = path.substring(slashIndex + 1);
                return new S3BlueprintArtifact(client, bucketName, objectKey);
            }
        }
        return null;
    }
}
