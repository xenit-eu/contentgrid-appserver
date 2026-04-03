package com.contentgrid.appserver.infrastructure.api;

import java.io.InputStream;

/**
 * A single readable entry within an {@link Artifact}.
 */
public interface ArtifactEntry {

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
