package com.contentgrid.appserver.rest.property.handler;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.converter.HttpServletRequestConverter;
import com.contentgrid.appserver.rest.exception.PropertyNotFoundException;
import com.contentgrid.appserver.rest.exception.UnsupportedMediaTypeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

public abstract class AbstractPropertyItemRequestHandler<T, P> extends AbstractPropertyRequestHandler<T, P> implements
        PropertyItemRequestHandler {

    protected AbstractPropertyItemRequestHandler(HttpServletRequestConverter<T> requestConverter) {
        super(requestConverter);
    }

    @Override
    public final ResponseEntity<Object> getPropertyItem(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, EntityId itemId) throws PropertyNotFoundException {
        return findProperty(application, entity, propertyName)
                .map(property -> getPropertyItem(application, entity, instanceId, property, itemId))
                .orElseThrow(() -> new PropertyNotFoundException(propertyName));
    }

    @Override
    public final ResponseEntity<Object> postPropertyItem(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, EntityId itemId, HttpServletRequest request) throws PropertyNotFoundException, UnsupportedMediaTypeException {
        return findProperty(application, entity, propertyName)
                .map(property -> handleProperty(request, body ->
                        postPropertyItem(application, entity, instanceId, property, itemId, body)))
                .orElseThrow(() -> new PropertyNotFoundException(propertyName));
    }

    @Override
    public final ResponseEntity<Object> putPropertyItem(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, EntityId itemId, HttpServletRequest request) throws PropertyNotFoundException, UnsupportedMediaTypeException {
        return findProperty(application, entity, propertyName)
                .map(property -> handleProperty(request, body ->
                        putPropertyItem(application, entity, instanceId, property, itemId, body)))
                .orElseThrow(() -> new PropertyNotFoundException(propertyName));
    }

    @Override
    public final ResponseEntity<Object> patchPropertyItem(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, EntityId itemId, HttpServletRequest request) throws PropertyNotFoundException, UnsupportedMediaTypeException {
        return findProperty(application, entity, propertyName)
                .map(property -> handleProperty(request, body ->
                        patchPropertyItem(application, entity, instanceId, property, itemId, body)))
                .orElseThrow(() -> new PropertyNotFoundException(propertyName));
    }

    @Override
    public final ResponseEntity<Object> deletePropertyItem(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, EntityId itemId) throws PropertyNotFoundException {
        return findProperty(application, entity, propertyName)
                .map(property -> deletePropertyItem(application, entity, instanceId, property, itemId))
                .orElseThrow(() -> new PropertyNotFoundException(propertyName));
    }

    protected abstract ResponseEntity<Object> getPropertyItem(Application application, Entity entity, EntityId instanceId, P property, EntityId itemId);

    protected abstract ResponseEntity<Object> postPropertyItem(Application application, Entity entity, EntityId instanceId, P property, EntityId itemId, T body);

    protected abstract ResponseEntity<Object> putPropertyItem(Application application, Entity entity, EntityId instanceId, P property, EntityId itemId, T body);

    protected abstract ResponseEntity<Object> patchPropertyItem(Application application, Entity entity, EntityId instanceId, P property, EntityId itemId, T body);

    protected abstract ResponseEntity<Object> deletePropertyItem(Application application, Entity entity, EntityId instanceId, P property, EntityId itemId);
}
