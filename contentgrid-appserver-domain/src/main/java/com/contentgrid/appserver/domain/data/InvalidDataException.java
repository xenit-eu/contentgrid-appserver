package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.PropertyName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.RelationPath;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;

/**
 * Base exception for all exceptions related to data validation
 */
public abstract class InvalidDataException extends Exception {

    /**
     * Consruct an exception that includes the property path where the exception occured
     * @param name The property name where the exception occured at
     * @return A new exception that wraps the current exception
     */
    public InvalidPropertyDataException withinProperty(PropertyName name) {
        var path = switch (name) {
            case AttributeName attributeName -> new SimpleAttributePath(attributeName);
            case RelationName relationName -> new RelationPath(relationName, null);
        };
        return new InvalidPropertyDataException(path, this);
    }

}
