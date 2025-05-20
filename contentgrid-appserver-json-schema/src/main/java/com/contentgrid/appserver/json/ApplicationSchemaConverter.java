package com.contentgrid.appserver.json;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.json.model.ApplicationSchema;

/**
 * Converts a deserialized ApplicationSchema (from JSON) to an Application (domain model).
 */
public interface ApplicationSchemaConverter {
    Application convert(ApplicationSchema schema);
}
