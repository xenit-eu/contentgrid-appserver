package com.contentgrid.appserver.impl.s3;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactReferenceResolver;
import io.minio.MinioAsyncClient;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class S3ArtifactReferenceResolver implements ArtifactReferenceResolver {

    private final MinioAsyncClient client;

    @Override
    public Artifact resolve(ArtifactReference reference) {
        var parts = reference.toString().split(":");
        if (parts.length == 2 && S3Artifact.SCHEME.equals(parts[0])) {
            var path = parts[1];
            var slashIndex = path.indexOf('/');
            if (slashIndex > 0) {
                var bucketName = path.substring(0, slashIndex);
                var objectKey = path.substring(slashIndex + 1);
                return new S3Artifact(client, bucketName, objectKey);
            }
        }
        return null;
    }
}
