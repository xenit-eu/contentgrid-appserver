package com.contentgrid.appserver.rest.exception;

import com.contentgrid.appserver.domain.values.RelationIdentity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MultipleRelationTargetsException extends Exception {
    private final RelationIdentity relation;
}
