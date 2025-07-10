package com.contentgrid.appserver.content.api.range;

import lombok.SneakyThrows;

/**
 * Resolved content range that can be used to read a part of the content
 */
public interface ResolvedContentRange {

    @SneakyThrows(UnsatisfiableContentRangeException.class)
    static ResolvedContentRange fullRange(long contentSize) {
        return ContentRangeRequest.createRange(0).resolve(contentSize);
    }

    /**
     * @return The byte at which to start reading
     */
    long getStartByte();

    /**
     * @return The byte at which to stop reading
     */
    long getEndByteInclusive();

    /**
     * @return The size of the content range
     */
    default long getRangeSize() {
        return getEndByteInclusive() - getStartByte() + 1;
    }

    /**
     * @return The full size of the content object
     */
    long getContentSize();
}
