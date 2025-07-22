package com.contentgrid.appserver.rest.mapping;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import java.util.stream.Stream;
import lombok.NonNull;

/**
 * Generates {@link ReplacementPathVariableValues} for a specific application
 * <p>
 * Specific implementations are used in the {@link SpecializedOnPropertyType.PropertyType} to generate replacement values for that specific type
 */
interface ReplacementPathVariablesGenerator {

    /**
     * @param application The application to generate replacement path variable values for
     * @return Replacement values for path variables
     */
    Stream<ReplacementPathVariableValues> generateForApplication(Application application);

    /**
     * Replacement values to use for path variables to specialize a path pattern
     *
     * @param entityPathSegment The replacement value for the {@link SpecializedOnPropertyType#entityPathVariable()}
     * @param propertyPathSegment The replacement value for the {@link SpecializedOnPropertyType#propertyPathVariable()}
     */
    record ReplacementPathVariableValues(
            @NonNull PathSegmentName entityPathSegment,
            @NonNull PathSegmentName propertyPathSegment
    ) {

    }
}
