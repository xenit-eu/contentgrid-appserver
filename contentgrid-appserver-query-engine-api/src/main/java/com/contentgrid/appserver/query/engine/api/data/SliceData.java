package com.contentgrid.appserver.query.engine.api.data;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class SliceData {

    @Singular
    List<EntityData> entities;
}
