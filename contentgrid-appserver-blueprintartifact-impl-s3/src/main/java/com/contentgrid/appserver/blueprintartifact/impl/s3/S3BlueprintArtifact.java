package com.contentgrid.appserver.blueprintartifact.impl.s3;

import com.contentgrid.appserver.blueprintartifact.impl.fs.zip.ZipBlueprintArtifact;
import com.contentgrid.appserver.blueprintartifact.impl.utils.AbstractRemoteBlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReference;
import io.minio.GetObjectArgs;
import io.minio.MinioAsyncClient;
import io.minio.errors.MinioException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class S3BlueprintArtifact extends AbstractRemoteBlueprintArtifact {

    public static final String SCHEME = "s3";

    @NonNull
    private final MinioAsyncClient client;

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

            try (var response = client.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build())
                    .get()) {
                Files.copy(response, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return new ZipBlueprintArtifact(tmpFile);
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new BlueprintArtifactException(ref, e);
        } catch (ExecutionException e) {
            throw new BlueprintArtifactException(ref, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BlueprintArtifactException(ref, e);
        }
    }
}
