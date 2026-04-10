package com.contentgrid.appserver.infrastructure.api;

import java.io.InputStream;

/**
 * A single readable entry within an {@link Artifact}.
 */
public interface ArtifactEntry {

    /**
     * Returns the reference identifying this entry and the artifact it belongs to.
     *
     * @return the entry reference
     */
    ArtifactEntryReference getEntryReference();

    /**
     * Opens a stream for reading this entry.
     * <p>
     * The caller is responsible for closing the returned stream after use.
     *
     * @return a stream for reading this entry
     * @throws ArtifactEntryUnreadableException if the stream cannot be opened
     */
    InputStream getInputStream() throws ArtifactEntryUnreadableException;
}
