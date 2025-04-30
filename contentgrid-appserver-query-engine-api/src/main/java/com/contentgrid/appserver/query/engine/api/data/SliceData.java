package com.contentgrid.appserver.query.engine.api.data;

import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class SliceData {

    @Singular
    List<EntityData> entities;

    @NonNull
    PageInfo pageInfo;

    @Value
    @Builder
    public static class PageInfo {
        Long start;
        Long size;
        Long estimatedCount;
        Long exactCount;
    }
}
