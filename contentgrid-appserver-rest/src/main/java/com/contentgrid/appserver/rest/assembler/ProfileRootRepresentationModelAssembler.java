package com.contentgrid.appserver.rest.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.rest.ProfileRestController;
import com.contentgrid.appserver.rest.links.ContentGridLinkRelations;
import lombok.NonNull;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;

public class ProfileRootRepresentationModelAssembler implements RepresentationModelAssembler<Application, EmptyRepresentationModel> {

    @Override
    public EmptyRepresentationModel toModel(@NonNull Application application) {
        var result = new EmptyRepresentationModel();
        result.add(getSelfLink(application));
        application.getEntities().forEach(entity -> result.add(getEntityProfileLink(application, entity)));
        return result;
    }

    private Link getSelfLink(@NonNull Application application) {
        return linkTo(methodOn(ProfileRestController.class).getProfile(application)).withSelfRel();
    }

    private Link getEntityProfileLink(@NonNull Application application, @NonNull Entity entity) {
        return linkTo(methodOn(ProfileRestController.class)
                .getEntityProfile(application, entity.getPathSegment()))
                .withRel(ContentGridLinkRelations.ENTITY)
                .withName(entity.getLinkName().getValue());
    }
}
