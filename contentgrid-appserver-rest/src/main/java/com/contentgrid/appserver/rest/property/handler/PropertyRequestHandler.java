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
     * Access the property. Returns an {@link ResponseEntity} containing the property.
     * Might throw if the property exists, but accessing the property fails for a different reason.
     *
     * @param application the application context
     * @param entity the entity
     * @param instanceId the primary key value of the entity
     * @param propertyName the path segment of the property
     * @return a {@link ResponseEntity} containing the property
     * @throws PropertyNotFoundException if the property does not exist.
     */
    ResponseEntity<Object> getProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName
    ) throws PropertyNotFoundException;

    ResponseEntity<Object> postProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            HttpServletRequest request
    ) throws PropertyNotFoundException, UnsupportedMediaTypeException;

    ResponseEntity<Object> putProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            HttpServletRequest request
    ) throws PropertyNotFoundException, UnsupportedMediaTypeException;

    ResponseEntity<Object> patchProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            HttpServletRequest request
    ) throws PropertyNotFoundException, UnsupportedMediaTypeException;

    ResponseEntity<Object> deleteProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName
    ) throws PropertyNotFoundException;
}
