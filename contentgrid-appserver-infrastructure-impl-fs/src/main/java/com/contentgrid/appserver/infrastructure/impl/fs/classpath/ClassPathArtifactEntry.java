package com.contentgrid.appserver.infrastructure.impl.fs.classpath;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClassPathArtifactEntry implements ArtifactEntry {

    @Getter
    @NonNull
    private final ArtifactEntryReference entryReference;
    @NonNull
    private final URL resource;

    @Override
    public InputStream getInputStream() throws ArtifactEntryUnreadableException {
        try {
            return resource.openStream();
        } catch (IOException e) {
            throw new ArtifactEntryUnreadableException(entryReference, e);
        }
    }
}
