package com.contentgrid.appserver.infrastructure.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public abstract class AbstractRemoteArtifact implements Artifact {

    private final Object lock = new Object[0];

    private volatile Artifact delegate;

    @Override
    public Optional<ArtifactEntry> load(Path path) throws ArtifactException {
        return delegate().load(path).map(entry -> {
            var entryRef = ArtifactEntryReference.of(getReference(), entry.getEntryReference().getPath());
            return entry.withEntryReference(entryRef);
        });
    }

    @Override
    public List<ArtifactEntry> loadAll(Path path) throws ArtifactException {
        return delegate().loadAll(path).stream().map(entry -> {
            var entryRef = ArtifactEntryReference.of(getReference(), entry.getEntryReference().getPath());
            return entry.withEntryReference(entryRef);
        }).toList();
    }

    private Artifact delegate() throws ArtifactException {
        if (delegate == null) {
            synchronized (lock) {
                if (delegate == null) {
                    delegate = createDelegate();
                }
            }
        }
        return delegate;
    }

    protected abstract Artifact createDelegate() throws ArtifactException;
}
