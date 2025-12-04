package com.contentgrid.appserver.rest.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MissingRelationTargetException extends Exception {
    private final String relationName;
}
