package com.contentgrid.appserver.contentstore.api;

/**
 * Unchecked exception wrapping a {@link ContentIOException} for propagation through
 * layers that do not declare checked exceptions.
 */
public class ContentStoreException extends RuntimeException {

    public ContentStoreException(ContentIOException cause) {
        super(cause);
    }

    @Override
    public ContentIOException getCause() {
        return (ContentIOException) super.getCause();
    }
}
