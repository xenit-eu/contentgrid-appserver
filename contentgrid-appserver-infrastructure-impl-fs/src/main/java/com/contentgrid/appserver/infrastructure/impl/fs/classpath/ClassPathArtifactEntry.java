package com.contentgrid.appserver.infrastructure.impl.fs.classpath;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import java.io.InputStream;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClassPathArtifactEntry implements ArtifactEntry {

    private final ArtifactReference reference;
    private final ClassLoader classLoader;
    private final Path path;

    @Override
    public InputStream getInputStream() throws ArtifactEntryUnreadableException {
        return classLoader.getResourceAsStream(path.toString().replace('\\', '/'));
    }
}
