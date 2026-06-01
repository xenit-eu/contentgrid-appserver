package com.contentgrid.appserver.rest.entity.assembler;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.paging.ResultSlice;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.rest.entity.assembler.EntityDataRepresentationModelAssembler.EntityContext;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplate;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplateGenerator;
import com.contentgrid.appserver.rest.hal.links.ContentGridLinkRelations;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider.CollectionParameters;
import com.contentgrid.hateoas.spring.pagination.SlicedResourcesAssembler;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.With;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.PagedModel.PageMetadata;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.core.EmbeddedWrapper;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

@Component
@RequiredArgsConstructor
public class EntityDataRepresentationModelAssembler implements RepresentationModelContextAssembler<EntityInstance, EntityDataRepresentationModel, EntityContext> {

    private final SlicedResourcesAssembler<EntityInstance> slicedResourcesAssembler;

    @Override
    public EntityDataRepresentationModel toModel(@NonNull EntityInstance entityData, @NonNull EntityContext context) {
        Entity entity = context.application().getRequiredEntityByName(context.entityName());
        var id = entityData.getIdentity().getEntityId();

        var model = EntityDataRepresentationModel.from(entityData);
        model.add(context.linkFactoryProvider().toItem(entityData.getIdentity()).withSelfRel());
        for (var relation : context.application().getRelationsForSourceEntity(entity)) {
            if (relation.getSourceEndPoint().getLinkName() != null && relation.getSourceEndPoint().getPathSegment() != null) {
                var relationIdentity = RelationIdentity.forRelation(entity.getName(), id, relation.getSourceEndPoint().getName());
                model.add(context.linkFactoryProvider().toRelation(relationIdentity).orElseThrow().withRel(ContentGridLinkRelations.RELATION))
                        .addTemplates(context.templateGenerator().generateRelationTemplates(relationIdentity));
            }
        }
        for (var content : entity.getContentAttributes()) {
            var contentLink = context.linkFactoryProvider().toContent(
                    entityData.getIdentity(),
                    content.getName()
            ).withRel(ContentGridLinkRelations.CONTENT);
            var contentTemplates = context.templateGenerator().generateContentTemplates(entity, content);
            model.add(contentLink).addTemplates(contentTemplates);
        }
        return model.addTemplate(context.templateGenerator().generateUpdateTemplate(entity.getName()))
                .addTemplate(getDeleteTemplate());
    }

    public CollectionModel<EntityDataRepresentationModel> toSlicedModel(ResultSlice slice, EntityContext context) {
        if (slice.current() instanceof EncodedCursorPagination pagination) {
            // use current pagination instead of pagination from request
            context = context.withPagination(pagination);
        }
        Link selfLink = getCollectionSelfLink(context);
        var slicedModel = slicedResourcesAssembler.toModel(slice, this.withContext(context), Optional.of(selfLink));
        slicedModel.add(getEntityProfileLink(context));
        var pageMetadata = getPageMetadata(slice);

        // Add pageMetadata to slicedModel by wrapping it in a PagedModel
        return PagedModel.of(wrap(slicedModel.getContent()), pageMetadata, slicedModel.getLinks());
    }

    private Collection<EntityDataRepresentationModel> wrap(Collection<EntityDataRepresentationModel> content) {
        // Yes, this is casting to a type that it really isn't, but that's the only way to make spring hateoas
        // properly make this object always present and have an 'item' linkrel
        return (Collection)List.of(new EntityDataCollectionRepresentationModelEmbeddedWrapper(content));
    }

    @Override
    public CollectionModel<EntityDataRepresentationModel> toCollectionModel(Iterable<? extends EntityInstance> entities,
            EntityContext context) {
        if (entities instanceof ResultSlice slice) {
            return toSlicedModel(slice, context);
        }
        var result = RepresentationModelContextAssembler.super.toCollectionModel(entities, context)
                .withFallbackType(EntityDataRepresentationModel.class);
        result.add(getCollectionSelfLink(context))
                .add(getEntityProfileLink(context));
        return result;
    }

    public RepresentationModelAssembler<EntityInstance, EntityDataRepresentationModel> withContext(Application application, EntityName entityName, UserLocales userLocales, LinkFactoryProvider linkFactoryProvider) {
        return withContext(application, entityName, userLocales, linkFactoryProvider, MultiValueMap.fromSingleValue(Map.of()), null);
    }

    public RepresentationModelAssembler<EntityInstance, EntityDataRepresentationModel> withContext(Application application, EntityName entityName, UserLocales userLocales, LinkFactoryProvider linkFactoryProvider, MultiValueMap<String, String> params, EncodedCursorPagination pagination) {
        return withContext(new EntityContext(application, entityName, userLocales, linkFactoryProvider, params, pagination));
    }

    private Link getCollectionSelfLink(EntityContext context) {
        return context.linkFactoryProvider().toCollection(context.entityName(), CollectionParameters.defaults()
                .withSearchParams(context.params())
                .withCursor(context.pagination())
        ).withSelfRel();
    }

    private Link getEntityProfileLink(EntityContext context) {
        return context.linkFactoryProvider().toProfile(context.entityName())
                .withRel(IanaLinkRelations.PROFILE);
    }

    private HalFormsTemplate getDeleteTemplate() {
        return HalFormsTemplate.builder()
                .key("delete")
                .httpMethod(HttpMethod.DELETE)
                .build();
    }

    private ItemCountPageMetadata getPageMetadata(ResultSlice slice) {
        int limit = slice.getLimit() != null ? slice.getLimit() : slice.getSize();
        // Fake numbers because we lost page context in domain-layer
        var pageMetadata = new PageMetadata(limit, 0, slice.getTotalItemCount().count());
        var cursorMetadata = getCursorPageMetadata(slice);
        return new ItemCountPageMetadata(pageMetadata, slice.getTotalItemCount(), cursorMetadata);
    }

    private CursorPageMetadata getCursorPageMetadata(ResultSlice slice) {
        var nextCursor = slice.next()
                .filter(EncodedCursorPagination.class::isInstance)
                .map(EncodedCursorPagination.class::cast)
                .map(EncodedCursorPagination::getCursor)
                .orElse(null);
        var prevCursor = slice.previous()
                .filter(EncodedCursorPagination.class::isInstance)
                .map(EncodedCursorPagination.class::cast)
                .map(EncodedCursorPagination::getCursor)
                .orElse(null);
        return new CursorPageMetadata(prevCursor, nextCursor);
    }

    public record EntityContext(
            Application application,
            EntityName entityName,
            UserLocales userLocales,
            LinkFactoryProvider linkFactoryProvider,
            MultiValueMap<String, String> params,
            @With EncodedCursorPagination pagination
    ) {
        HalFormsTemplateGenerator templateGenerator() {
            return new HalFormsTemplateGenerator(application, userLocales, linkFactoryProvider);
        }
    }

    @RequiredArgsConstructor
    private static class EntityDataCollectionRepresentationModelEmbeddedWrapper implements EmbeddedWrapper {

        private final Collection<EntityDataRepresentationModel> contents;

        @Override
        public Optional<LinkRelation> getRel() {
            return Optional.of(IanaLinkRelations.ITEM);
        }

        @Override
        public boolean hasRel(LinkRelation rel) {
            return IanaLinkRelations.ITEM.isSameAs(rel);
        }

        @Override
        public boolean isCollectionValue() {
            return true;
        }

        @Override
        public Object getValue() {
            return contents;
        }

        @Override
        public Class<?> getRelTargetType() {
            return EntityDataRepresentationModel.class;
        }
    }
}
