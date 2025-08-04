package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.query.engine.api.data.EntityData;
import java.util.List;
import lombok.Getter;
import lombok.Value;

@Value
public class EntityPage {

    @Getter
    List<EntityData> items;


}
