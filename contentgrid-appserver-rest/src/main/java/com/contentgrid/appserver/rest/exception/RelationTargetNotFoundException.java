package com.contentgrid.appserver.rest.exception;

import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import java.util.Arrays;
import java.util.stream.Stream;
import lombok.val;

public class RelationTargetNotFoundException extends Exception {

    public RelationTargetNotFoundException(EntityIdNotFoundException cause) {
        super("Invalid relation target: %s with id %s".formatted(cause.getEntityName().getValue(), cause.getId().getValue()), cause);
    }

    public Stream<EntityIdNotFoundException> allExceptions() {
        val cause = (EntityIdNotFoundException) getCause();
        return Stream.concat(
                Stream.of(cause),
                Arrays.stream(cause.getSuppressed())
                        .filter(EntityIdNotFoundException.class::isInstance)
                        .map(EntityIdNotFoundException.class::cast)
        );
    }

}
