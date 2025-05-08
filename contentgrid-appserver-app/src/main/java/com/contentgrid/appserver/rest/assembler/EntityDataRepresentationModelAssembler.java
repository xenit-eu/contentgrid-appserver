package com.contentgrid.appserver.rest.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.query.EntityInstance;
import com.contentgrid.appserver.rest.EntityRestController;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;

@RequiredArgsConstructor
public class EntityDataRepresentationModelAssembler implements RepresentationModelAssembler<EntityInstance, EntityDataRepresentationModel> {

    private final Application application;

    static RepresentationModel<?> toRepresentationModel(Application application, Entity entity, EntityInstance inst) {
        return RepresentationModel.of(inst)
                .add(linkTo(methodOn(EntityRestController.class)
                        .getEntity(application, entity.getPathSegment(), inst.getId())
                ).withSelfRel());
    }

    @Override
    public EntityDataRepresentationModel toModel(EntityInstance entityData) {
        Entity entity = application.getEntityByName(entityData.getEntityName()).orElseThrow();
        return EntityDataRepresentationModel.from(entity, entityData)
                .add(linkTo(methodOn(EntityRestController.class)
                        .getEntity(application, entity.getPathSegment(), entityData.getId())
                ).withSelfRel());
    }
}
