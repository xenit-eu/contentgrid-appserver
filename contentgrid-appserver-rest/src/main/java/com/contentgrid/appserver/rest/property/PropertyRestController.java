package com.contentgrid.appserver.rest.property;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.property.handler.PropertyRequestHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PropertyRestController {

    private final List<PropertyRequestHandler> requestHandlers;

    @GetMapping("/{entityName}/{instanceId}/{propertyName}")
    public ResponseEntity<Object> getProperty(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName
    ) {
        return handleProperty(application, entityName, propertyName, (requestHandler, entity) ->
                requestHandler.getProperty(application, entity, instanceId, propertyName));
    }

    @PostMapping("/{entityName}/{instanceId}/{propertyName}")
    public ResponseEntity<Object> postProperty(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            HttpServletRequest request
    ) {
        return handleProperty(application, entityName, propertyName, (requestHandler, entity) ->
                requestHandler.postProperty(application, entity, instanceId, propertyName, request));
    }

    @PutMapping("/{entityName}/{instanceId}/{propertyName}")
    public ResponseEntity<Object> putProperty(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            HttpServletRequest request
    ) {
        return handleProperty(application, entityName, propertyName, (requestHandler, entity) ->
                requestHandler.putProperty(application, entity, instanceId, propertyName, request));
    }

    @PatchMapping("/{entityName}/{instanceId}/{propertyName}")
    public ResponseEntity<Object> patchProperty(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            HttpServletRequest request
    ) {
        return handleProperty(application, entityName, propertyName, (requestHandler, entity) ->
                requestHandler.patchProperty(application, entity, instanceId, propertyName, request));
    }

    @DeleteMapping("/{entityName}/{instanceId}/{propertyName}")
    public ResponseEntity<Object> deleteProperty(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName
    ) {
        return handleProperty(application, entityName, propertyName, (requestHandler, entity) ->
                requestHandler.deleteProperty(application, entity, instanceId, propertyName));
    }

    private ResponseEntity<Object> handleProperty(
            Application application, PathSegmentName entityName, PathSegmentName propertyName,
            BiFunction<PropertyRequestHandler, Entity, Optional<ResponseEntity<Object>>> function
    ) {
        var entity = application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity %s does not exist".formatted(entityName)));

        return requestHandlers.stream()
                .map(requestHandler -> function.apply(requestHandler, entity))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Property %s does not exist on entity %s".formatted(propertyName, entityName)));
    }
}
