package com.contentgrid.appserver.infrastructure.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public abstract class AbstractRemoteArtifact implements Artifact {

    private volatile Artifact delegate;

    @Override
    public Optional<ArtifactEntry> load(Path path) throws ArtifactException {
        return delegate().load(path);
    }

    @Override
    public List<ArtifactEntry> loadAll(Path path) throws ArtifactException {
        return delegate().loadAll(path);
    }

    private Artifact delegate() throws ArtifactException {
        if (delegate == null) {
            synchronized (this) {
                if (delegate == null) {
                    delegate = createDelegate();
                }
            }
        }
        return delegate;
    }

    protected abstract Artifact createDelegate() throws ArtifactException;
}
