package com.contentgrid.appserver.content.api;

/**
 * An accessor to a stored content object
 */
public sealed interface ContentAccessor permits ContentReader, ContentWriter {
    /**
     * Get the content reference that the accessor is for
     * <p>
     * When creating content using a {@link ContentWriter}; this value is only available after {@link ContentWriter#getContentOutputStream()} has been closed
     * @return The content reference
     */
    ContentReference getReference();

    /**
     * Get the size of the content that the accessor is for
     * <p>
     * When creating content using a {@link ContentWriter}; this value is only available after {@link ContentWriter#getContentOutputStream()} has been closed
     * @return The size of the content in bytes
     */
    long getContentSize();

    /**
     * @return A description for the content accessor; used for error output when working with it
     */
    String getDescription();

}
