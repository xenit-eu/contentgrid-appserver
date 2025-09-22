package com.contentgrid.appserver.contentstore.api.range;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A content range starting the given number of characters from the end
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
final class SuffixContentRangeRequest implements ContentRangeRequest {
    private final long suffixLength;

    @Override
    public ResolvedContentRange resolve(long contentSize) {
        return new ResolvedContentRangeImpl(
                Math.max(0, contentSize - suffixLength),
                contentSize - 1,
                contentSize
        );
    }

    @Override
    public String toString() {
        return "-%d".formatted(suffixLength);
    }
}
