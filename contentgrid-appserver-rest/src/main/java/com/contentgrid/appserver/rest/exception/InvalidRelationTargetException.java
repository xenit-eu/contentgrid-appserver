package com.contentgrid.appserver.rest.exception;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import java.net.URI;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class InvalidRelationTargetException extends Exception {
    @NonNull
    private final EntityName entityName;

    @NonNull
    private final RelationName relationName;

    @NonNull
    private final URI reference;

    @Override
    public String getMessage() {
        return "'%s' is an invalid value for relation %s on entity %s"
                .formatted(
                        reference,
                        relationName,
                        entityName
                );
    }
}

