package com.contentgrid.appserver.rest.property;

import static com.contentgrid.appserver.rest.property.PropertyUtils.handleProperty;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType.PropertyType;
import com.contentgrid.appserver.rest.property.handler.PropertyRequestHandler;
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
@SpecializedOnPropertyType(type = {PropertyType.TO_ONE_RELATION,
        PropertyType.TO_MANY_RELATION}, entityPathVariable = "entityName", propertyPathVariable = "propertyName")
public class PropertyRestController {

    private final List<PropertyRequestHandler> requestHandlers;

    @GetMapping(value = "/{entityName}/{instanceId}/{propertyName}")
    public ResponseEntity<Object> getProperty(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName
    ) {
        return handleProperty(application, entityName, propertyName, requestHandlers, (requestHandler, entity) ->
                requestHandler.getProperty(application, entity, instanceId, propertyName));
    }

    @PostMapping(value = "/{entityName}/{instanceId}/{propertyName}")
    public ResponseEntity<Object> postProperty(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            HttpServletRequest request
    ) {
        return handleProperty(application, entityName, propertyName, requestHandlers, (requestHandler, entity) ->
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
        return handleProperty(application, entityName, propertyName, requestHandlers, (requestHandler, entity) ->
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
        return handleProperty(application, entityName, propertyName, requestHandlers, (requestHandler, entity) ->
                requestHandler.patchProperty(application, entity, instanceId, propertyName, request));
    }

    @DeleteMapping("/{entityName}/{instanceId}/{propertyName}")
    public ResponseEntity<Object> deleteProperty(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName
    ) {
        return handleProperty(application, entityName, propertyName, requestHandlers, (requestHandler, entity) ->
                requestHandler.deleteProperty(application, entity, instanceId, propertyName));
    }
}
