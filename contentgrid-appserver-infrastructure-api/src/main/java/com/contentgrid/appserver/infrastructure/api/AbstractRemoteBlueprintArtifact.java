package com.contentgrid.appserver.infrastructure.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public abstract class AbstractRemoteBlueprintArtifact implements BlueprintArtifact {

    private final Object lock = new Object[0];

    private volatile BlueprintArtifact delegate;

    @Override
    public Optional<BlueprintArtifactItem> load(Path path) throws BlueprintArtifactException {
        return delegate().load(path).map(item -> {
            var itemRef = BlueprintArtifactItemReference.of(getReference(), item.getItemReference().getPath());
            return item.withItemReference(itemRef);
        });
    }

    @Override
    public List<BlueprintArtifactItem> loadAll(Path path) throws BlueprintArtifactException {
        return delegate().loadAll(path).stream().map(item -> {
            var itemRef = BlueprintArtifactItemReference.of(getReference(), item.getItemReference().getPath());
            return item.withItemReference(itemRef);
        }).toList();
    }

    private BlueprintArtifact delegate() throws BlueprintArtifactException {
        if (delegate == null) {
            synchronized (lock) {
                if (delegate == null) {
                    delegate = createDelegate();
                }
            }
        }
        return delegate;
    }

    protected abstract BlueprintArtifact createDelegate() throws BlueprintArtifactException;
}
