package com.contentgrid.appserver.rest.exception;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ContentNotFoundException extends RuntimeException {
    @NonNull
    private final EntityIdentity entityIdentity;
    @NonNull
    private final AttributeName attributeName;

}
