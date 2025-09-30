package com.contentgrid.appserver.rest.paging;

import lombok.Value;

@Value
public class CursorPageMetadata {

    String previousCursor;

    String nextCursor;
}