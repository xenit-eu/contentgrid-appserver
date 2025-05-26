package com.contentgrid.appserver.rest.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class EntityDataRepresentationModelAssembler implements RepresentationModelContextAssembler<EntityData, EntityDataRepresentationModel, Application> {

    @Override
    public EntityDataRepresentationModel toModel(@NonNull EntityData entityData, @NonNull Application application) {
        Entity entity = application.getEntityByName(entityData.getName()).orElseThrow();
        var id = entityData.getId();

        var model = EntityDataRepresentationModel.from(entity, entityData);
        model.add(linkTo(methodOn(EntityRestController.class)
                .getEntity(application, entity.getPathSegment(), id)
        ).withSelfRel());
        return model;
    }
}
