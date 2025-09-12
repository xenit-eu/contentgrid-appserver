package com.contentgrid.appserver.rest.property;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationPath;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.authorization.PermissionPredicate;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.RelationLinkNotFoundException;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.converter.UriListHttpMessageConverter.URIList;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType.PropertyType;
import com.contentgrid.hateoas.spring.links.UriTemplateMatcher;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
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
        var targetPathSegment = application.getRelationTargetEntity(relation).getPathSegment();
        return UriTemplateMatcher.<EntityId>builder()
                .matcherFor(methodOn(EntityRestController.class)
                                .getEntity(application, targetPathSegment, null, null),
                        params -> EntityId.of(UUID.fromString(params.get("instanceId"))))
                .build();
    }

    @GetMapping
    public ResponseEntity<Object> getRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            PermissionPredicate permissionPredicate
    ) {
        var relation = getRequiredRelation(application, entityName, propertyName);
        datamodelApi.findById(application, EntityRequest.forEntity(relation.getSourceEndPoint().getEntity(), instanceId), permissionPredicate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id %s not found".formatted(instanceId)));

        var targetEntity = application.getRelationTargetEntity(relation);

        if(relation.getTargetEndPoint().getName() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Following an unnamed *-to-many relation not implemented.");
        }

        var relationPath = new RelationPath(
                relation.getTargetEndPoint().getName(),
                new SimpleAttributePath(targetEntity.getPrimaryKey().getName())
        );

        var targetFilter = targetEntity.getSearchFilters().stream()
                .filter(searchFilter -> {
                    if (searchFilter instanceof ExactSearchFilter exactSearchFilter) {
                        return Objects.equals(exactSearchFilter.getAttributePath(), relationPath);
                    }
                    return false;
                })
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "A search filter for '%s' is required to follow this relation".formatted(relationPath)));

        var redirectUrl = linkTo(methodOn(EntityRestController.class)
                .listEntity(application, targetEntity.getPathSegment(), null,
                        MultiValueMap.fromSingleValue(Map.of(targetFilter.getName().getValue(), instanceId.toString())), null))
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
    }

    @PostMapping
    public ResponseEntity<Object> addRelationItems(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            @RequestBody URIList body,
            PermissionPredicate permissionPredicate
    ) {
        var uris = body.uris();
        if (uris.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No entity url provided.");
        }
        var relation = getRequiredRelation(application, entityName, propertyName);
        var matcher = getMatcherForTargetEntity(application, relation);
        var targetIds = new java.util.HashSet<EntityId>();

        for (var element : uris) {
            var maybeId = matcher.tryMatch(element.toString());
            if (maybeId.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid target entity.");
            }
            targetIds.add(maybeId.get());
        }
        try {
            datamodelApi.addRelationItems(application, relation, instanceId, targetIds, permissionPredicate);
        } catch (EntityIdNotFoundException e) {
            if(Objects.equals(e.getEntityName(), relation.getSourceEndPoint().getEntity()) && Objects.equals(e.getId(), instanceId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            }
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
            @PathVariable PathSegmentName propertyName,
            PermissionPredicate permissionPredicate
    ) {
        try {
            var relation = getRequiredRelation(application, entityName, propertyName);
            datamodelApi.deleteRelation(application, relation, instanceId, permissionPredicate);
        } catch (EntityIdNotFoundException | RelationLinkNotFoundException e) {
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
            @PathVariable EntityId itemId,
            PermissionPredicate permissionPredicate
    ) {
        var relation = getRequiredRelation(application, entityName, propertyName);
        if (datamodelApi.hasRelationTarget(application, relation, instanceId, itemId, permissionPredicate)) {
            var uri = linkTo(methodOn(EntityRestController.class).getEntity(application, application.getRelationTargetEntity(relation).getPathSegment(), itemId, null)).toUri();
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
            @PathVariable EntityId itemId,
            PermissionPredicate permissionPredicate
    ) {
        try {
            var relation = getRequiredRelation(application, entityName, propertyName);
            datamodelApi.removeRelationItem(application, relation, instanceId, itemId, permissionPredicate);
        } catch (EntityIdNotFoundException | RelationLinkNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

}
