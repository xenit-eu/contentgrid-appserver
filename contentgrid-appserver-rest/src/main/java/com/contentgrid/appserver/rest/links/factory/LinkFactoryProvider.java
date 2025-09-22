package com.contentgrid.appserver.rest.links.factory;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.exceptions.AttributeNotFoundException;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.ProfileRestController;
import com.contentgrid.appserver.rest.RootRestController;
import com.contentgrid.appserver.rest.property.ContentRestController;
import com.contentgrid.appserver.rest.property.XToOneRelationRestController;
import com.contentgrid.hateoas.spring.links.UriTemplateMatcher;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.With;
import org.springframework.hateoas.server.MethodLinkBuilderFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Generates {@link LinkFactory}s for entity-based paths
 * <p>
 * Allows an easy way to generate links to specific resources without having to specify
 * all parameters for the controller method call.
 */
@RequiredArgsConstructor
public class LinkFactoryProvider {
    @NonNull
    private final Application application;

    @NonNull
    private final UserLocales userLocales;

    @NonNull
    private final MethodLinkBuilderFactory<?> linkBuilderFactory;

    @With
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CollectionParameters {

        /**
         * @return Default collection parameters (no search parameters and no cursor)
         */
        public static CollectionParameters defaults() {
            return new CollectionParameters(new LinkedMultiValueMap<>(), null);
        }

        @NonNull
        private final MultiValueMap<String, String> searchParams;

        private final EncodedCursorPagination cursor;

        public CollectionParameters withModifiedSearchParams(@NonNull Consumer<MultiValueMap<String, String>> modifier) {
            var copy = new LinkedMultiValueMap<>(searchParams);
            modifier.accept(copy);
            return withSearchParams(copy);
        }

        public CollectionParameters withSearchParam(@NonNull String name, @NonNull String value) {
            return withModifiedSearchParams(map -> map.set(name, value));
        }

        public CollectionParameters withSearchParam(@NonNull String name, @NonNull List<String> values) {
            return withModifiedSearchParams(map -> map.put(name, values));
        }
    }

    private CustomizableLinkFactory linkTo(Object invocationValue) {
        var linkBuilder = linkBuilderFactory.linkTo(invocationValue);

        return new LinkBuilderAndModifiersLinkFactory(linkBuilder);
    }

    /**
     * Generate a link to the API root
     * @return A link to the API root
     */
    public LinkFactory toRoot() {
        return linkTo(methodOn(RootRestController.class).getRoot(application, this));
    }

    /**
     * Generate a link to an entity collection
     *
     * @param entityName The entity to link to
     * @param parameters Parameters for the collection request
     * @return A link to the entity collection with the given parameters
     */
    public LinkFactory toCollection(@NonNull EntityName entityName, @NonNull CollectionParameters parameters) {
        var entity = application.getRequiredEntityByName(entityName);
        return linkTo(methodOn(EntityRestController.class)
                .listEntity(
                        application,
                        entity.getPathSegment(),
                        null,
                        parameters.searchParams,
                        parameters.cursor,
                        this
                ))
                .withName(entity.getLinkName().getValue())
                .withTitle(entity.getTranslations(userLocales).getPluralName())
                .withProfile(toProfile(entityName).toUri().toString());
    }

    /**
     * Generate a link to an entity item
     * @param identity The identity of the entity to link to
     * @return A link to an entity item
     */
    public LinkFactory toItem(@NonNull EntityIdentity identity) {
        var entity = application.getRequiredEntityByName(identity.getEntityName());
        return linkTo(methodOn(EntityRestController.class)
                .getEntity(
                        application,
                        entity.getPathSegment(),
                        identity.getEntityId(),
                        null,
                        this
                ))
                .withTitle(entity.getTranslations(userLocales).getSingularName())
                .withProfile(toProfile(identity.getEntityName()).toUri().toString());
    }

    /**
     * Generate a link matcher for an entity item
     * @param entityName The entity to generate a matcher for
     * @return Template matcher for an entity item of a particular type
     */
    public UriTemplateMatcher<EntityId> itemMatcher(@NonNull EntityName entityName) {
        var entity = application.getRequiredEntityByName(entityName);

        return UriTemplateMatcher.<EntityId>builder()
                .matcherFor(methodOn(EntityRestController.class)
                                .getEntity(application, entity.getPathSegment(), null, null, null),
                        params -> EntityId.of(UUID.fromString(params.get("instanceId"))))
                .build();
    }

    /**
     * Generate a link for an entity relation
     * @param relationIdentity The identity of the relation to link to
     * @return A link to an entity relation
     */
    public LinkFactory toRelation(@NonNull RelationIdentity relationIdentity) {
        var entity = application.getRequiredEntityByName(relationIdentity.getEntityName());
        var relation = application.getRequiredRelationForEntity(entity, relationIdentity.getRelationName());

        // Links for *-to-many relations are the same as links for *-to-one relations,
        // no need to switch based on relation type
        return linkTo(methodOn(XToOneRelationRestController.class)
                .getRelation(
                        application,
                        entity.getPathSegment(),
                        relationIdentity.getEntityId(),
                        relation.getSourceEndPoint().getPathSegment(),
                        null,
                        this
                ))
                .withTitle(relation.getSourceEndPoint().getTranslations(userLocales).getName())
                .withName(relation.getSourceEndPoint().getLinkName().getValue());
    }

    /**
     * Generate a link for entity content
     * @param identity The identity of the entity to link to
     * @param attributeName The name of the content attribute
     * @return A link to entity content
     */
    public LinkFactory toContent(@NonNull EntityIdentity identity, @NonNull AttributeName attributeName) {
        var entity = application.getRequiredEntityByName(identity.getEntityName());
        var attribute = entity.getAttributeByName(attributeName)
                .filter(ContentAttribute.class::isInstance)
                .map(ContentAttribute.class::cast)
                .orElseThrow(() -> new AttributeNotFoundException("Entity '%s' does not have content attribute '%s'".formatted(entity.getName(), attributeName)));

        return linkTo(methodOn(ContentRestController.class)
                .getContent(
                        null,
                        application,
                        entity.getPathSegment(),
                        identity.getEntityId(),
                        attribute.getPathSegment(),
                        null,
                        null,
                        null
                ))
                .withName(attributeName.getValue())
                .withTitle(attribute.getTranslations(userLocales).getName());
    }

    /**
     * Generate a link to the profile root
     * @return A link to the profile root
     */
    public LinkFactory toProfileRoot() {
        return linkTo(methodOn(ProfileRestController.class).getProfile(application, this));
    }

    /**
     * Generate a link to an entity profile
     *
     * @param entityName The entity to link to the profile to
     * @return A link to the entity profile
     */
    public LinkFactory toProfile(@NonNull EntityName entityName) {
        var entity = application.getRequiredEntityByName(entityName);
        return linkTo(methodOn(ProfileRestController.class)
                .getHalFormsEntityProfile(
                        application,
                        entity.getPathSegment(),
                        userLocales,
                        this
                ))
                .withName(entity.getLinkName().getValue())
                .withTitle(entity.getTranslations(userLocales).getSingularName());
    }

}
