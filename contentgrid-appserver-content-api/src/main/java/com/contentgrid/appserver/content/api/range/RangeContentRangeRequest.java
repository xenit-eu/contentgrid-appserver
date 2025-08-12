package com.contentgrid.appserver.content.api.range;

import lombok.Getter;

/**
 * A content range from the given start byte until the end byte (or the end of the content)
 */
@Getter
final class RangeContentRangeRequest implements ContentRangeRequest {
    private final long startByte;
    private final Long endByteInclusive;

    RangeContentRangeRequest(long startByte, Long endByteInclusive) {
        if(startByte < 0) {
            throw new IllegalArgumentException("First byte position must be positive");
        }
        if(endByteInclusive != null && endByteInclusive < startByte) {
            throw new IllegalArgumentException("First byte position should be less than or equal to last byte position");
        }
        this.startByte = startByte;
        this.endByteInclusive = endByteInclusive;
    }

    @Override
    public ResolvedContentRange resolve(long contentSize) throws UnsatisfiableContentRangeException {
        if(startByte >= contentSize) {
            throw new UnsatisfiableContentRangeException(this, contentSize);
        }
        if(this.endByteInclusive == null || endByteInclusive > contentSize) {
            return new ResolvedContentRangeImpl(
                    startByte,
                    contentSize - 1,
                    contentSize
            );
        }
        return new ResolvedContentRangeImpl(
                startByte,
                endByteInclusive,
                contentSize
        );
    }

    @Override
    public String toString() {
        if(endByteInclusive != null) {
            return "%d-%d".formatted(startByte, endByteInclusive);
        }
        return "%d-".formatted(startByte);
    }
}
