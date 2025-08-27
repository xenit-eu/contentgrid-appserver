package com.contentgrid.appserver.rest.paging;

import lombok.Value;
import org.openapitools.jackson.nullable.JsonNullable;

@Value
public class CursorPageMetadata {

    JsonNullable<String> previousCursor;

    JsonNullable<String> nextCursor;
}