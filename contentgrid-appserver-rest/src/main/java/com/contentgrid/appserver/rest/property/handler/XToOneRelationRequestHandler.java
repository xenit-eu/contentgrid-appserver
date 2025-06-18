package com.contentgrid.appserver.rest.property.handler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.converter.UriListHttpServletRequestConverter;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class XToOneRelationRequestHandler extends AbstractPropertyRequestHandler<List<URI>, Relation> {

    private static final HttpMethod[] SUPPORTED_PROPERTY_METHODS = {HttpMethod.GET, HttpMethod.HEAD, HttpMethod.PUT, HttpMethod.DELETE};

    @NonNull
    private final DatamodelApi datamodelApi;

    @Autowired
    public XToOneRelationRequestHandler(@NonNull DatamodelApi datamodelApi) {
        super(new UriListHttpServletRequestConverter());
        this.datamodelApi = datamodelApi;
    }

    @Override
    protected Optional<Relation> findProperty(Application application, Entity entity, PathSegmentName propertyName) {
        return application.getRelationForPath(entity.getPathSegment(), propertyName)
                .filter(relation -> relation instanceof OneToOneRelation || relation instanceof ManyToOneRelation);
    }

    @Override
    protected ResponseEntity<Object> getProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property
    ) {
        var targetPathSegment = property.getTargetEndPoint().getEntity().getPathSegment();
        try {
            var targetId = datamodelApi.findRelationTarget(application, property, instanceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target of %s not found".formatted(property.getSourceEndPoint().getName())));
            var redirectUrl = linkTo(methodOn(EntityRestController.class).getEntity(application, targetPathSegment, targetId)).toUri();

            return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    private PathSegmentName parseEntityPathSegment(URI uri) {
        var path = uri.getPath().split("/");
        if (path.length == 3 && path[0].isEmpty()) {
            return PathSegmentName.of(path[1]);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid path %s".formatted(uri.getPath()));
        }
    }

    private EntityId parseEntityId(URI uri) {
        var path = uri.getPath().split("/");
        if (path.length == 3 && path[0].isEmpty()) {
            return EntityId.of(UUID.fromString(path[2]));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid path %s".formatted(uri.getPath()));
        }
    }

    @Override
    protected ResponseEntity<Object> postProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            List<URI> body
    ) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(SUPPORTED_PROPERTY_METHODS)
                .build();
    }

    @Override
    protected ResponseEntity<Object> putProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            List<URI> body
    ) {
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No entity url provided.");
        }
        if (body.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple targets not supported.");
        }
        var targetPathSegment = property.getTargetEndPoint().getEntity().getPathSegment();
        var element = body.getFirst();

        var pathSegment = parseEntityPathSegment(element);
        if (!targetPathSegment.equals(pathSegment)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid target entity. Expected %s, got %s.".formatted(targetPathSegment, pathSegment));
        }
        var targetId = parseEntityId(element);
        var data = XToOneRelationData.builder()
                .entity(property.getSourceEndPoint().getEntity().getName())
                .name(property.getSourceEndPoint().getName())
                .ref(targetId)
                .build();
        try {
            datamodelApi.setRelation(application, data, instanceId);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    protected ResponseEntity<Object> patchProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            List<URI> body
    ) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(SUPPORTED_PROPERTY_METHODS)
                .build();
    }

    @Override
    protected ResponseEntity<Object> deleteProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property
    ) {
        try {
            datamodelApi.deleteRelation(application, property, instanceId);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

}
