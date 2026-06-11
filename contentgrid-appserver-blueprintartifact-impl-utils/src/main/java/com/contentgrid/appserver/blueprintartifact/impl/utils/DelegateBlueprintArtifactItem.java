package com.contentgrid.appserver.blueprintartifact.impl.utils;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItem;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemReference;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import java.io.InputStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Wraps a delegate {@link BlueprintArtifactItem} into a new one with a different {@link BlueprintArtifactItemReference}.
 * The underlying {@link InputStream} stays the same.
 */
@RequiredArgsConstructor
public class DelegateBlueprintArtifactItem implements BlueprintArtifactItem {

    @Getter
    private final BlueprintArtifactItemReference itemReference;
    private final BlueprintArtifactItem delegate;

    @Override
    public InputStream getInputStream() throws BlueprintArtifactItemUnreadableException {
        return delegate.getInputStream();
    }
}
