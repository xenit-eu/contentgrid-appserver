package com.contentgrid.appserver.rest.property.handler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.converter.UriListHttpServletRequestConverter;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RelationRequestHandler extends AbstractPropertyItemRequestHandler<List<URI>, Relation> {

    private final DatamodelApi datamodelApi;

    @Autowired
    public RelationRequestHandler(DatamodelApi datamodelApi) {
        super(new UriListHttpServletRequestConverter());
        this.datamodelApi = datamodelApi;
    }

    @Override
    public boolean supports(Application application, Entity entity, PathSegmentName propertyName) {
        return application.getRelationForPath(entity.getPathSegment(), propertyName).isPresent();
    }

    @Override
    protected Optional<Relation> findProperty(Application application, Entity entity, PathSegmentName propertyName) {
        return application.getRelationForPath(entity.getPathSegment(), propertyName);
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
            URI redirectUrl;
            switch (property) {
                case OneToOneRelation oneToOneRelation -> {
                    var targetId = datamodelApi.findRelationTarget(application, oneToOneRelation, instanceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target of %s not found".formatted(property.getSourceEndPoint().getName())));
                    redirectUrl = WebMvcLinkBuilder.linkTo(methodOn(EntityRestController.class).getEntity(application, targetPathSegment, targetId)).toUri();
                }
                case ManyToOneRelation manyToOneRelation -> {
                    var targetId = datamodelApi.findRelationTarget(application, manyToOneRelation, instanceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target of %s not found".formatted(property.getSourceEndPoint().getName())));
                    redirectUrl = linkTo(methodOn(EntityRestController.class).getEntity(application, targetPathSegment, targetId)).toUri();
                }
                case OneToManyRelation oneToManyRelation -> {
                    datamodelApi.findById(application, oneToManyRelation.getSourceEndPoint().getEntity(), instanceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id %s not found".formatted(instanceId)));
                    redirectUrl = linkTo(methodOn(EntityRestController.class).listEntity(application, targetPathSegment, 0,
                            Map.of(property.getTargetEndPoint().getName().getValue(), instanceId.toString()))).toUri(); // TODO: use RelationSearchFilter
                }
                case ManyToManyRelation manyToManyRelation -> {
                    datamodelApi.findById(application, manyToManyRelation.getSourceEndPoint().getEntity(), instanceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id %s not found".formatted(instanceId)));
                    redirectUrl = linkTo(methodOn(EntityRestController.class).listEntity(application, targetPathSegment, 0,
                            Map.of(property.getTargetEndPoint().getName().getValue(), instanceId.toString()))).toUri(); // TODO: use RelationSearchFilter
                }
            }
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
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No entity url provided.");
        }
        var targetPathSegment = property.getTargetEndPoint().getEntity().getPathSegment();

        if (!(property instanceof OneToManyRelation || property instanceof ManyToManyRelation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Single targets not supported.");
        }

        var dataBuilder = XToManyRelationData.builder()
                .entity(property.getSourceEndPoint().getEntity().getName())
                .name(property.getSourceEndPoint().getName());

        for (var element : body) {
            var pathSegment = parseEntityPathSegment(element);
            if (!targetPathSegment.equals(pathSegment)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid target entity. Expected %s, got %s.".formatted(targetPathSegment, pathSegment));
            }
            var targetId = parseEntityId(element);
            dataBuilder.ref(targetId);
        }
        try {
            datamodelApi.addRelationItems(application, dataBuilder.build(), instanceId);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    protected ResponseEntity<Object> patchProperty(Application application, Entity entity, EntityId instanceId,
            Relation property, List<URI> body) {
        return ResponseEntity.notFound().build();
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

        if (property instanceof OneToManyRelation || property instanceof ManyToManyRelation) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple targets not supported.");
        }
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

    @Override
    protected ResponseEntity<Object> getPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            EntityId itemId
    ) {
        try {
            if (datamodelApi.hasRelationTarget(application, property, instanceId, itemId)) {
                var uri = linkTo(methodOn(EntityRestController.class).getEntity(application, entity.getPathSegment(), instanceId)).toUri();
                return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @Override
    public ResponseEntity<Object> postPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            EntityId itemId,
            List<URI> body
    ) {
        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<Object> putPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            EntityId itemId,
            List<URI> body
    ) {
        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<Object> patchPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            EntityId itemId,
            List<URI> body
    ) {
        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<Object> deletePropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            EntityId itemId
    ) {
        if (!(property instanceof OneToManyRelation || property instanceof ManyToManyRelation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Single targets not supported.");
        }

        try {
            datamodelApi.removeRelationItem(application, property, instanceId, itemId);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

}
