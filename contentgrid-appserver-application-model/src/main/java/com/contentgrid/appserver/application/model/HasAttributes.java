package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.values.AttributeName;
import java.util.List;
import java.util.Optional;

/**
 * Interface for objects that contain attributes and can look them up by name.
 */
public interface HasAttributes {
    Optional<Attribute> getAttributeByName(AttributeName attributeName);
    List<Attribute> getAttributes();
}