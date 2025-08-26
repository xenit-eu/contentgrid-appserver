package com.contentgrid.appserver.query.engine.api.data;

import lombok.Value;

@Value
public class OffsetData implements QueryPageData {
    int limit;
    int offset;

}
