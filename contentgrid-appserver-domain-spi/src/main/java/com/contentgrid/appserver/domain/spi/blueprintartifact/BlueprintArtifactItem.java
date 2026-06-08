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

    /**
     * Returns a new {@link BlueprintArtifactItem} with the provided {@code itemReference}.
     * The underlying {@link InputStream} stays the same.
     *
     * @param itemReference the new reference
     * @return an item with the updated {@code itemReference}
     */
    default BlueprintArtifactItem withItemReference(BlueprintArtifactItemReference itemReference) {
        return new BlueprintArtifactItem() {
            @Override
            public BlueprintArtifactItemReference getItemReference() {
                return itemReference;
            }

            @Override
            public InputStream getInputStream() throws BlueprintArtifactItemUnreadableException {
                return BlueprintArtifactItem.this.getInputStream();
            }
        };
    }
}
