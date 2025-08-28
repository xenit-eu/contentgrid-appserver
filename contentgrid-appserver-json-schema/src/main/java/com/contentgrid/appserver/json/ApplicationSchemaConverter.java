package com.contentgrid.appserver.json;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.json.exceptions.InvalidJsonException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Converts a deserialized ApplicationSchema (from JSON) to an Application (domain model).
 */
public interface ApplicationSchemaConverter {

    /**
     * Converts the given InputStream containing JSON data into an Application object.
     *
     * @param inputStream the InputStream containing JSON data
     * @return the converted Application object
     * @throws InvalidJsonException if the JSON data is invalid or cannot be converted
     */
    Application convert(InputStream inputStream) throws InvalidJsonException;

    /**
     * Converts the given Application object into JSON and writes it to the provided OutputStream.
     *
     * @param app the Application object to convert
     * @param out the OutputStream to write the JSON data to
     */
    void toJson(Application app, OutputStream out);
}
