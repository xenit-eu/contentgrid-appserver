package com.contentgrid.appserver.rest.property;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.rest.exception.PropertyNotFoundException;
import com.contentgrid.appserver.rest.exception.UnsupportedMediaTypeException;
import com.contentgrid.appserver.rest.property.handler.PropertyRequestHandler;
import java.util.Collection;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@UtilityClass
public class PropertyUtils {

    public interface HandlePropertyFunction<T extends PropertyRequestHandler> {
        ResponseEntity<Object> apply(T requestHandler, Entity entity) throws PropertyNotFoundException, UnsupportedMediaTypeException;
    }

    public static <T extends PropertyRequestHandler> ResponseEntity<Object> handleProperty(
            Application application, PathSegmentName entityName, PathSegmentName propertyName,
            Collection<T> requestHandlers, HandlePropertyFunction<T> function
    ) {
        var entity = application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity %s does not exist".formatted(entityName)));

        UnsupportedMediaTypeException unsupportedMediaTypeException = null;

        for (var requestHandler : requestHandlers) {
            try {
                return function.apply(requestHandler, entity);
            } catch (PropertyNotFoundException e) {
                // skip
            } catch (UnsupportedMediaTypeException e) {
                unsupportedMediaTypeException = e;
            }
        }

        if (unsupportedMediaTypeException != null) {
            throw unsupportedMediaTypeException;
        } else {
            throw new PropertyNotFoundException(propertyName);
        }
    }
}
