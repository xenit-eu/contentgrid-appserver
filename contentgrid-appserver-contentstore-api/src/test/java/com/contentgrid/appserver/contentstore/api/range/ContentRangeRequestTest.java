package com.contentgrid.appserver.contentstore.api.range;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ContentRangeRequestTest {
    @Test
    void prefixRange() throws UnsatisfiableContentRangeException {
        var range = ContentRangeRequest.createRange(20).resolve(150);
        assertEquals(20, range.getStartByte());
        assertEquals(149, range.getEndByteInclusive());
        assertEquals(150, range.getContentSize());
        assertEquals(130, range.getRangeSize());
    }

    @Test
    void fullPrefixOutOfBounds() {
        assertThrows(UnsatisfiableContentRangeException.class, () -> {
            ContentRangeRequest.createRange(20).resolve(10);
        });
    }

    @Test
    void suffixRange() throws UnsatisfiableContentRangeException {
        var range = ContentRangeRequest.createSuffixRange(50).resolve(150);
        assertEquals(100, range.getStartByte());
        assertEquals(149, range.getEndByteInclusive());
        assertEquals(150, range.getContentSize());
        assertEquals(50, range.getRangeSize());
    }

    @Test
    void suffixRangeOutOfBounds() throws UnsatisfiableContentRangeException {
        var range = ContentRangeRequest.createSuffixRange(50).resolve(10);
        assertEquals(0, range.getStartByte());
        assertEquals(9, range.getEndByteInclusive());
        assertEquals(10, range.getContentSize());
        assertEquals(10, range.getRangeSize());
    }

    @Test
    void fullRange() throws UnsatisfiableContentRangeException {
        var range = ContentRangeRequest.createRange(20, 30).resolve(150);
        assertEquals(20, range.getStartByte());
        assertEquals(30, range.getEndByteInclusive());
        assertEquals(11, range.getRangeSize());
        assertEquals(150, range.getContentSize());
    }

    @Test
    void fullRangeOutOfBoundsEnd() throws UnsatisfiableContentRangeException {
        var range = ContentRangeRequest.createRange(20, 30).resolve(25);
        assertEquals(20, range.getStartByte());
        assertEquals(24, range.getEndByteInclusive());
        assertEquals(5, range.getRangeSize());
        assertEquals(25, range.getContentSize());
    }

    @Test
    void fullRangeOutOfBoundsStart() {
        assertThrows(UnsatisfiableContentRangeException.class, () -> {
            ContentRangeRequest.createRange(20, 30).resolve(10);
        });
    }

    @Test
    void exactlyMatchingRange() throws UnsatisfiableContentRangeException {
        var range = ContentRangeRequest.createRange(0, 14).resolve(15);
        assertEquals(0, range.getStartByte());
        assertEquals(14, range.getEndByteInclusive());
        assertEquals(15, range.getRangeSize());
        assertEquals(15, range.getContentSize());
    }


}