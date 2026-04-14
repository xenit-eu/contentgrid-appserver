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
        var path = Path.of(reference.getPath());
        return switch (reference.getScheme()) {
            case FILE -> new FilesystemDirectoryArtifact(path);
            case ZIP -> new ZipArtifact(path);
            case CLASSPATH -> new ClassPathArtifact(classLoader, path);
            default -> null;
        };
    }
}
