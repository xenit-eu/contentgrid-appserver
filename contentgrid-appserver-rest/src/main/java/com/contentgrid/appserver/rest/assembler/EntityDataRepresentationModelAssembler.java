package com.contentgrid.appserver.rest.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.links.ContentGridLinkRelations;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import org.springframework.hateoas.Link;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class EntityDataRepresentationModelAssembler implements RepresentationModelContextAssembler<EntityData, EntityDataRepresentationModel, Application> {

    @Override
    public EntityDataRepresentationModel toModel(@NonNull EntityData entityData, @NonNull Application application) {
        Entity entity = application.getEntityByName(entityData.getName()).orElseThrow();
        var id = entityData.getId();

        var model = EntityDataRepresentationModel.from(entity, entityData);
        model.add(getSelfLink(application, entity, id));
        for (var relation : application.getRelationsForSourceEntity(entity)) {
            if (relation.getSourceEndPoint().getLinkName() != null && relation.getSourceEndPoint().getPathSegment() != null) {
                model.add(getRelationLink(application, relation, id));
            }
        }
        for (var content : entity.getContentAttributes()) {
            model.add(getContentLink(application, entity, id, content));
        }
        return model;
    }

    private Link getSelfLink(Application application, Entity entity, EntityId id) {
        return linkTo(methodOn(EntityRestController.class)
                .getEntity(application, entity.getPathSegment(), id)
        ).withSelfRel();
    }

    private Link getRelationLink(Application application, Relation relation, EntityId id) {
        // TODO: ACC-2100: use linkTo(methodOn(...)).withRel(RELATION).withName(relation.getSourceEndPoint().getName().getValue())
        var href = getSelfLink(application, relation.getSourceEndPoint().getEntity(), id).getHref() + "/" + relation.getSourceEndPoint().getPathSegment();
        return Link.of(href, ContentGridLinkRelations.RELATION)
                .withName(relation.getSourceEndPoint().getName().getValue());
    }

    private Link getContentLink(Application application, Entity entity, EntityId id, ContentAttribute attribute) {
        // TODO: ACC-2097: use linkTo(methodOn(...)).withRel(CONTENT).withName(attribute.getLinkName().getValue())
        var href = getSelfLink(application, entity, id).getHref() + "/" + attribute.getPathSegment();
        return Link.of(href, ContentGridLinkRelations.CONTENT)
                .withName(attribute.getLinkName().getValue());
    }
}
