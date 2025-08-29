package com.contentgrid.appserver.rest.paging;

import lombok.NonNull;
import lombok.Value;

@Value
public class CursorPageMetadata {

    @NonNull
    String cursor;

    String previousCursor;

    String nextCursor;
}