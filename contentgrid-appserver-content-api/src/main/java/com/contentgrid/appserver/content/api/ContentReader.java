package com.contentgrid.appserver.content.api;

import java.io.InputStream;

/**
 * A reader to read a specific content object
 */
public interface ContentReader extends ContentAccessor {

    /**
     * Obtain a stream for reading from this content reader
     * <p>
     * After the content has been read, the caller is responsible for properly closing the stream
     * @return A stream for reading the content
     */
    InputStream getContentInputStream() throws UnreadableContentException;
}
