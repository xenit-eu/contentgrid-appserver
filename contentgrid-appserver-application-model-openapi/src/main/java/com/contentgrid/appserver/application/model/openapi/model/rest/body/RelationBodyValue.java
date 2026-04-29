package com.contentgrid.appserver.application.model.openapi.model.rest.body;

import com.contentgrid.appserver.application.model.values.EntityName;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;

/**
 * A {@link BodyValue} representing a field that holds a reference to another entity via a relation.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@RequiredArgsConstructor
public class RelationBodyValue extends BodyValue {

    @NonNull
    EntityName targetEntity;
}
