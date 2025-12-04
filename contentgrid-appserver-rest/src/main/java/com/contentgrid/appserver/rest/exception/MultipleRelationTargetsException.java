package com.contentgrid.appserver.rest.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MultipleRelationTargetsException extends Exception {
    private final String relationName;
}
