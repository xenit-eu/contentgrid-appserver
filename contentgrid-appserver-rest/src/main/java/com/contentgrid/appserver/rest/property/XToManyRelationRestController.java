package com.contentgrid.appserver.rest.property;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
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
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType.PropertyType;
import com.contentgrid.hateoas.spring.links.UriTemplateMatcher;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@SpecializedOnPropertyType(type = PropertyType.TO_MANY_RELATION, entityPathVariable = "entityName", propertyPathVariable = "propertyName")
@RequestMapping("/{entityName}/{instanceId}/{propertyName}")
public class XToManyRelationRestController {

    @NonNull
    private final DatamodelApi datamodelApi;

    private Relation getRequiredRelation(Application application, PathSegmentName entityName, PathSegmentName propertyName) {
        return application.getRelationForPath(entityName, propertyName)
                .filter(relation -> relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private UriTemplateMatcher<EntityId> getMatcherForTargetEntity(Application application, Relation relation) {
        var targetPathSegment = relation.getTargetEndPoint().getEntity().getPathSegment();
        return UriTemplateMatcher.<EntityId>builder()
                .matcherFor(methodOn(EntityRestController.class)
                                .getEntity(application, targetPathSegment, null),
                        params -> EntityId.of(UUID.fromString(params.get("instanceId"))))
                .build();
    }

    @GetMapping
    public ResponseEntity<Object> getRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName
    ) {
        var relation = getRequiredRelation(application, entityName, propertyName);
        var targetPathSegment = relation.getTargetEndPoint().getEntity().getPathSegment();
        datamodelApi.findById(application, relation.getSourceEndPoint().getEntity(), instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id %s not found".formatted(instanceId)));
        // TODO: ACC-2149 use FilterName of relation
        var filterName = relation.getTargetEndPoint().getName();
        if (filterName != null) {
            var redirectUrl = linkTo(methodOn(EntityRestController.class)
                    .listEntity(application, targetPathSegment, 0, null, Map.of(filterName.getValue(), instanceId.toString())))
                    .toUri();

            return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Following an unidirectional *-to-many relation not implemented.");
        }
    }

    @PostMapping
    public ResponseEntity<Object> addRelationItems(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            @RequestBody List<URI> body
    ) {
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No entity url provided.");
        }
        var relation = getRequiredRelation(application, entityName, propertyName);
        var matcher = getMatcherForTargetEntity(application, relation);
        var targetIds = new java.util.HashSet<EntityId>();

        for (var element : body) {
            var maybeId = matcher.tryMatch(element.toString());
            if (maybeId.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid target entity.");
            }
            targetIds.add(maybeId.get());
        }
        try {
            datamodelApi.addRelationItems(application, relation, instanceId, targetIds);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Object> deleteRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName
    ) {
        try {
            var relation = getRequiredRelation(application, entityName, propertyName);
            datamodelApi.deleteRelation(application, relation, instanceId);
        } catch (EntityNotFoundException|RelationLinkNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<Object> getRelationItem(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            @PathVariable EntityId itemId
    ) {
        var relation = getRequiredRelation(application, entityName, propertyName);
        if (datamodelApi.hasRelationTarget(application, relation, instanceId, itemId)) {
            var uri = linkTo(methodOn(EntityRestController.class).getEntity(application, relation.getTargetEndPoint().getEntity().getPathSegment(), itemId)).toUri();
            return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Object> deleteRelationItem(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            @PathVariable EntityId itemId
    ) {
        try {
            var relation = getRequiredRelation(application, entityName, propertyName);
            datamodelApi.removeRelationItem(application, relation, instanceId, itemId);
        } catch (EntityNotFoundException | RelationLinkNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

}
