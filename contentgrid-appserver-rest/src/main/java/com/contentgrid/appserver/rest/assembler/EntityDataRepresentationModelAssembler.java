package com.contentgrid.appserver.rest.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplate;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplateGenerator;
import com.contentgrid.appserver.rest.property.ContentRestController;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.links.ContentGridLinkRelations;
import com.contentgrid.appserver.rest.property.XToOneRelationRestController;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EntityDataRepresentationModelAssembler implements RepresentationModelContextAssembler<EntityData, EntityDataRepresentationModel, Application> {

    private final HalFormsTemplateGenerator templateGenerator;

    @Override
    public EntityDataRepresentationModel toModel(@NonNull EntityData entityData, @NonNull Application application) {
        Entity entity = application.getEntityByName(entityData.getName()).orElseThrow();
        var id = entityData.getId();

        var model = EntityDataRepresentationModel.from(entity, entityData);
        model.add(getSelfLink(application, entity, id));
        for (var relation : application.getRelationsForSourceEntity(entity)) {
            if (relation.getSourceEndPoint().getLinkName() != null && relation.getSourceEndPoint().getPathSegment() != null) {
                var relationLink = getRelationLink(application, relation, id);
                var relationTemplates = templateGenerator.generateRelationTemplates(application, relation, relationLink.getHref());
                model.add(relationLink).addTemplates(relationTemplates);
            }
        }
        for (var content : entity.getContentAttributes()) {
            var contentLink = getContentLink(application, entity, id, content);
            var contentTemplates = templateGenerator.generateContentTemplates(application, entity, content, contentLink.getHref());
            model.add(contentLink).addTemplates(contentTemplates);
        }
        return model.addTemplate(getUpdateTemplate(application, entity))
                .addTemplate(getDeleteTemplate());
    }

    private Link getSelfLink(Application application, Entity entity, EntityId id) {
        return linkTo(methodOn(EntityRestController.class)
                .getEntity(application, entity.getPathSegment(), id)
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
                .getContent(application, entity.getPathSegment(), id, attribute.getPathSegment()))
                .withRel(ContentGridLinkRelations.CONTENT)
                .withName(attribute.getLinkName().getValue());
    }

    private HalFormsTemplate getUpdateTemplate(Application application, Entity entity) {
        return templateGenerator.generateUpdateTemplate(application, entity);
    }

    private HalFormsTemplate getDeleteTemplate() {
        return HalFormsTemplate.builder()
                .key("delete")
                .httpMethod(HttpMethod.DELETE)
                .build();
    }
}
