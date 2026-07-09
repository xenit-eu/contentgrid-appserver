package com.contentgrid.appserver.application.model.propertypath;

import com.contentgrid.appserver.application.model.propertypath.PropertyPath.CrossesAttribute;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.ResolvesToAttribute;
import com.contentgrid.appserver.application.model.values.AttributeName;
import lombok.NonNull;

/**
 * A property path that crosses only attributes and resolves to in an attribute on the current entity
 */
public sealed interface AttributePath extends CrossesAttribute, ResolvesToAttribute permits SimpleAttributePath, CompositeAttributePath {
    @NonNull
    AttributeName getFirst();
    AttributePath getRest();

    AttributePath withSuffix(AttributeName attributeName);

}
