package com.contentgrid.appserver.query.engine.api;

import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.version.Version;
import lombok.NonNull;

public record EntityIdAndVersion(
        @NonNull EntityId entityId,
        @NonNull Version version
) {

}
