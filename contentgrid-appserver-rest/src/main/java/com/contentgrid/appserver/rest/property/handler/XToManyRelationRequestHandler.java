package com.contentgrid.appserver.rest.property.handler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.RelationLinkNotFoundException;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.converter.UriListHttpServletRequestConverter;
import com.contentgrid.hateoas.spring.links.UriTemplateMatcher;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class XToManyRelationRequestHandler extends AbstractPropertyItemRequestHandler<List<URI>, Relation> {

    private static final Set<HttpMethod> SUPPORTED_PROPERTY_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST, HttpMethod.DELETE);
    private static final Set<HttpMethod> SUPPORTED_PROPERTY_ITEM_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.DELETE);

    @NonNull
    private final DatamodelApi datamodelApi;

    @Autowired
    public XToManyRelationRequestHandler(@NonNull DatamodelApi datamodelApi) {
        super(new UriListHttpServletRequestConverter());
        this.datamodelApi = datamodelApi;
    }

    @Override
    protected Optional<Relation> findProperty(Application application, Entity entity, PathSegmentName propertyName) {
        return application.getRelationForPath(entity.getPathSegment(), propertyName)
                .filter(relation -> relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation);
    }

    private UriTemplateMatcher<EntityId> getMatcherForTargetEntity(Application application, Relation relation) {
        var targetPathSegment = relation.getTargetEndPoint().getEntity().getPathSegment();
        return UriTemplateMatcher.<EntityId>builder()
                .matcherFor(methodOn(EntityRestController.class)
                                .getEntity(application, targetPathSegment, null),
                        params -> EntityId.of(UUID.fromString(params.get("instanceId"))))
                .build();
    }

    @Override
    protected ResponseEntity<Object> getProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property
    ) {
        var targetPathSegment = property.getTargetEndPoint().getEntity().getPathSegment();
        datamodelApi.findById(application, property.getSourceEndPoint().getEntity(), instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id %s not found".formatted(instanceId)));
        // TODO: ACC-2149 use FilterName of relation
        var filterName = property.getTargetEndPoint().getName();
        if (filterName != null) {
            var redirectUrl = linkTo(methodOn(EntityRestController.class)
                    .listEntity(application, targetPathSegment, 0, null, Map.of(filterName.getValue(), instanceId.toString())))
                    .toUri();

            return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Following an unidirectional *-to-many relation not implemented.");
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
        var matcher = getMatcherForTargetEntity(application, property);
        var targetIds = new java.util.HashSet<EntityId>();

        for (var element : body) {
            var maybeId = matcher.tryMatch(element.toString());
            if (maybeId.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid target entity.");
            }
            targetIds.add(maybeId.get());
        }
        try {
            datamodelApi.addRelationItems(application, property, instanceId, targetIds);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    protected ResponseEntity<Object> putProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            List<URI> body
    ) {
        throw new MethodNotAllowedException(HttpMethod.PUT, SUPPORTED_PROPERTY_METHODS);
    }

    @Override
    protected ResponseEntity<Object> patchProperty(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            List<URI> body
    ) {
        throw new MethodNotAllowedException(HttpMethod.PATCH, SUPPORTED_PROPERTY_METHODS);
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
        } catch (EntityNotFoundException|RelationLinkNotFoundException e) {
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
        if (datamodelApi.hasRelationTarget(application, property, instanceId, itemId)) {
            var uri = linkTo(methodOn(EntityRestController.class).getEntity(application, property.getTargetEndPoint().getEntity().getPathSegment(), itemId)).toUri();
            return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @Override
    protected ResponseEntity<Object> postPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            EntityId itemId,
            List<URI> body
    ) {
        throw new MethodNotAllowedException(HttpMethod.POST, SUPPORTED_PROPERTY_ITEM_METHODS);
    }

    @Override
    protected ResponseEntity<Object> putPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            EntityId itemId,
            List<URI> body
    ) {
        throw new MethodNotAllowedException(HttpMethod.PUT, SUPPORTED_PROPERTY_ITEM_METHODS);
    }

    @Override
    protected ResponseEntity<Object> patchPropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            EntityId itemId,
            List<URI> body
    ) {
        throw new MethodNotAllowedException(HttpMethod.PATCH, SUPPORTED_PROPERTY_ITEM_METHODS);
    }

    @Override
    protected ResponseEntity<Object> deletePropertyItem(
            Application application,
            Entity entity,
            EntityId instanceId,
            Relation property,
            EntityId itemId
    ) {
        try {
            datamodelApi.removeRelationItem(application, property, instanceId, itemId);
        } catch (EntityNotFoundException | RelationLinkNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

}
