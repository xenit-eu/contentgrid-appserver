package com.contentgrid.appserver.blueprintartifact.impl.utils;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItem;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemReference;
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
            return new DelegateBlueprintArtifactItem(itemRef, item);
        });
    }

    @Override
    public List<BlueprintArtifactItem> loadAll(Path path) throws BlueprintArtifactException {
        return delegate().loadAll(path).stream().map(item -> {
            var itemRef = BlueprintArtifactItemReference.of(getReference(), item.getItemReference().getPath());
            return new DelegateBlueprintArtifactItem(itemRef, item);
        }).map(BlueprintArtifactItem.class::cast).toList();
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
