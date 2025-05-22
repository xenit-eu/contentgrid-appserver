package com.contentgrid.appserver.json;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.json.model.ApplicationSchema;
import java.io.InputStream;

/**
 * Converts a deserialized ApplicationSchema (from JSON) to an Application (domain model).
 */
public interface ApplicationSchemaConverter {
    Application convert(InputStream inputStream);
}
