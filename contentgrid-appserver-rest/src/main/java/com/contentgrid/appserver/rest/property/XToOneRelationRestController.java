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
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.rest.converter.UriListHttpMessageConverter.URIList;
import com.contentgrid.appserver.rest.exception.EmptyRelationException;
import com.contentgrid.appserver.rest.exception.InvalidRelationTargetException;
import com.contentgrid.appserver.rest.exception.MissingRelationTargetException;
import com.contentgrid.appserver.rest.exception.MultipleRelationTargetsException;
import com.contentgrid.appserver.rest.exception.RelationTargetNotFoundException;
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
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@SpecializedOnPropertyType(type = PropertyType.TO_ONE_RELATION, entityPathVariable = "entityName", propertyPathVariable = "propertyName")
@RequestMapping("/{entityName}/{id}/{propertyName}")
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
            @PathVariable EntityId id,
            @PathVariable PathSegmentName propertyName,
            VersionConstraint versionConstraint,
            WebRequest webRequest,
            AuthorizationContext authorizationContext,
            LinkFactoryProvider linkFactoryProvider
    ) throws EmptyRelationException {
        var relation = getRequiredRelation(application, entityName, propertyName);
        var source = relation.getSourceEndPoint();
        var relationRequest = RelationRequest.forRelation(source.getEntity(), id, source.getName())
                .withVersionConstraint(versionConstraint);
        try {
            var relationTarget = datamodelApi.findRelationTarget(application, relationRequest, authorizationContext)
                    .orElseThrow(() -> new EmptyRelationException(RelationIdentity.forRelation(source.getEntity(), id, source.getName())));
            var redirectUrl = linkFactoryProvider.toItem(relationTarget.getTargetEntityIdentity()).toUri();

            var eTag = calculateETag(relationTarget);

            if (webRequest.checkNotModified(eTag)) {
                return null;
            }

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(redirectUrl)
                    .eTag(eTag)
                    .build();
        } catch (EntityIdNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @PutMapping(consumes = "text/uri-list")
    public ResponseEntity<Object> setRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            @PathVariable PathSegmentName propertyName,
            @RequestBody(required = false) URIList body,
            VersionConstraint versionConstraint,
            AuthorizationContext authorizationContext,
            LinkFactoryProvider linkFactoryProvider
    ) throws RelationTargetNotFoundException, MissingRelationTargetException, InvalidRelationTargetException, MultipleRelationTargetsException {
        var relation = getRequiredRelation(application, entityName, propertyName);
        var relationIdent = RelationIdentity.forRelation(
                relation.getSourceEndPoint().getEntity(),
                id,
                relation.getSourceEndPoint().getName());
        if (body == null || body.uris().isEmpty()) {
            throw new MissingRelationTargetException(relationIdent);
        }
        var uris = body.uris();
        if (uris.size() > 1) {
            throw new MultipleRelationTargetsException(relationIdent);
        }
        var element = uris.getFirst();
        var maybeId = linkFactoryProvider.itemMatcher(relation.getTargetEndPoint().getEntity()).tryMatch(element.toString());
        if (maybeId.isEmpty()) {
            throw new InvalidRelationTargetException(element.toString());
        }
        try {
            var relationRequest = RelationRequest.forRelation(
                    relation.getSourceEndPoint().getEntity(),
                    id,
                    relation.getSourceEndPoint().getName()
            ).withVersionConstraint(versionConstraint);
            var relationTarget = datamodelApi.setRelation(application, relationRequest, maybeId.get(), authorizationContext);
            return ResponseEntity.noContent()
                    .eTag(calculateETag(relationTarget))
                    .build();
        } catch (EntityIdNotFoundException e) {
            if(Objects.equals(e.getEntityName(), relation.getSourceEndPoint().getEntity()) && Objects.equals(e.getId(), id)) {
                throw new EntityIdNotFoundException(e.getEntityName(), e.getId());
            } else {
                throw new RelationTargetNotFoundException(e);
            }
        }
    }

    @DeleteMapping
    public ResponseEntity<Object> clearRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            @PathVariable PathSegmentName propertyName,
            VersionConstraint versionConstraint,
            AuthorizationContext authorizationContext
    ) {
        var relation = getRequiredRelation(application, entityName, propertyName);
        var relationRequest = RelationRequest.forRelation(
                relation.getSourceEndPoint().getEntity(),
                id,
                relation.getSourceEndPoint().getName()
        ).withVersionConstraint(versionConstraint);
        datamodelApi.deleteRelation(application, relationRequest, authorizationContext);
        return ResponseEntity.noContent().build();
    }

}
