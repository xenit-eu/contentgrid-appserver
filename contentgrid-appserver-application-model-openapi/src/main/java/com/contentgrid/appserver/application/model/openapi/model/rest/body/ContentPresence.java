package com.contentgrid.appserver.application.model.openapi.model.rest.body;

import com.contentgrid.appserver.application.model.propertypath.AttributePath;

/**
 * Tells whether content is stored in a content attribute of an entity instance.
 */
@FunctionalInterface
public interface ContentPresence {

    ContentPresence ALWAYS = attributePath -> true;

    boolean hasContent(AttributePath attributePath);
}
