package com.contentgrid.appserver.rest.property.handler;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.exception.UnsupportedMediaTypeException;
import com.contentgrid.appserver.rest.exception.PropertyNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

public interface PropertyRequestHandler {

    /**
     * Http Get on the property. Returns a {@link ResponseEntity} containing the property.
     * Might throw if the property exists, but accessing the property fails for a different reason.
     *
     * @param application the application context
     * @param entity the entity
     * @param instanceId the primary key value of the entity
     * @param propertyName the path segment of the property
     * @return a {@link ResponseEntity} containing the result
     * @throws PropertyNotFoundException if the property does not exist.
     */
    ResponseEntity<Object> getProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName
    ) throws PropertyNotFoundException;

    /**
     * HTTP Post on the property. Returns a {@link ResponseEntity} if successful.
     * Might throw if the property exists, but posting to it fails for a different reason.
     *
     * @param application the application context
     * @param entity the entity
     * @param instanceId the primary key value of the entity
     * @param propertyName the path segment of the property
     * @param request the request, can be used to look up the body, query parameters, form-data parts, ...
     * @return a {@link ResponseEntity} containing the result
     * @throws PropertyNotFoundException if the property does not exist.
     * @throws UnsupportedMediaTypeException if the property exists,
     *         but the content-type of the request is not supported.
     */
    ResponseEntity<Object> postProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            HttpServletRequest request
    ) throws PropertyNotFoundException, UnsupportedMediaTypeException;

    /**
     * HTTP Put on the property. Returns a {@link ResponseEntity} if successful.
     * Might throw if the property exists, but performing a put fails for a different reason.
     *
     * @param application the application context
     * @param entity the entity
     * @param instanceId the primary key value of the entity
     * @param propertyName the path segment of the property
     * @param request the request, can be used to look up the body, query parameters, form-data parts, ...
     * @return a {@link ResponseEntity} containing the result
     * @throws PropertyNotFoundException if the property does not exist.
     * @throws UnsupportedMediaTypeException if the property exists,
     *         but the content-type of the request is not supported.
     */
    ResponseEntity<Object> putProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            HttpServletRequest request
    ) throws PropertyNotFoundException, UnsupportedMediaTypeException;

    /**
     * HTTP Patch on the property. Returns a {@link ResponseEntity} if successful.
     * Might throw if the property exists, but patching fails for a different reason.
     *
     * @param application the application context
     * @param entity the entity
     * @param instanceId the primary key value of the entity
     * @param propertyName the path segment of the property
     * @param request the request, can be used to look up the body, query parameters, form-data parts, ...
     * @return a {@link ResponseEntity} containing the result
     * @throws PropertyNotFoundException if the property does not exist.
     * @throws UnsupportedMediaTypeException if the property exists,
     *         but the content-type of the request is not supported.
     */
    ResponseEntity<Object> patchProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            HttpServletRequest request
    ) throws PropertyNotFoundException, UnsupportedMediaTypeException;

    /**
     * HTTP Delete on the property. Returns a {@link ResponseEntity} if successful.
     * Might throw if the property exists, but deleting it fails for a different reason.
     *
     * @param application the application context
     * @param entity the entity
     * @param instanceId the primary key value of the entity
     * @param propertyName the path segment of the property
     * @return a {@link ResponseEntity} containing the result
     * @throws PropertyNotFoundException if the property does not exist.
     * @throws UnsupportedMediaTypeException if the property exists,
     *         but the content-type of the request is not supported.
     */
    ResponseEntity<Object> deleteProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName
    ) throws PropertyNotFoundException;
}
