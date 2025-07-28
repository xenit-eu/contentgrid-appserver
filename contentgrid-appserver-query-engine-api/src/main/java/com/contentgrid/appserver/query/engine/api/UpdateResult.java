package com.contentgrid.appserver.query.engine.api;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import lombok.Value;

/**
 * Result of {@link QueryEngine#update(Application, EntityData)}, containing both the original and the updated data
 */
@Value
public class UpdateResult {

    /**
     * The entity data before the update
     */
    EntityData original;
    /**
     * The entity data after the update
     */
    EntityData updated;
}
