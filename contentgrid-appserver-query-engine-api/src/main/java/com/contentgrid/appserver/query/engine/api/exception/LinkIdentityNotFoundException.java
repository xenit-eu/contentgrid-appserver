package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.values.EntityId;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Exception thrown when no entity matching the given id was found in the database.
 */
@Getter
@RequiredArgsConstructor
public class LinkIdentityNotFoundException extends QueryEngineException {

    @NonNull
    private final EntityId entityId;
    @NonNull
    private final String entityName;
    @NonNull
    private final PathSegmentName linkPathSegment;

    private final String linkName;

    @Override
    public String getMessage() {
        return "No link '%s' with name '%s' found for entity '%s' with id '%s".formatted(linkPathSegment, linkName, entityName, entityId);
    }
}
