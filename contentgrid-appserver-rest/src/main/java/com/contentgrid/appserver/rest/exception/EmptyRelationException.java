package com.contentgrid.appserver.rest.exception;

import com.contentgrid.appserver.domain.values.RelationRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class EmptyRelationException extends RuntimeException {
    private final RelationRequest relationRequest;
}
