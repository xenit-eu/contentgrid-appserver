package com.contentgrid.appserver.rest.property.handler;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.exception.PropertyNotFoundException;
import com.contentgrid.appserver.rest.exception.UnsupportedMediaTypeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

public interface PropertyItemRequestHandler extends PropertyRequestHandler {

    ResponseEntity<Object> getPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            EntityId itemId
    ) throws PropertyNotFoundException;

    ResponseEntity<Object> postPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            EntityId itemId,
            HttpServletRequest request
    ) throws PropertyNotFoundException, UnsupportedMediaTypeException;

    ResponseEntity<Object> putPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            EntityId itemId,
            HttpServletRequest request
    ) throws PropertyNotFoundException, UnsupportedMediaTypeException;

    ResponseEntity<Object> patchPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            EntityId itemId,
            HttpServletRequest request
    ) throws PropertyNotFoundException, UnsupportedMediaTypeException;

    ResponseEntity<Object> deletePropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            EntityId itemId
    ) throws PropertyNotFoundException;
}
