package com.contentgrid.appserver.rest.property.handler;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.ResponseEntity;

public interface PropertyItemRequestHandler extends PropertyRequestHandler {

    Optional<ResponseEntity<Object>> getPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            EntityId itemId
    );

    Optional<ResponseEntity<Object>> postPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            EntityId itemId,
            HttpServletRequest request
    );

    Optional<ResponseEntity<Object>> putPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            EntityId itemId,
            HttpServletRequest request
    );

    Optional<ResponseEntity<Object>> patchPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            EntityId itemId,
            HttpServletRequest request
    );

    Optional<ResponseEntity<Object>> deletePropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            EntityId itemId
    );
}
