package com.contentgrid.appserver.contentstore.api.range;

/**
 * Request to read a byte-range of content
 */
public sealed interface ContentRangeRequest permits RangeContentRangeRequest, SuffixContentRangeRequest {

    /**
     * Create a range for the last given number of bytes
     * @param suffixLength The last number of bytes to request
     * @return A range request over the last number of bytes
     */
    static ContentRangeRequest createSuffixRange(long suffixLength) {
        return new SuffixContentRangeRequest(suffixLength);
    }

    /**
     * Create a range from the given position to the end
     * @param firstPos The first byte position
     * @return A range request starting from {@code firstPos} until the end of the content
     */
    static ContentRangeRequest createRange(long firstPos) {
        return new RangeContentRangeRequest(firstPos, null);
    }

    /**
     * Create a range between the first and the last position
     * @param firstPos The first byte position
     * @param lastPos The last byte position (inclusive)
     * @return A range request starting from {@code firstPos} until {@code lastPos} (inclusive)
     */
    static ContentRangeRequest createRange(long firstPos, long lastPos) {
        return new RangeContentRangeRequest(firstPos, lastPos);
    }

    /**
     * Resolves a range request into a fully-specified resolved range
     * @param contentSize The size of the content to resolve the range against
     * @return A resolved range that can be used for exact access
     */
    ResolvedContentRange resolve(long contentSize) throws UnsatisfiableContentRangeException;


}
