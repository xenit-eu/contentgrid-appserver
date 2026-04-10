package com.contentgrid.appserver.infrastructure.impl.fs.zip;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryNotFoundException;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryReference;
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
    public ArtifactEntry load(Path path) throws ArtifactException {
        var ref = getReference();
        var entryRef = ArtifactEntryReference.of(ref, path.toString());
        try (var zipFile = new ZipFile(zipPath.toFile())) {
            if (zipFile.getEntry(path.toString()) == null) {
                throw new ArtifactEntryNotFoundException(entryRef);
            }
        } catch (IOException e) {
            throw new ArtifactException(ref, e);
        }
        return new ZipArtifactEntry(entryRef, zipPath);
    }

    @Override
    public List<ArtifactEntry> loadAll(Path path) throws ArtifactException {
        var ref = getReference();
        var prefix = path.normalize();
        var result = new ArrayList<ArtifactEntry>();
        try (var zipFile = new ZipFile(zipPath.toFile())) {
            zipFile.entries().asIterator().forEachRemaining(entry -> {
                var entryPath = Path.of(entry.getName());
                if ((prefix.toString().isEmpty() || entryPath.startsWith(prefix)) && !entry.isDirectory()) {
                    result.add(new ZipArtifactEntry(ArtifactEntryReference.of(ref, entry.getName()), zipPath));
                }
            });
        } catch (IOException e) {
            throw new ArtifactException(ref, e);
        }
        return result;
    }
}
