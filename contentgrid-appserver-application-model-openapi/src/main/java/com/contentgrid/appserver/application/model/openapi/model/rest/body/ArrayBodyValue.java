package com.contentgrid.appserver.application.model.openapi.model.rest.body;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;

/**
 * A {@link BodyValue} representing an array field whose items are of a given value type.
 * <p>
 * {@code items} can be any {@link BodyValue}, including {@link ObjectBodyValue} for arrays of
 * objects or {@link SimpleBodyValue} for arrays of scalars.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@RequiredArgsConstructor
public class ArrayBodyValue extends BodyValue {

    @NonNull
    BodyValue items;

}
