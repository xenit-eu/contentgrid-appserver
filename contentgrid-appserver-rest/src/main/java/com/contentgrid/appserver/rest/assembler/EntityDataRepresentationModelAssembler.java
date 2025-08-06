package com.contentgrid.appserver.rest.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler.EntityContext;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplate;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplateGenerator;
import com.contentgrid.appserver.rest.property.ContentRestController;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.links.ContentGridLinkRelations;
import com.contentgrid.appserver.rest.property.XToOneRelationRestController;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EntityDataRepresentationModelAssembler implements RepresentationModelContextAssembler<EntityData, EntityDataRepresentationModel, EntityContext> {

    private final HalFormsTemplateGenerator templateGenerator;

    @Override
    public EntityDataRepresentationModel toModel(@NonNull EntityData entityData, @NonNull EntityContext context) {
        var id = entityData.getId();

        var model = EntityDataRepresentationModel.from(context.entity(), entityData);
        model.add(getSelfLink(context.application(), context.entity(), id));
        for (var relation : context.application().getRelationsForSourceEntity(context.entity())) {
            if (relation.getSourceEndPoint().getLinkName() != null && relation.getSourceEndPoint().getPathSegment() != null) {
                var relationLink = getRelationLink(context.application(), relation, id);
                var relationTemplates = templateGenerator.generateRelationTemplates(context.application(), relation, relationLink.getHref());
                model.add(relationLink).addTemplates(relationTemplates);
            }
        }
        for (var content : context.entity().getContentAttributes()) {
            var contentLink = getContentLink(context.application(), context.entity(), id, content);
            var contentTemplates = templateGenerator.generateContentTemplates(context.application(), context.entity(), content, contentLink.getHref());
            model.add(contentLink).addTemplates(contentTemplates);
        }
        return model.addTemplate(templateGenerator.generateUpdateTemplate(context.application(), context.entity()))
                .addTemplate(getDeleteTemplate());
    }

    @Override
    public CollectionModel<EntityDataRepresentationModel> toCollectionModel(Iterable<? extends EntityData> entities,
            EntityContext context) {
        var result = RepresentationModelContextAssembler.super.toCollectionModel(entities, context);
        result.add(getCollectionSelfLink(context.application(), context.entity()));
        return result;
    }

    public RepresentationModelAssembler<EntityData, EntityDataRepresentationModel> withContext(Application application, Entity entity) {
        return withContext(new EntityContext(application, entity));
    }

    private Link getSelfLink(Application application, Entity entity, EntityId id) {
        return linkTo(methodOn(EntityRestController.class)
                .getEntity(application, entity.getPathSegment(), id)
        ).withSelfRel();
    }

    private Link getCollectionSelfLink(Application application, Entity entity) {
        return linkTo(methodOn(EntityRestController.class)
                .listEntity(application, entity.getPathSegment(), 0, null, Map.of())
        ).withSelfRel();
    }

    private Link getRelationLink(Application application, Relation relation, EntityId id) {
        // Links for *-to-many relations are the same as links for *-to-one relations,
        // no need to switch based on relation type
        return linkTo(methodOn(XToOneRelationRestController.class)
                .getRelation(application, relation.getSourceEndPoint().getEntity().getPathSegment(), id,
                        relation.getSourceEndPoint().getPathSegment()))
                .withRel(ContentGridLinkRelations.RELATION)
                .withName(relation.getSourceEndPoint().getLinkName().getValue());
    }

    private Link getContentLink(Application application, Entity entity, EntityId id, ContentAttribute attribute) {
        return linkTo(methodOn(ContentRestController.class)
                .getContent(null, application, entity.getPathSegment(), id, attribute.getPathSegment(), null, null))
                .withRel(ContentGridLinkRelations.CONTENT)
                .withName(attribute.getLinkName().getValue());
    }

    private HalFormsTemplate getDeleteTemplate() {
        return HalFormsTemplate.builder()
                .key("delete")
                .httpMethod(HttpMethod.DELETE)
                .build();
    }

    public record EntityContext(Application application, Entity entity) {}
}
