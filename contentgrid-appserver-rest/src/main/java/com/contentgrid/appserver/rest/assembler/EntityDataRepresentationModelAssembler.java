package com.contentgrid.appserver.rest.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.paging.ResultSlice;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler.EntityContext;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplate;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplateGenerator;
import com.contentgrid.appserver.rest.paging.CursorPageMetadata;
import com.contentgrid.appserver.rest.paging.ItemCountPageMetadata;
import com.contentgrid.appserver.rest.property.ContentRestController;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.links.ContentGridLinkRelations;
import com.contentgrid.appserver.rest.property.XToOneRelationRestController;
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
import org.springframework.hateoas.server.MethodLinkBuilderFactory;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EntityDataRepresentationModelAssembler implements RepresentationModelContextAssembler<EntityData, EntityDataRepresentationModel, EntityContext> {

    private final HalFormsTemplateGenerator templateGenerator;
    private final SlicedResourcesAssembler<EntityData> slicedResourcesAssembler;
    private final MethodLinkBuilderFactory<WebMvcLinkBuilder> linkBuilderFactory;

    @Override
    public EntityDataRepresentationModel toModel(@NonNull EntityData entityData, @NonNull EntityContext context) {
        Entity entity = context.application().getEntityByName(entityData.getName()).orElseThrow();
        var id = entityData.getId();

        var model = EntityDataRepresentationModel.from(entity, entityData);
        model.add(getSelfLink(context.application(), entity, id));
        for (var relation : context.application().getRelationsForSourceEntity(entity)) {
            if (relation.getSourceEndPoint().getLinkName() != null && relation.getSourceEndPoint().getPathSegment() != null) {
                var relationLink = getRelationLink(context.application(), relation, id);
                var relationTemplates = templateGenerator.generateRelationTemplates(context.application(), relation, relationLink.getHref());
                model.add(relationLink).addTemplates(relationTemplates);
            }
        }
        for (var content : entity.getContentAttributes()) {
            var contentLink = getContentLink(context.application(), entity, id, content);
            var contentTemplates = templateGenerator.generateContentTemplates(context.application(), entity, content, contentLink.getHref());
            model.add(contentLink).addTemplates(contentTemplates);
        }
        return model.addTemplate(templateGenerator.generateUpdateTemplate(context.application(), entity))
                .addTemplate(getDeleteTemplate());
    }

    public CollectionModel<EntityDataRepresentationModel> toSlicedModel(ResultSlice slice, EntityContext context) {
        if (slice.current() instanceof EncodedCursorPagination pagination) {
            // use current pagination instead of pagination from request
            context = context.withPagination(pagination);
        }
        Link selfLink = this.getCollectionSelfLink(context);
        var slicedModel = slicedResourcesAssembler.toModel(slice, this.withContext(context), Optional.of(selfLink));
        var pageMetadata = getPageMetadata(slice);

        // Add pageMetadata to slicedModel by wrapping it in a PagedModel
        return PagedModel.of(slicedModel.getContent(), pageMetadata, slicedModel.getLinks());
    }

    @Override
    public CollectionModel<EntityDataRepresentationModel> toCollectionModel(Iterable<? extends EntityData> entities,
            EntityContext context) {
        if (entities instanceof ResultSlice slice) {
            return toSlicedModel(slice, context);
        }
        var result = RepresentationModelContextAssembler.super.toCollectionModel(entities, context);
        result.add(getCollectionSelfLink(context));
        return result;
    }

    public RepresentationModelAssembler<EntityData, EntityDataRepresentationModel> withContext(Application application, PathSegmentName entityPathSegment) {
        return withContext(application, entityPathSegment, Map.of(), null);
    }

    public RepresentationModelAssembler<EntityData, EntityDataRepresentationModel> withContext(Application application, PathSegmentName entityPathSegment, Map<String, String> params, EncodedCursorPagination pagination) {
        return withContext(new EntityContext(application, entityPathSegment, params, pagination));
    }

    private Link getSelfLink(Application application, Entity entity, EntityId id) {
        return linkBuilderFactory.linkTo(methodOn(EntityRestController.class)
                .getEntity(application, entity.getPathSegment(), id, null)
        ).withSelfRel();
    }

    private Link getCollectionSelfLink(EntityContext context) {
        return linkBuilderFactory.linkTo(methodOn(EntityRestController.class)
                .listEntity(context.application(), context.entityPathSegment(), null, context.params(), context.pagination()))
                .withSelfRel();
    }

    private Link getRelationLink(Application application, Relation relation, EntityId id) {
        // Links for *-to-many relations are the same as links for *-to-one relations,
        // no need to switch based on relation type
        return linkBuilderFactory.linkTo(methodOn(XToOneRelationRestController.class)
                .getRelation(application, relation.getSourceEndPoint().getEntity().getPathSegment(), id,
                        relation.getSourceEndPoint().getPathSegment()))
                .withRel(ContentGridLinkRelations.RELATION)
                .withName(relation.getSourceEndPoint().getLinkName().getValue());
    }

    private Link getContentLink(Application application, Entity entity, EntityId id, ContentAttribute attribute) {
        return linkBuilderFactory.linkTo(methodOn(ContentRestController.class)
                .getContent(null, application, entity.getPathSegment(), id, attribute.getPathSegment(), null, null, null))
                .withRel(ContentGridLinkRelations.CONTENT)
                .withName(attribute.getLinkName().getValue());
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

    public record EntityContext(Application application, PathSegmentName entityPathSegment, Map<String, String> params, @With EncodedCursorPagination pagination) {}
}
