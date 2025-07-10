package com.contentgrid.appserver.content.api.range;

import lombok.Getter;

@Getter
final class ResolvedContentRangeImpl implements ResolvedContentRange {
    long startByte;
    long endByteInclusive;
    long contentSize;

    ResolvedContentRangeImpl(long startByte, long endByteInclusive, long contentSize) {
        if(startByte >= contentSize) {
            throw new IllegalArgumentException("startByte must be less than the content size");
        }
        if(endByteInclusive >= contentSize) {
            throw new IllegalArgumentException("endByteInclusive must be less than the content size");
        }
        this.startByte = startByte;
        this.endByteInclusive = endByteInclusive;
        this.contentSize = contentSize;
    }

    @Override
    public String toString() {
        return "%d-%d/%d".formatted(startByte, endByteInclusive, contentSize);
    }
}
