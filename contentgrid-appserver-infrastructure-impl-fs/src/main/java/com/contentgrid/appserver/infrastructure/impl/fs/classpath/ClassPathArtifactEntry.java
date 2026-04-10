package com.contentgrid.appserver.infrastructure.impl.fs.classpath;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import java.io.InputStream;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClassPathArtifactEntry implements ArtifactEntry {

    private final ArtifactEntryReference entryReference;
    private final ClassLoader classLoader;
    private final Path classpathPath;

    @Override
    public ArtifactEntryReference getEntryReference() {
        return entryReference;
    }

    @Override
    public InputStream getInputStream() throws ArtifactEntryUnreadableException {
        return classLoader.getResourceAsStream(classpathPath.toString().replace('\\', '/'));
    }
}
