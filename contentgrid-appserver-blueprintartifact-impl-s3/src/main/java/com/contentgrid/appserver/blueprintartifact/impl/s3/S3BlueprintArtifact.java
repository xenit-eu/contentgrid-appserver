package com.contentgrid.appserver.blueprintartifact.impl.s3;

import com.contentgrid.appserver.blueprintartifact.impl.fs.zip.ZipBlueprintArtifact;
import com.contentgrid.appserver.blueprintartifact.impl.utils.AbstractRemoteBlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CompletionException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@RequiredArgsConstructor
public class S3BlueprintArtifact extends AbstractRemoteBlueprintArtifact {

    public static final String SCHEME = "s3";

    @NonNull
    private final S3AsyncClient client;

    @NonNull
    private final String bucketName;

    @NonNull
    private final String objectKey;

    @Override
    public BlueprintArtifactReference getReference() {
        return BlueprintArtifactReference.of(SCHEME + ":" + bucketName + "/" + objectKey);
    }

    @Override
    protected BlueprintArtifact createDelegate() throws BlueprintArtifactException {
        var ref = getReference();
        try {
            var tmpFile = Files.createTempFile("s3-blueprint-artifact-", ".zip",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            tmpFile.toFile().deleteOnExit();

            try (var response = client.getObject(GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .build(), AsyncResponseTransformer.toBlockingInputStream())
                    .join()) {
                Files.copy(response, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return new ZipBlueprintArtifact(tmpFile);
        } catch (CompletionException e) {
            throw new BlueprintArtifactException(ref, e.getCause());
        } catch (RuntimeException | IOException e) {
            throw new BlueprintArtifactException(ref, e);
        }
    }
}
