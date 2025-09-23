package com.contentgrid.appserver.rest.assembler;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.paging.ResultSlice;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler.EntityContext;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplate;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplateGenerator;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider.CollectionParameters;
import com.contentgrid.appserver.rest.paging.CursorPageMetadata;
import com.contentgrid.appserver.rest.paging.ItemCountPageMetadata;
import com.contentgrid.appserver.rest.links.ContentGridLinkRelations;
import com.contentgrid.hateoas.spring.pagination.SlicedResourcesAssembler;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.With;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.PagedModel.PageMetadata;
import org.springframework.hateoas.server.RepresentationModelAssembler;
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
                model.add(context.linkFactoryProvider().toRelation(relationIdentity).withRel(ContentGridLinkRelations.RELATION))
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
        var pageMetadata = getPageMetadata(slice);

        // Add pageMetadata to slicedModel by wrapping it in a PagedModel
        return PagedModel.of(slicedModel.getContent(), pageMetadata, slicedModel.getLinks());
    }

    @Override
    public CollectionModel<EntityDataRepresentationModel> toCollectionModel(Iterable<? extends EntityInstance> entities,
            EntityContext context) {
        if (entities instanceof ResultSlice slice) {
            return toSlicedModel(slice, context);
        }
        var result = RepresentationModelContextAssembler.super.toCollectionModel(entities, context);
        result.add(getCollectionSelfLink(context));
        return result;
    }

    public RepresentationModelAssembler<EntityInstance, EntityDataRepresentationModel> withContext(Application application, EntityName entityName, LinkFactoryProvider linkFactoryProvider) {
        return withContext(application, entityName, linkFactoryProvider, MultiValueMap.fromSingleValue(Map.of()), null);
    }

    public RepresentationModelAssembler<EntityInstance, EntityDataRepresentationModel> withContext(Application application, EntityName entityName, LinkFactoryProvider linkFactoryProvider, MultiValueMap<String, String> params, EncodedCursorPagination pagination) {
        return withContext(new EntityContext(application, entityName, linkFactoryProvider, params, pagination));
    }

    private Link getCollectionSelfLink(EntityContext context) {
        return context.linkFactoryProvider().toCollection(context.entityName(), CollectionParameters.defaults()
                .withSearchParams(context.params())
                .withCursor(context.pagination())
        ).withSelfRel();
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
        var cursor = ((EncodedCursorPagination) slice.current()).getCursor();
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
        return new CursorPageMetadata(cursor, prevCursor, nextCursor);
    }

    public record EntityContext(
            Application application,
            EntityName entityName,
            LinkFactoryProvider linkFactoryProvider,
            MultiValueMap<String, String> params,
            @With EncodedCursorPagination pagination
    ) {
        HalFormsTemplateGenerator templateGenerator() {
            return new HalFormsTemplateGenerator(application, linkFactoryProvider);
        }
    }
}
