package com.contentgrid.appserver.rest.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.ProfileRestController;
import com.contentgrid.appserver.rest.RootRestController;
import com.contentgrid.appserver.rest.links.ContentGridLinkRelations;
import java.util.Map;
import lombok.NonNull;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;

public class RootRepresentationModelAssembler implements RepresentationModelAssembler<Application, EmptyRepresentationModel> {

    @Override
    public EmptyRepresentationModel toModel(@NonNull Application application) {
        var model = new EmptyRepresentationModel();
        model.add(getSelfLink(application))
                .add(getProfileLink(application));
        application.getEntities().forEach(entity -> model.add(getEntityLink(application, entity)));
        return model;
    }

    private Link getSelfLink(@NonNull Application application) {
        return linkTo(methodOn(RootRestController.class).getRoot(application))
                .withSelfRel();
    }

    private Link getEntityLink(@NonNull Application application, @NonNull Entity entity) {
        return linkTo(methodOn(EntityRestController.class)
                .listEntity(application, entity.getPathSegment(), 0, null, Map.of()))
                .withRel(ContentGridLinkRelations.ENTITY).expand()
                .withName(entity.getLinkName().getValue());
    }

    private Link getProfileLink(@NonNull Application application) {
        return linkTo(methodOn(ProfileRestController.class).getProfile(application))
                .withRel(IanaLinkRelations.PROFILE);
    }
}
