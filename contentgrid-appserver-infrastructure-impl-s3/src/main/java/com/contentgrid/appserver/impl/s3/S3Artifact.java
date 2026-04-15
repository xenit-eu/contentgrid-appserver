package com.contentgrid.appserver.impl.s3;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import com.contentgrid.appserver.infrastructure.impl.fs.zip.ZipArtifact;
import io.minio.GetObjectArgs;
import io.minio.MinioAsyncClient;
import io.minio.errors.MinioException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class S3Artifact implements Artifact {

    public static final String SCHEME = "s3";

    @NonNull
    private final MinioAsyncClient client;

    @NonNull
    private final String bucketName;

    @NonNull
    private final String objectKey;

    private volatile ZipArtifact zipArtifact;

    @Override
    public ArtifactReference getReference() {
        return ArtifactReference.of(SCHEME + ":" + bucketName + "/" + objectKey);
    }

    @Override
    public Optional<ArtifactEntry> load(Path path) throws ArtifactException {
        return delegate().load(path);
    }

    @Override
    public List<ArtifactEntry> loadAll(Path path) throws ArtifactException {
        return delegate().loadAll(path);
    }

    private ZipArtifact delegate() throws ArtifactException {
        if (zipArtifact == null) {
            synchronized (this) {
                if (zipArtifact == null) {
                    zipArtifact = download();
                }
            }
        }
        return zipArtifact;
    }

    private ZipArtifact download() throws ArtifactException {
        var ref = getReference();
        try {
            var tmpFile = Files.createTempFile("s3artifact-", ".zip");
            tmpFile.toFile().deleteOnExit();

            try (var response = client.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build())
                    .get()) {
                Files.copy(response, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return new ZipArtifact(tmpFile);
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new ArtifactException(ref, e);
        } catch (ExecutionException e) {
            throw new ArtifactException(ref, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArtifactException(ref, e);
        }
    }
}
