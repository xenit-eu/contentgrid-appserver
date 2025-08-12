package com.contentgrid.appserver.content.api.range;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class UnsatisfiableContentRangeException extends Exception {
    @NonNull
    private final ContentRangeRequest rangeRequest;
    private final long contentSize;

    public UnsatisfiableContentRangeException(ContentRangeRequest rangeRequest, long contentSize) {
        super("Requested range %s can not be satisfied for content size %d.".formatted(rangeRequest, contentSize));
        this.rangeRequest = rangeRequest;
        this.contentSize = contentSize;
    }
}
