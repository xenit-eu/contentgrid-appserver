package com.contentgrid.appserver.rest.property.handler;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.converter.HttpServletRequestConverter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
public abstract class AbstractPropertyRequestHandler<T, P> implements PropertyRequestHandler {

    @Getter(AccessLevel.PROTECTED)
    private final HttpServletRequestConverter<T> requestConverter;

    abstract protected Optional<P> findProperty(Application application, Entity entity, PathSegmentName propertyName);

    @Override
    public final Optional<ResponseEntity<Object>> getProperty(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName) {
        return findProperty(application, entity, propertyName)
                .map(property -> getProperty(application, entity, instanceId, property));
    }

    @Override
    public final Optional<ResponseEntity<Object>> postProperty(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, HttpServletRequest request) {
        return findProperty(application, entity, propertyName)
                .map(property -> handleProperty(request, body ->
                        postProperty(application, entity, instanceId, property, body)));
    }

    @Override
    public final Optional<ResponseEntity<Object>> putProperty(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, HttpServletRequest request) {
        return findProperty(application, entity, propertyName)
                .map(property -> handleProperty(request, body ->
                        putProperty(application, entity, instanceId, property, body)));
    }

    @Override
    public final Optional<ResponseEntity<Object>> patchProperty(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, HttpServletRequest request) {
        return findProperty(application, entity, propertyName)
                .map(property -> handleProperty(request, body ->
                        patchProperty(application, entity, instanceId, property, body)));
    }

    @Override
    public final Optional<ResponseEntity<Object>> deleteProperty(Application application, Entity entity, EntityId instanceId, PathSegmentName propertyName) {
        return findProperty(application, entity, propertyName)
                .map(property -> deleteProperty(application, entity, instanceId, property));
    }

    protected final ResponseEntity<Object> handleProperty(HttpServletRequest request, Function<T, ResponseEntity<Object>> function) {
        if (!requestConverter.canRead(request)) {
            // TODO: what if a different request handler supports media type? e.g. PUT multipart/form-data vs PUT */*
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "media type not supported");
        }
        return requestConverter.convert(request).map(function)
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    protected abstract ResponseEntity<Object> getProperty(Application application, Entity entity, EntityId instanceId, P property);

    protected abstract ResponseEntity<Object> postProperty(Application application, Entity entity, EntityId instanceId, P property, T body);

    protected abstract ResponseEntity<Object> putProperty(Application application, Entity entity, EntityId instanceId, P property, T body);

    protected abstract ResponseEntity<Object> patchProperty(Application application, Entity entity, EntityId instanceId, P property, T body);

    protected abstract ResponseEntity<Object> deleteProperty(Application application, Entity entity, EntityId instanceId, P property);
}
