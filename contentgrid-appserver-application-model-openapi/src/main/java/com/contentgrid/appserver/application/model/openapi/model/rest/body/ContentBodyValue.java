package com.contentgrid.appserver.application.model.openapi.model.rest.body;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;

/**
 * A {@link BodyValue} representing a file attachment field (derived from a
 * {@link com.contentgrid.appserver.application.model.attributes.ContentAttribute}).
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class ContentBodyValue extends BodyValue {

}
