package com.contentgrid.appserver.rest.property.handler;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.converter.HttpServletRequestConverter;
import com.contentgrid.appserver.rest.exception.IllegalMediaTypeException;
import com.contentgrid.appserver.rest.exception.UnsupportedMediaTypeException;
import com.contentgrid.appserver.rest.exception.PropertyNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
public abstract class AbstractPropertyRequestHandler<T, P> implements PropertyRequestHandler {

    @Getter(AccessLevel.PROTECTED)
    private final HttpServletRequestConverter<T> requestConverter;

    protected abstract Optional<P> findProperty(Application application, Entity entity, PathSegmentName propertyName);

    @Override
    public final ResponseEntity<Object> getProperty(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName) throws PropertyNotFoundException {
        return findProperty(application, entity, propertyName)
                .map(property -> getProperty(application, entity, instanceId, property))
                .orElseThrow(() -> new PropertyNotFoundException(propertyName));
    }

    @Override
    public final ResponseEntity<Object> postProperty(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, HttpServletRequest request) throws PropertyNotFoundException, UnsupportedMediaTypeException {
        return findProperty(application, entity, propertyName)
                .map(property -> handleProperty(request, body ->
                        postProperty(application, entity, instanceId, property, body)))
                .orElseThrow(() -> new PropertyNotFoundException(propertyName));
    }

    @Override
    public final ResponseEntity<Object> putProperty(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, HttpServletRequest request) throws PropertyNotFoundException, UnsupportedMediaTypeException {
        return findProperty(application, entity, propertyName)
                .map(property -> handleProperty(request, body ->
                        putProperty(application, entity, instanceId, property, body)))
                .orElseThrow(() -> new PropertyNotFoundException(propertyName));
    }

    @Override
    public final ResponseEntity<Object> patchProperty(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName, HttpServletRequest request) throws PropertyNotFoundException, UnsupportedMediaTypeException {
        return findProperty(application, entity, propertyName)
                .map(property -> handleProperty(request, body ->
                        patchProperty(application, entity, instanceId, property, body)))
                .orElseThrow(() -> new PropertyNotFoundException(propertyName));
    }

    @Override
    public final ResponseEntity<Object> deleteProperty(Application application, Entity entity, EntityId instanceId,
            PathSegmentName propertyName) throws PropertyNotFoundException {
        return findProperty(application, entity, propertyName)
                .map(property -> deleteProperty(application, entity, instanceId, property))
                .orElseThrow(() -> new PropertyNotFoundException(propertyName));
    }

    protected final ResponseEntity<Object> handleProperty(HttpServletRequest request, Function<T, ResponseEntity<Object>> function)
            throws IllegalMediaTypeException, UnsupportedMediaTypeException {
        assertMediaTypeSupported(request);
        return requestConverter.convert(request).map(function)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid body"));
    }

    private void assertMediaTypeSupported(HttpServletRequest request)
            throws IllegalMediaTypeException, UnsupportedMediaTypeException {
        try {
            var mediaType = MediaType.parseMediaType(request.getContentType());
            if (!requestConverter.canRead(mediaType)) {
                throw new UnsupportedMediaTypeException(mediaType, requestConverter.getSupportedMediaTypes());
            }
        } catch (InvalidMediaTypeException | InvalidMimeTypeException e) {
            throw new IllegalMediaTypeException(request.getContentType(), requestConverter.getSupportedMediaTypes());
        }
    }

    protected abstract ResponseEntity<Object> getProperty(Application application, Entity entity, EntityId instanceId, P property);

    protected abstract ResponseEntity<Object> postProperty(Application application, Entity entity, EntityId instanceId, P property, T body);

    protected abstract ResponseEntity<Object> putProperty(Application application, Entity entity, EntityId instanceId, P property, T body);

    protected abstract ResponseEntity<Object> patchProperty(Application application, Entity entity, EntityId instanceId, P property, T body);

    protected abstract ResponseEntity<Object> deleteProperty(Application application, Entity entity, EntityId instanceId, P property);
}
