package com.contentgrid.appserver.rest.entity;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.RelationLinkNotFoundException;
import com.contentgrid.appserver.rest.converter.UriListHttpMessageConverter.URIList;
import com.contentgrid.appserver.rest.exception.InvalidRelationTargetException;
import com.contentgrid.appserver.rest.exception.MissingRelationTargetException;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider.CollectionParameters;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType.PropertyType;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@SpecializedOnPropertyType(type = PropertyType.TO_MANY_RELATION, entityPathVariable = "entityName", propertyPathVariable = "propertyName")
@RequestMapping("/{entityName}/{id}/{propertyName}")
public class XToManyRelationRestController {

    @NonNull
    private final DatamodelApi datamodelApi;

    private Relation getRequiredRelation(Application application, PathSegmentName entityName, PathSegmentName propertyName) {
        return application.getRelationForPath(entityName, propertyName)
                .filter(relation -> relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation)
                // Due to @SpecializedOnProperty this _should_ never throw
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public ResponseEntity<Object> getRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            @PathVariable PathSegmentName propertyName,
            AuthorizationContext authorizationContext,
            LinkFactoryProvider linkFactoryProvider
    ) {
        var relation = getRequiredRelation(application, entityName, propertyName);
        var request = EntityRequest.forEntity(relation.getSourceEndPoint().getEntity(), id);
        datamodelApi.findById(application, request, authorizationContext)
                .orElseThrow(() -> new EntityIdNotFoundException(request));

        var targetFilter = application.getFilterForRelation(relation);

        var redirectUrl = linkFactoryProvider.toCollection(relation.getTargetEndPoint().getEntity(), CollectionParameters.defaults()
                .withSearchParam(targetFilter.getName().getValue(), id.getValue().toString())
        ).toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
    }

    @PostMapping(consumes = "text/uri-list")
    public ResponseEntity<Object> addRelationItems(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            @PathVariable PathSegmentName propertyName,
            @RequestBody(required = false) URIList body,
            AuthorizationContext authorizationContext,
            LinkFactoryProvider linkFactoryProvider
    ) throws MissingRelationTargetException, InvalidRelationTargetException {
        var relation = getRequiredRelation(application, entityName, propertyName);
        if (body == null || body.uris().isEmpty()) {
            throw new MissingRelationTargetException(RelationIdentity.forRelation(
                    relation.getSourceEndPoint().getEntity(),
                    id,
                    relation.getSourceEndPoint().getName()
            ));
        }
        var uris = body.uris();
        var relationRequest = RelationRequest.forRelation(
                relation.getSourceEndPoint().getEntity(),
                id,
                relation.getSourceEndPoint().getName()
        );
        var matcher = linkFactoryProvider.itemMatcher(relation.getTargetEndPoint().getEntity());
        var targetIds = new java.util.HashSet<EntityId>();

        for (var element : uris) {
            var maybeId = matcher.tryMatch(element.toString());
            if (maybeId.isEmpty()) {
                // Invalid Relation Target: wrong entity (e.g., person instead of invoice) or wrong link format
                throw new InvalidRelationTargetException(
                        relation.getSourceEndPoint().getEntity(),
                        relation.getSourceEndPoint().getName(),
                        element
                );
            }
            targetIds.add(maybeId.get());
        }
        datamodelApi.addRelationItems(application, relationRequest, targetIds, authorizationContext);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Object> deleteRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            @PathVariable PathSegmentName propertyName,
            AuthorizationContext authorizationContext
    ) {
        var relation = getRequiredRelation(application, entityName, propertyName);
        var request = RelationRequest.forRelation(
                relation.getSourceEndPoint().getEntity(),
                id,
                relation.getSourceEndPoint().getName()
        );
        datamodelApi.deleteRelation(application, request, authorizationContext);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<Object> getRelationItem(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            @PathVariable PathSegmentName propertyName,
            @PathVariable EntityId itemId,
            AuthorizationContext authorizationContext,
            LinkFactoryProvider linkFactoryProvider
    ) {
        var relation = getRequiredRelation(application, entityName, propertyName);
        if (datamodelApi.hasRelationTarget(application, relation, id, itemId, authorizationContext)) {
            var uri = linkFactoryProvider.toItem(EntityIdentity.forEntity(relation.getTargetEndPoint().getEntity(), itemId)).toUri();
            return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
        } else {
            throw new RelationLinkNotFoundException(relation, id, itemId);
        }
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Object> deleteRelationItem(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            @PathVariable PathSegmentName propertyName,
            @PathVariable EntityId itemId,
            AuthorizationContext authorizationContext
    ) {
        var relation = getRequiredRelation(application, entityName, propertyName);
        var relationRequest = RelationRequest.forRelation(
                relation.getSourceEndPoint().getEntity(),
                id,
                relation.getSourceEndPoint().getName()
        );
        datamodelApi.removeRelationItem(application, relationRequest, itemId, authorizationContext);
        return ResponseEntity.noContent().build();
    }

}
