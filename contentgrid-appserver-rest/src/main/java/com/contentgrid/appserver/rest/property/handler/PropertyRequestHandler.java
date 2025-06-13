package com.contentgrid.appserver.rest.property.handler;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.ResponseEntity;

public interface PropertyRequestHandler {

    boolean supports(Application application, Entity entity, PathSegmentName propertyName);

    /**
     * Access the property. Returns an {@link Optional} containing the result if the property exists, empty otherwise.
     * Might throw if the property exists, but accessing the property fails for a different reason.
     *
     * @param application the application context
     * @param entity the entity
     * @param instanceId the primary key value of the entity
     * @param propertyName the path segment of the property
     * @return an {@link Optional} containing the result if property exists, empty otherwise
     */
    Optional<ResponseEntity<Object>> getProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName
    );

    Optional<ResponseEntity<Object>> postProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            HttpServletRequest request
    );

    Optional<ResponseEntity<Object>> putProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            HttpServletRequest request
    );

    Optional<ResponseEntity<Object>> patchProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName,
            HttpServletRequest request
    );

    Optional<ResponseEntity<Object>> deleteProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            PathSegmentName propertyName
    );
}
