package com.contentgrid.appserver.rest.entity.assembler;

import lombok.Value;

@Value
public class CursorPageMetadata {

    String previousCursor;

    String nextCursor;
}