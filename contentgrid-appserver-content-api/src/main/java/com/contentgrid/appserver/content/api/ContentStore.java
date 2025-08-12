package com.contentgrid.appserver.content.api;

import com.contentgrid.appserver.content.api.range.ResolvedContentRange;

/**
 * Provides persistence for content objects
 */
public interface ContentStore {

    /**
     * Obtain an object that is used to read content referenced by a given content reference
     * <p>
     * A reader can only be used to read content once.
     * <p>
     * A content range request is an advisory request only. The content reader is not required to satisfy the requested range exactly, as long as at least the range is satisfied.
     * In particular, the content reader may extend the range to align to storage boundaries.
     * If the content is shorter than the end of the range, the content reader will restrict the request to the end of the range
     *
     * @param contentReference The content reference to obtain a reader for
     * @param contentRange The range of the content that is requested to be read.
     * @throws UnreadableContentException When the content can not be read from the reference for any reason
     * @return Reader for accessing content stored in this content reference
     */
    ContentReader getReader(
            ContentReference contentReference,
            ResolvedContentRange contentRange
    ) throws UnreadableContentException;

    /**
     * Obtain an object that is used for writing content
     * The writer is stateful and can only be used to write content once.
     * <p>
     * The store will ensure that the {@link ContentWriter#getReference()} can be used for subsequent read attempts.
     * @return Writer for storing the content
     */
    ContentWriter createNewWriter() throws UnwritableContentException;

    /**
     * Removes the content associated with a given content reference
     *
     * @param contentReference The content reference to remove the content for
     */
    void remove(ContentReference contentReference) throws UnwritableContentException;
}
