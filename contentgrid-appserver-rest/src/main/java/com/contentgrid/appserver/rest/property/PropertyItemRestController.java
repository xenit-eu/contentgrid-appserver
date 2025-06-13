package com.contentgrid.appserver.rest.property;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.property.handler.PropertyItemRequestHandler;
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
public class PropertyItemRestController {

    private final List<PropertyItemRequestHandler> requestHandlers;

    @GetMapping("/{entityName}/{instanceId}/{propertyName}/{itemId}")
    public ResponseEntity<Object> getPropertyItem(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            @PathVariable EntityId itemId
    ) {
        return handleProperty(application, entityName, propertyName, (requestHandler, entity) ->
                requestHandler.getPropertyItem(application, entity, instanceId, propertyName, itemId));
    }

    @PostMapping("/{entityName}/{instanceId}/{propertyName}/{itemId}")
    public ResponseEntity<Object> postPropertyItem(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            @PathVariable EntityId itemId,
            HttpServletRequest request
    ) {
        return handleProperty(application, entityName, propertyName, (requestHandler, entity) ->
                requestHandler.postPropertyItem(application, entity, instanceId, propertyName, itemId, request));
    }

    @PutMapping("/{entityName}/{instanceId}/{propertyName}/{itemId}")
    public ResponseEntity<Object> putPropertyItem(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            @PathVariable EntityId itemId,
            HttpServletRequest request
    ) {
        return handleProperty(application, entityName, propertyName, (requestHandler, entity) ->
                requestHandler.putPropertyItem(application, entity, instanceId, propertyName, itemId, request));
    }

    @PatchMapping("/{entityName}/{instanceId}/{propertyName}/{itemId}")
    public ResponseEntity<Object> patchPropertyItem(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            @PathVariable EntityId itemId,
            HttpServletRequest request
    ) {
        return handleProperty(application, entityName, propertyName, (requestHandler, entity) ->
                requestHandler.patchPropertyItem(application, entity, instanceId, propertyName, itemId, request));
    }

    @DeleteMapping("/{entityName}/{instanceId}/{propertyName}/{itemId}")
    public ResponseEntity<Object> deletePropertyItem(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            @PathVariable EntityId itemId
    ) {
        return handleProperty(application, entityName, propertyName, (requestHandler, entity) ->
                requestHandler.deletePropertyItem(application, entity, instanceId, propertyName, itemId));
    }

    private ResponseEntity<Object> handleProperty(
            Application application, PathSegmentName entityName, PathSegmentName propertyName,
            BiFunction<PropertyItemRequestHandler, Entity, Optional<ResponseEntity<Object>>> function
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
