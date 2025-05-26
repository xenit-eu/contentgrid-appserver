package com.contentgrid.appserver.rest.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.links.LinkRelationUtils;
import java.util.Map;
import lombok.NonNull;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;

public class RootRepresentationModelAssembler implements RepresentationModelAssembler<Application, RootRepresentationModel> {

    @Override
    public RootRepresentationModel toModel(@NonNull Application application) {
        var model = new RootRepresentationModel();
        application.getEntities().forEach(entity -> model.add(getEntityLink(application, entity)));
        return model;
    }

    private Link getEntityLink(@NonNull Application application, @NonNull Entity entity) {
        var linkRel = LinkRelationUtils.from(entity.getCollectionLinkRel());
        return linkTo(methodOn(EntityRestController.class)
                .listEntity(application, entity.getPathSegment(), 0, Map.of()))
                .withRel(linkRel);
    }
}
