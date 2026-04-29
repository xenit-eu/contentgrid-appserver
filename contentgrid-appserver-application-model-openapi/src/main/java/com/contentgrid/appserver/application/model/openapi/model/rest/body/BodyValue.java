package com.contentgrid.appserver.application.model.openapi.model.rest.body;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * Abstract base for all REST body field values. Carries metadata common to every value kind.
 */
@Getter
@EqualsAndHashCode
@ToString
@SuperBuilder(toBuilder = true)
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public abstract sealed class BodyValue
        permits ObjectBodyValue, SimpleBodyValue, RelationBodyValue, ContentBodyValue, ArrayBodyValue {

    String title;
    String description;

    /**
     * Field must be present in the body (but can potentially be set to {@code null}, depending on the {@link #nullable} flag)
     */
    boolean mandatory;

    /**
     * Field is allowed to be set to {@code null} (but it may be required to be present, depending on the {@link #mandatory} flag)
     */
    boolean nullable;

    /**
     * A reference to where this value was generated from
     */
    SourceType sourceType;

    public abstract BodyValueBuilder toBuilder();

    public BodyValue withTitle(String title) {
        return toBuilder().title(title).build();
    }

    public BodyValue withDescription(String description) {
        return toBuilder().description(description).build();
    }

    public BodyValue withMandatory(boolean mandatory) {
        return toBuilder().mandatory(mandatory).build();
    }

    public BodyValue withNullable(boolean nullable) {
        return toBuilder().nullable(nullable).build();
    }

    public BodyValue withSourceType(SourceType sourceType) {
        return toBuilder().sourceType(sourceType).build();
    }
}
