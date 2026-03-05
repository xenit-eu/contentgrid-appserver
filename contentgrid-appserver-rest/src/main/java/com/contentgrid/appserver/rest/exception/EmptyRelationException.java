package com.contentgrid.appserver.rest.exception;

import com.contentgrid.appserver.domain.values.RelationIdentity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class EmptyRelationException extends RuntimeException {
    private final RelationIdentity relationIdentity;

    @Override
    public String getMessage() {
        return "%s is empty".formatted(relationIdentity);
    }
}
