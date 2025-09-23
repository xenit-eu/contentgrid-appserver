package com.contentgrid.appserver.rest.property;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.RelationTarget;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.RelationLinkNotFoundException;
import com.contentgrid.appserver.rest.converter.UriListHttpMessageConverter.URIList;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType.PropertyType;
import java.util.Objects;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.ETag;
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

    @NonNull
    private final ConversionService conversionService;

    private Relation getRequiredRelation(Application application, PathSegmentName entityName, PathSegmentName propertyName) {
        return application.getRelationForPath(entityName, propertyName)
                .filter(relation -> relation instanceof OneToOneRelation || relation instanceof ManyToOneRelation)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String calculateETag(RelationTarget result) {
        return Optional.ofNullable(conversionService.convert(result.getRelationIdentity().getVersion(), ETag.class))
                .map(ETag::formattedTag)
                .orElse(null);
    }

    @GetMapping
    public ResponseEntity<Object> getRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            AuthorizationContext authorizationContext,
            LinkFactoryProvider linkFactoryProvider
    ) {
        var relation = getRequiredRelation(application, entityName, propertyName);
        var relationRequest = RelationRequest.forRelation(
                relation.getSourceEndPoint().getEntity(),
                instanceId,
                relation.getSourceEndPoint().getName()
        );
        try {
            var relationTarget = datamodelApi.findRelationTarget(application, relationRequest, authorizationContext)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target of %s not found".formatted(relation.getSourceEndPoint().getName())));
            var redirectUrl = linkFactoryProvider.toItem(relationTarget.getTargetEntityIdentity()).toUri();

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(redirectUrl)
                    .eTag(calculateETag(relationTarget))
                    .build();
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
            VersionConstraint versionConstraint,
            AuthorizationContext authorizationContext,
            LinkFactoryProvider linkFactoryProvider
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
        var maybeId = linkFactoryProvider.itemMatcher(relation.getTargetEndPoint().getEntity()).tryMatch(element.toString());
        if (maybeId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid target entity.");
        }
        try {
            var relationRequest = RelationRequest.forRelation(
                    relation.getSourceEndPoint().getEntity(),
                    instanceId,
                    relation.getSourceEndPoint().getName()
            ).withVersionConstraint(versionConstraint);
            datamodelApi.setRelation(application, relationRequest, maybeId.get(), authorizationContext);
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
    public ResponseEntity<Object> clearRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            VersionConstraint versionConstraint,
            AuthorizationContext authorizationContext
    ) {
        try {
            var relation = getRequiredRelation(application, entityName, propertyName);
            var relationRequest = RelationRequest.forRelation(
                    relation.getSourceEndPoint().getEntity(),
                    instanceId,
                    relation.getSourceEndPoint().getName()
            ).withVersionConstraint(versionConstraint);
            datamodelApi.deleteRelation(application, relationRequest, authorizationContext);
        } catch (EntityIdNotFoundException | RelationLinkNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

}
