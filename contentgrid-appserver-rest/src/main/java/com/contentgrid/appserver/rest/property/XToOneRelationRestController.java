package com.contentgrid.appserver.rest.property;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.authorization.PermissionPredicate;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.RelationLinkNotFoundException;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.converter.UriListHttpMessageConverter.URIList;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType.PropertyType;
import com.contentgrid.hateoas.spring.links.UriTemplateMatcher;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@SpecializedOnPropertyType(type = PropertyType.TO_ONE_RELATION, entityPathVariable = "entityName", propertyPathVariable = "propertyName")
@RequestMapping("/{entityName}/{instanceId}/{propertyName}")
public class XToOneRelationRestController {

    @NonNull
    private final DatamodelApi datamodelApi;

    private Relation getRequiredRelation(Application application, PathSegmentName entityName, PathSegmentName propertyName) {
        return application.getRelationForPath(entityName, propertyName)
                .filter(relation -> relation instanceof OneToOneRelation || relation instanceof ManyToOneRelation)
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
        var targetPathSegment = application.getRelationTargetEntity(relation).getPathSegment();
        try {
            var targetId = datamodelApi.findRelationTarget(application, relation, instanceId, permissionPredicate)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target of %s not found".formatted(relation.getSourceEndPoint().getName())));
            var redirectUrl = linkTo(methodOn(EntityRestController.class).getEntity(application, targetPathSegment, targetId, null)).toUri();

            return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
        } catch (EntityIdNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @PutMapping
    public ResponseEntity<Object> setRelation(
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
        if (uris.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple targets not supported.");
        }
        var relation = getRequiredRelation(application, entityName, propertyName);
        var element = uris.getFirst();
        var maybeId = getMatcherForTargetEntity(application, relation).tryMatch(element.toString());
        if (maybeId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid target entity.");
        }
        try {
            datamodelApi.setRelation(application, relation, instanceId, maybeId.get(), permissionPredicate);
        } catch (EntityIdNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Object> clearRelation(
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

}
