package com.contentgrid.appserver.infrastructure.impl.fs.classpath;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import java.io.InputStream;
import java.nio.file.Path;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClassPathArtifactEntry implements ArtifactEntry {

    @Getter
    @NonNull
    private final ArtifactEntryReference entryReference;
    @NonNull
    private final ClassLoader classLoader;
    @NonNull
    private final Path classpathPath;

    @Override
    public InputStream getInputStream() throws ArtifactEntryUnreadableException {
        return classLoader.getResourceAsStream(classpathPath.toString().replace('\\', '/'));
    }
}
