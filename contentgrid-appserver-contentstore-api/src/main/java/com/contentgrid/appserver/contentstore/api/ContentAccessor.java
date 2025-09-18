package com.contentgrid.appserver.contentstore.api;

/**
 * An accessor to a stored content object
 */
public interface ContentAccessor {
    /**
     * Get the content reference that the accessor is for
     * <p>
     * @return The content reference
     */
    ContentReference getReference();

    /**
     * Get the size of the content that the accessor is for
     * <p>
     * @return The size of the content in bytes
     */
    long getContentSize();

    /**
     * @return A description for the content accessor; used for error output when working with it
     */
    String getDescription();

}
