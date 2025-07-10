package com.contentgrid.appserver.content.api;

import java.io.OutputStream;

/**
 * A writer to write a specific content object.
 * <p>
 * Content can only be written once into a writer, after which it becomes immutable.
 */
public non-sealed interface ContentWriter extends ContentAccessor {

    /**
     * Obtain a stream to write data to this content object
     * <p>
     * The caller is responsible for closing the output stream
     * @return A stream to write the content object data into
     */
    OutputStream getContentOutputStream() throws UnwritableContentException;
}
