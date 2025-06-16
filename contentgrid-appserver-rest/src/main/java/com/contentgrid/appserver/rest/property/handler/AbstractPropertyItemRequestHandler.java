package com.contentgrid.appserver.rest.property.handler;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.converter.HttpServletRequestConverter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.ResponseEntity;

public abstract class AbstractPropertyItemRequestHandler<T, P> extends AbstractPropertyRequestHandler<T, P> implements
        PropertyItemRequestHandler {

    protected AbstractPropertyItemRequestHandler(HttpServletRequestConverter<T> requestConverter) {
        super(requestConverter);
    }

    @Override
    public final Optional<ResponseEntity<Object>> getPropertyItem(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, EntityId itemId) {
        return findProperty(application, entity, propertyName)
                .map(property -> getPropertyItem(application, entity, instanceId, property, itemId));
    }

    @Override
    public final Optional<ResponseEntity<Object>> postPropertyItem(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, EntityId itemId, HttpServletRequest request) {
        return findProperty(application, entity, propertyName)
                .map(property -> handleProperty(request, body ->
                        postPropertyItem(application, entity, instanceId, property, itemId, body)));
    }

    @Override
    public final Optional<ResponseEntity<Object>> putPropertyItem(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, EntityId itemId, HttpServletRequest request) {
        return findProperty(application, entity, propertyName)
                .map(property -> handleProperty(request, body ->
                        putPropertyItem(application, entity, instanceId, property, itemId, body)));
    }

    @Override
    public final Optional<ResponseEntity<Object>> patchPropertyItem(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, EntityId itemId, HttpServletRequest request) {
        return findProperty(application, entity, propertyName)
                .map(property -> handleProperty(request, body ->
                        patchPropertyItem(application, entity, instanceId, property, itemId, body)));
    }

    @Override
    public final Optional<ResponseEntity<Object>> deletePropertyItem(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, EntityId itemId) {
        return findProperty(application, entity, propertyName)
                .map(property -> deletePropertyItem(application, entity, instanceId, property, itemId));
    }

    protected abstract ResponseEntity<Object> getPropertyItem(Application application, Entity entity, EntityId instanceId, P property, EntityId itemId);

    protected abstract ResponseEntity<Object> postPropertyItem(Application application, Entity entity, EntityId instanceId, P property, EntityId itemId, T body);

    protected abstract ResponseEntity<Object> putPropertyItem(Application application, Entity entity, EntityId instanceId, P property, EntityId itemId, T body);

    protected abstract ResponseEntity<Object> patchPropertyItem(Application application, Entity entity, EntityId instanceId, P property, EntityId itemId, T body);

    protected abstract ResponseEntity<Object> deletePropertyItem(Application application, Entity entity, EntityId instanceId, P property, EntityId itemId);
}
