package com.contentgrid.appserver.infrastructure.impl.fs.zip;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ZipArtifact implements Artifact {

    private final Path zipPath;

    @Override
    public ArtifactReference getReference() {
        return ArtifactReference.of(ArtifactReference.Scheme.ZIP, zipPath.toAbsolutePath().toString());
    }

    @Override
    public ArtifactEntry load(String path) throws ArtifactException {
        return new ZipArtifactEntry(getReference(), zipPath, path);
    }

    @Override
    public List<ArtifactEntry> loadAll(String path) throws ArtifactException {
        var ref = getReference();
        var prefix = path.isEmpty() ? "" : (path.endsWith("/") ? path : path + "/");
        var result = new ArrayList<ArtifactEntry>();
        try (var zipFile = new ZipFile(zipPath.toFile())) {
            zipFile.entries().asIterator().forEachRemaining(entry -> {
                var name = entry.getName();
                if (name.startsWith(prefix) && !entry.isDirectory()) {
                    result.add(new ZipArtifactEntry(ref, zipPath, name));
                }
            });
        } catch (IOException e) {
            throw new ArtifactException(ref, e);
        }
        return result;
    }
}
