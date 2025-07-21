package com.contentgrid.appserver.rest.property;

import static com.contentgrid.appserver.rest.property.PropertyUtils.handleProperty;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType.PropertyType;
import com.contentgrid.appserver.rest.property.handler.PropertyItemRequestHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@SpecializedOnPropertyType(type = PropertyType.TO_MANY_RELATION, entityPathVariable = "entityName", propertyPathVariable = "propertyName")
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
        return handleProperty(application, entityName, propertyName, requestHandlers, (requestHandler, entity) ->
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
        return handleProperty(application, entityName, propertyName, requestHandlers, (requestHandler, entity) ->
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
        return handleProperty(application, entityName, propertyName, requestHandlers, (requestHandler, entity) ->
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
        return handleProperty(application, entityName, propertyName, requestHandlers, (requestHandler, entity) ->
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
        return handleProperty(application, entityName, propertyName, requestHandlers, (requestHandler, entity) ->
                requestHandler.deletePropertyItem(application, entity, instanceId, propertyName, itemId));
    }
}
