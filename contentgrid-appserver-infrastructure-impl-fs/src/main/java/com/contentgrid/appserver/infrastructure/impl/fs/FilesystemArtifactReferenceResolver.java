package com.contentgrid.appserver.infrastructure.impl.fs;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactReferenceResolver;
import com.contentgrid.appserver.infrastructure.impl.fs.classpath.ClassPathArtifact;
import com.contentgrid.appserver.infrastructure.impl.fs.directory.FilesystemDirectoryArtifact;
import com.contentgrid.appserver.infrastructure.impl.fs.zip.ZipArtifact;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FilesystemArtifactReferenceResolver implements ArtifactReferenceResolver {

    private final ClassLoader classLoader;

    public FilesystemArtifactReferenceResolver() {
        this(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public Artifact resolve(ArtifactReference reference) {
        var array = reference.toString().split(":");
        if (array.length == 2) {
            var scheme = array[0];
            var path = Path.of(array[1]);
            return switch (scheme) {
                case FilesystemDirectoryArtifact.SCHEME -> new FilesystemDirectoryArtifact(path);
                case ZipArtifact.SCHEME -> new ZipArtifact(path);
                case ClassPathArtifact.SCHEME -> new ClassPathArtifact(classLoader, path);
                default -> null;
            };
        }
        return null;
    }
}
