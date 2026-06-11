package com.contentgrid.appserver.domain.spi.blueprintartifact;

import java.io.InputStream;

/**
 * A single readable item within a {@link BlueprintArtifact}.
 */
public interface BlueprintArtifactItem {

    /**
     * Returns the reference identifying this item and the blueprint artifact it belongs to.
     *
     * @return the item reference
     */
    BlueprintArtifactItemReference getItemReference();

    /**
     * Opens a stream for reading this item.
     * <p>
     * The caller is responsible for closing the returned stream after use.
     *
     * @return a stream for reading this item
     * @throws BlueprintArtifactItemUnreadableException if the stream cannot be opened
     */
    InputStream getInputStream() throws BlueprintArtifactItemUnreadableException;
}
