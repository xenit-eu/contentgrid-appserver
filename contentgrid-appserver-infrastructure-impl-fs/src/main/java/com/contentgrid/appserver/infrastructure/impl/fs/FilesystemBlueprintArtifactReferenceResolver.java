package com.contentgrid.appserver.infrastructure.impl.fs;

import com.contentgrid.appserver.infrastructure.api.BlueprintArtifact;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactReference;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactReferenceResolver;
import com.contentgrid.appserver.infrastructure.impl.fs.classpath.ClassPathBlueprintArtifact;
import com.contentgrid.appserver.infrastructure.impl.fs.directory.FilesystemDirectoryBlueprintArtifact;
import com.contentgrid.appserver.infrastructure.impl.fs.zip.ZipBlueprintArtifact;
import java.nio.file.Path;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FilesystemBlueprintArtifactReferenceResolver implements BlueprintArtifactReferenceResolver {

    @NonNull
    private final ClassLoader classLoader;

    public FilesystemBlueprintArtifactReferenceResolver() {
        this(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public BlueprintArtifact resolve(BlueprintArtifactReference reference) {
        var array = reference.toString().split(":");
        if (array.length == 2) {
            var scheme = array[0];
            var path = Path.of(array[1]);
            return switch (scheme) {
                case FilesystemDirectoryBlueprintArtifact.SCHEME -> new FilesystemDirectoryBlueprintArtifact(path);
                case ZipBlueprintArtifact.SCHEME -> new ZipBlueprintArtifact(path);
                case ClassPathBlueprintArtifact.SCHEME -> new ClassPathBlueprintArtifact(classLoader, path);
                default -> null;
            };
        }
        return null;
    }
}
